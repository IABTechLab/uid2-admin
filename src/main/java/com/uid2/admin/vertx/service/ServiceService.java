package com.uid2.admin.vertx.service;

import com.uid2.admin.auth.AdminAuthMiddleware;
import com.uid2.admin.store.writer.StoreWriter;
import com.uid2.admin.vertx.ResponseUtil;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.audit.AuditParams;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.Service;
import com.uid2.shared.model.ServiceLink;
import com.uid2.shared.store.reader.RotatingServiceLinkStore;
import com.uid2.shared.store.reader.RotatingServiceStore;
import com.uid2.shared.store.reader.RotatingSiteStore;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.uid2.admin.vertx.Endpoints.*;

public class ServiceService implements IService {

    private final AdminAuthMiddleware auth;
    private final WriteLock writeLock;
    private final StoreWriter<Collection<Service>> storeWriter;
    private final RotatingServiceStore serviceProvider;
    private final RotatingSiteStore siteProvider;
    private final RotatingServiceLinkStore serviceLinkProvider;
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceService.class);

    public ServiceService(AdminAuthMiddleware auth,
                          WriteLock writeLock,
                          StoreWriter<Collection<Service>> storeWriter,
                          RotatingServiceStore serviceProvider,
                          RotatingSiteStore siteProvider,
                          RotatingServiceLinkStore serviceLinkProvider) {
        this.auth = auth;
        this.writeLock = writeLock;
        this.storeWriter = storeWriter;
        this.serviceProvider = serviceProvider;
        this.siteProvider = siteProvider;
        this.serviceLinkProvider = serviceLinkProvider;
    }

    @Override
    public void setupRoutes(Router router) {
        router.get(API_SERVICE_LIST.toString()).handler(auth.handle(this::handleServiceListAll, Role.MAINTAINER, Role.METRICS_EXPORT));
        router.get(API_SERVICE_LIST_SERVICE_ID.toString()).handler(auth.handle(this::handleServiceList, Role.MAINTAINER));
        router.post(API_SERVICE_ADD.toString()).blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleServiceAdd(ctx);
            }
        }, new AuditParams(Collections.emptyList(), List.of("site_id", "name", "roles")), Role.PRIVILEGED));
        router.post(API_SERVICE_UPDATE.toString()).blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleUpdate(ctx);
            }
        }, new AuditParams(Collections.emptyList(), List.of("service_id", "site_id", "name", "roles")), Role.PRIVILEGED));
        router.post(API_SERVICE_DELETE.toString()).blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleDelete(ctx);
            }
        }, new AuditParams(Collections.emptyList(), List.of("service_id")), Role.SUPER_USER));
        router.post(API_SERVICE_REMOVE_LINK_ID_REGEX.toString()).blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleRemoveLinkIdRegex(ctx);
            }
        }, new AuditParams(Collections.emptyList(), List.of("service_id")), Role.PRIVILEGED));
    }

    private void handleServiceListAll(RoutingContext rc) {
        try {
            JsonArray ja = new JsonArray();
            this.serviceProvider.getAllServices().forEach(s -> ja.add(toJson(s)));
            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(ja.encode());
        } catch (Exception e) {
            ResponseUtil.errorInternal(rc, "Internal Server Error", e);
        }
    }

    private void handleServiceList(RoutingContext rc) {
        final int serviceId;
        try {
            serviceId = Integer.parseInt(rc.pathParam("service_id"));
        } catch (Exception e) {
            ResponseUtil.error(rc, 400, "failed to parse a service_id from request");
            return;
        }

        Service service = serviceProvider.getService(serviceId);
        if (service == null) {
            ResponseUtil.error(rc, 404, "failed to find a service for service_id: " + serviceId);
            return;
        }

        rc.response()
                .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end(toJson(service).encodePrettily());
    }

    private void handleServiceAdd(RoutingContext rc) {
        try {
            siteProvider.loadContent();

            JsonObject body = rc.body().asJsonObject();
            if (body == null) {
                ResponseUtil.error(rc, 400, "json payload required but not provided");
                return;
            }
            Integer siteId = body.getInteger("site_id");
            String name = body.getString("name");
            JsonArray rolesSpec = body.getJsonArray("roles");
            String linkIdRegex = body.getString("link_id_regex");
            if (siteId == null || name == null || rolesSpec == null || rolesSpec.isEmpty()) {
                ResponseUtil.error(rc, 400, "required parameters: site_id, name, roles");
                return;
            }

            if (siteProvider.getSite(siteId) == null) {
                ResponseUtil.error(rc, 404, "site_id " + siteId + " not valid");
                return;
            }

            if (name.equals("")) {
                ResponseUtil.error(rc, 400, "name cannot be empty");
                return;
            }

            boolean exists = serviceProvider.getAllServices().stream().anyMatch(s -> s.getSiteId() == siteId && s.getName().equals(name));
            if (exists) {
                ResponseUtil.error(rc, 400, "site_id " + siteId + " already has service of name " + name);
                return;
            }

            if (nameExists(name)) {
                ResponseUtil.error(rc, 400, "service name " + name + " already exists");
                return;
            }

            final Set<Role> roles;
            try {
                roles = rolesSpec.stream().map(s -> Role.valueOf((String) s)).collect(Collectors.toSet());
            } catch (IllegalArgumentException e) {
                ResponseUtil.error(rc, 400, "invalid parameter: roles");
                return;
            }

            if (linkIdRegex != null && !linkIdRegex.isEmpty() && !linkIdRegex.isBlank()) {
                try {
                    Pattern.compile(linkIdRegex);
                } catch (Exception e) {
                    ResponseUtil.error(rc, 400, "invalid parameter: link_id_regex; not a valid regex");
                    return;
                }
            } else {
                linkIdRegex = null;
            }

            final List<Service> services = this.serviceProvider.getAllServices()
                    .stream().sorted(Comparator.comparingInt(Service::getServiceId))
                    .collect(Collectors.toList());
            final int serviceId = 1 + services.stream().mapToInt(Service::getServiceId).max().orElse(0);
            Service service = new Service(serviceId, siteId, name, roles, linkIdRegex);

            services.add(service);

            storeWriter.upload(services, null);

            rc.response().end(toJson(service).encodePrettily());
        } catch (Exception e) {
            ResponseUtil.errorInternal(rc, "Internal Server Error", e);
        }
    }

    // Can update the site_id, name, roles, and link_id_regex
    private void handleUpdate(RoutingContext rc) {
        try {
            JsonObject body = rc.body() != null ? rc.body().asJsonObject() : null;
            Service service = findServiceFromRequest(rc);
            if (service == null) return; // error already handled

            
            Integer siteId = body != null ? body.getInteger("site_id") : null;
            String name = body != null ? body.getString("name") : null;
            String linkIdRegex = body != null ? body.getString("link_id_regex") : null;

            JsonArray rolesSpec = null;
            if (body != null && body.getString("roles") != null && !body.getString("roles").isEmpty()) {
                try {
                    rolesSpec = body.getJsonArray("roles");
                } catch (ClassCastException c) {
                    ResponseUtil.error(rc, 400, "invalid parameter: roles");
                    return;
                }
            }

            int serviceId = service.getServiceId();

            // check that this does not create a duplicate service
            if (siteHasService(siteId, name, serviceId)) {
                ResponseUtil.error(rc, 400, "site_id " + siteId + " already has service of name " + name);
                return;
            }

            // check name is not duplicate
            if (nameExists(name)) {
                ResponseUtil.error(rc, 400, "service name " + name + " already exists");
                return;
            }

            if (rolesSpec != null) {
                final Set<Role> roles;
                try {
                    roles = rolesSpec.stream().map(s -> Role.valueOf((String) s)).collect(Collectors.toSet());
                } catch (IllegalArgumentException e) {
                    ResponseUtil.error(rc, 400, "invalid parameter: roles");
                    return;
                }
                // check that if role is removed, it is no longer in use by service links
                Set<Role> rolesToRemove = service.getRoles().stream().filter(r -> !roles.contains(r)).collect(Collectors.toSet());
                List<ServiceLink> serviceLinks = this.serviceLinkProvider.getAllServiceLinks().stream().filter(sl -> sl.getServiceId() == serviceId).collect(Collectors.toList());
                if (serviceLinks.stream().anyMatch(sl -> sl.getRoles().stream().anyMatch(rolesToRemove::contains))) {
                    ResponseUtil.error(rc, 400, "roles: " + rolesToRemove.stream().map(Role::toString).collect(Collectors.joining(", ")) + " may still be in use");
                    return;
                }
                service.setRoles(roles);
            }

            if (linkIdRegex != null && !linkIdRegex.isEmpty() && !linkIdRegex.isBlank()) {
                try {
                    Pattern.compile(linkIdRegex);
                    service.setLinkIdRegex(linkIdRegex);
                } catch (Exception e) {
                    ResponseUtil.error(rc, 400, "invalid parameter: link_id_regex; not a valid regex");
                    return;
                }
            }

            if (siteId != null && siteId != 0) {
                service.setSiteId(siteId);
            }

            if (name != null && !name.isEmpty()) {
                service.setName(name);
            }

            List<Service> services = getSortedServices();

            storeWriter.upload(services, null);

            rc.response().end(toJson(service).encodePrettily());
        } catch (Exception e) {
            ResponseUtil.errorInternal(rc, "Internal Server Error", e);
        }
    }

    private void handleDelete(RoutingContext rc) {
        Service service = findServiceFromRequest(rc);
        if (service == null) return; // error already handled

        try {
            List<Service> services = getSortedServices();
            services.remove(service);
            storeWriter.upload(services, null);
            rc.response().end(toJson(service).encodePrettily());
        } catch (Exception e) {
            ResponseUtil.errorInternal(rc, "Internal Server Error", e);
        }
    }

    private void handleRemoveLinkIdRegex(RoutingContext rc) {
        Service service = findServiceFromRequest(rc);
        if (service == null) return; // error already handled

        try {
            service.setLinkIdRegex(null);
            List<Service> services = getSortedServices();
            storeWriter.upload(services, null);
            rc.response().end(toJson(service).encodePrettily());
        } catch (Exception e) {
            ResponseUtil.errorInternal(rc, "Internal Server Error", e);
        }
    }

    private JsonObject toJson(Service s) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("service_id", s.getServiceId());
        jsonObject.put("site_id", s.getSiteId());
        jsonObject.put("name", s.getName());
        jsonObject.put("roles", s.getRoles());
        jsonObject.put("link_id_regex", s.getLinkIdRegex());
        return jsonObject;
    }

    private boolean nameExists(String name) {
        return ((name != null && !name.isEmpty()) && serviceProvider.getAllServices().stream().anyMatch(s -> s.getName().equals(name)));
    }

    private boolean siteHasService(Integer siteId, String name, int serviceId) {
        return (siteId != null && siteId != 0 && name != null && !name.isEmpty())
                && serviceProvider.getAllServices().stream().anyMatch(s -> s.getServiceId() != serviceId
                    && s.getSiteId() == siteId && s.getName().equals(name));
    }

    
    private Service findServiceFromRequest(RoutingContext rc) {
        JsonObject body = rc.body() != null ? rc.body().asJsonObject() : null;
        if (body == null) {
            ResponseUtil.error(rc, 400, "json payload required but not provided");
            return null;
        }

        int serviceId = body.getInteger("service_id", -1);
        if (serviceId == -1) {
            ResponseUtil.error(rc, 400, "required parameters: service_id");
            return null;
        }

        try {
            serviceProvider.loadContent();
            Service service = serviceProvider.getService(serviceId);
            if (service == null) {
                ResponseUtil.error(rc, 404, "failed to find a service for service_id: " + serviceId);
                return null;
            }
            return service;
        } catch (Exception e) {
            ResponseUtil.errorInternal(rc, "Internal Server Error", e);
            return null;
        }
    }

    private List<Service> getSortedServices() {
        return serviceProvider.getAllServices()
                .stream()
                .sorted(Comparator.comparingInt(Service::getServiceId))
                .collect(Collectors.toList());
    }
}
