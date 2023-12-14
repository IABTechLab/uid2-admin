package com.uid2.admin.vertx.service;

import com.uid2.admin.store.writer.StoreWriter;
import com.uid2.admin.vertx.ResponseUtil;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.auth.Role;
import com.uid2.shared.middleware.AuthMiddleware;
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

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ServiceLinkService implements IService {

    private final AuthMiddleware auth;
    private final WriteLock writeLock;
    private final StoreWriter<Collection<ServiceLink>> storeWriter;
    private final RotatingServiceLinkStore serviceLinkProvider;
    private final RotatingServiceStore serviceProvider;
    private final RotatingSiteStore siteProvider;
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceLinkService.class);

    public ServiceLinkService(AuthMiddleware auth,
                              WriteLock writeLock,
                              StoreWriter<Collection<ServiceLink>> storeWriter,
                              RotatingServiceLinkStore serviceLinkProvider,
                              RotatingServiceStore serviceProvider,
                              RotatingSiteStore siteProvider) {
        this.auth = auth;
        this.writeLock = writeLock;
        this.storeWriter = storeWriter;
        this.serviceLinkProvider = serviceLinkProvider;
        this.serviceProvider = serviceProvider;
        this.siteProvider = siteProvider;
    }

    @Override
    public void setupRoutes(Router router) {
        router.get("/api/service_link/list").handler(auth.handle(this::handleServiceLinkList, Role.ADMINISTRATOR));
        router.post("/api/service_link/add").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleServiceLinkAdd(ctx);
            }
        }, Role.ADMINISTRATOR));
        router.post("/api/service_link/update").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleServiceLinkUpdate(ctx);
            }
        }, Role.ADMINISTRATOR));
        router.post("/api/service_link/delete").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleServiceLinkDelete(ctx);
            }
        }, Role.ADMINISTRATOR));
    }

    private void handleServiceLinkList(RoutingContext rc) {
        try {
            JsonArray ja = new JsonArray();
            this.serviceLinkProvider.getAllServiceLinks().forEach(s -> ja.add(toJson(s)));
            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(ja.encode());
        } catch (Exception e) {
            ResponseUtil.errorInternal(rc, "Internal Server Error", e);
        }
    }

    private void handleServiceLinkAdd(RoutingContext rc) {
        try {
            siteProvider.loadContent();
            serviceProvider.loadContent();

            JsonObject body = rc.body().asJsonObject();
            if (body == null) {
                ResponseUtil.error(rc, 400, "json payload required but not provided");
                return;
            }
            String linkId = body.getString("link_id");
            Integer serviceId = body.getInteger("service_id");
            Integer siteId = body.getInteger("site_id");
            String name = body.getString("name");
            JsonArray rolesJson = body.getJsonArray("roles");
            if (linkId == null || serviceId == null || siteId == null || name == null || name.isEmpty() || rolesJson == null || rolesJson.isEmpty()) {
                ResponseUtil.error(rc, 400, "required parameters: link_id, service_id, site_id, name, roles");
                return;
            }

            if (serviceProvider.getService(serviceId) == null) {
                ResponseUtil.error(rc, 404, "service_id " + serviceId + " not valid");
                return;
            }

            if (siteProvider.getSite(siteId) == null) {
                ResponseUtil.error(rc, 404, "site_id " + siteId + " not valid");
                return;
            }

            final List<ServiceLink> serviceLinks = this.serviceLinkProvider.getAllServiceLinks()
                    .stream().sorted(Comparator.comparing(ServiceLink::getLinkId))
                    .collect(Collectors.toList());

            if (serviceLinks.stream().anyMatch(sl -> sl.getServiceId() == serviceId && sl.getLinkId().equals(linkId))) {
                ResponseUtil.error(rc, 400, "service link already exists");
                return;
            }

            Set<Role> serviceRoles = serviceProvider.getService(serviceId).getRoles();
            final Set<Role> roles;
            try {
                roles = validateRoles(rolesJson, serviceRoles);
            } catch (IllegalArgumentException e) {
                ResponseUtil.error(rc, 400, e.getMessage());
                return;
            }

            ServiceLink serviceLink = new ServiceLink(linkId, serviceId, siteId, name, roles);

            serviceLinks.add(serviceLink);

            storeWriter.upload(serviceLinks, null);

            rc.response().end(toJson(serviceLink).encodePrettily());
        } catch (Exception e) {
            ResponseUtil.errorInternal(rc, "Internal Server Error", e);
        }
    }

    // The only property that can be edited is the name and roles
    private void handleServiceLinkUpdate(RoutingContext rc) {
        try {
            siteProvider.loadContent();
            serviceProvider.loadContent();

            JsonObject body = rc.body().asJsonObject();
            if (body == null) {
                ResponseUtil.error(rc, 400, "json payload required but not provided");
                return;
            }
            String linkId = body.getString("link_id");
            Integer serviceId = body.getInteger("service_id");
            Integer siteId = body.getInteger("site_id");
            String name = body.getString("name");
            JsonArray rolesJson = body.getJsonArray("roles");

            if (siteId == null || serviceId == null || linkId == null || linkId.isEmpty()) {
                ResponseUtil.error(rc, 400, "required parameters: site_id, service_id, link_id");
                return;
            }

            if (serviceProvider.getService(serviceId) == null) {
                ResponseUtil.error(rc, 404, "service_id " + serviceId + " not valid");
                return;
            }

            if (siteProvider.getSite(siteId) == null) {
                ResponseUtil.error(rc, 404, "site_id " + siteId + " not valid");
                return;
            }

            final List<ServiceLink> serviceLinks = this.serviceLinkProvider.getAllServiceLinks()
                    .stream().sorted(Comparator.comparing(ServiceLink::getLinkId))
                    .collect(Collectors.toList());

            ServiceLink serviceLink = serviceLinks
                    .stream().filter(s -> s.getServiceId() == serviceId && s.getSiteId() == siteId && s.getLinkId().equals(linkId))
                    .findFirst()
                    .orElse(null);
            if (serviceLink == null) {
                ResponseUtil.error(rc, 404, "failed to find a service_link for serviceId: " + serviceId + ", site_id: " + siteId + " and link_id: " + linkId);
                return;
            }

            if (name != null && !name.isEmpty()) {
                serviceLink.setName(name);
            }

            if (rolesJson != null && !rolesJson.isEmpty()) {
                final Set<Role> roles;
                try {
                    roles = validateRoles(rolesJson, serviceProvider.getService(serviceId).getRoles());
                } catch (IllegalArgumentException e) {
                    ResponseUtil.error(rc, 400, e.getMessage());
                    return;
                }
                serviceLink.setRoles(roles);
            }

            storeWriter.upload(serviceLinks, null);

            rc.response().end(toJson(serviceLink).encodePrettily());
        } catch (Exception e) {
            ResponseUtil.errorInternal(rc, "Internal Server Error", e);
        }
    }

    private void handleServiceLinkDelete(RoutingContext rc) {
        try {
            serviceProvider.loadContent();
            JsonObject body = rc.body().asJsonObject();
            if (body == null) {
                ResponseUtil.error(rc, 400, "json payload required but not provided");
                return;
            }

            String linkId = body.getString("link_id");
            Integer serviceId = body.getInteger("service_id");

            if (linkId == null || serviceId == null) {
                ResponseUtil.error(rc, 400, "required parameters: link_id, service_id");
                return;
            }

            ServiceLink serviceLink = this.serviceLinkProvider.getAllServiceLinks()
                    .stream().filter(s -> s.getServiceId() == serviceId && s.getLinkId().equals(linkId))
                    .findFirst()
                    .orElse(null);
            if (serviceLink == null) {
                ResponseUtil.error(rc, 404, "failed to find a service_link for serviceId: " + serviceId + " and link_id: " + linkId);
                return;
            }

            final List<ServiceLink> serviceLinks = this.serviceLinkProvider.getAllServiceLinks()
                    .stream().sorted(Comparator.comparingInt(ServiceLink::getServiceId))
                    .collect(Collectors.toList());

            serviceLinks.remove(serviceLink);
            storeWriter.upload(serviceLinks, null);
            rc.response().end(toJson(serviceLink).encodePrettily());
        } catch (Exception e) {
            ResponseUtil.errorInternal(rc, "Internal Server Error", e);
        }
    }

    private JsonObject toJson(ServiceLink s) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("link_id", s.getLinkId());
        jsonObject.put("service_id", s.getServiceId());
        jsonObject.put("site_id", s.getSiteId());
        jsonObject.put("name", s.getName());
        jsonObject.put("roles", s.getRoles());
        return jsonObject;
    }

    /** Given roles in json, return a set of valid roles. If roles are invalid, return null. **/
    private Set<Role> validateRoles(JsonArray rolesToValidate, Set<Role> serviceRoles) {
        Set<Role> roles;
        try {
            roles = rolesToValidate.stream().map(s -> Role.valueOf((String) s)).collect(Collectors.toSet());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid parameter: roles. Roles allowed: " + serviceRoles.stream().map(Role::toString).collect(Collectors.joining(", ")));
        }
        // roles must be a subset of roles allowed in service
        if (!serviceRoles.containsAll(roles)) {
            throw new IllegalArgumentException("roles allowed: " + serviceRoles.stream().map(Role::toString).collect(Collectors.joining(", ")));
        }
        return roles;
    }
}
