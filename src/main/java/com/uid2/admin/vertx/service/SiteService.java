package com.uid2.admin.vertx.service;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.auth.AdminAuthMiddleware;
import com.uid2.admin.legacy.ILegacyClientKeyProvider;
import com.uid2.admin.legacy.LegacyClientKey;
import com.uid2.shared.model.ClientType;
import com.google.common.net.InternetDomainName;
import com.uid2.admin.store.writer.StoreWriter;
import com.uid2.admin.vertx.JsonUtil;
import com.uid2.admin.vertx.RequestUtil;
import com.uid2.admin.vertx.ResponseUtil;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.Const;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.Site;
import com.uid2.shared.store.reader.RotatingSiteStore;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.Router;

import static com.uid2.admin.vertx.RequestUtil.getTypes;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class SiteService implements IService {
    private final AdminAuthMiddleware auth;
    private final WriteLock writeLock;
    private final StoreWriter<Collection<Site>> storeWriter;
    private final RotatingSiteStore siteProvider;
    private final ILegacyClientKeyProvider legacyClientKeyProvider;
    private final ObjectWriter jsonWriter = JsonUtil.createJsonWriter();
    private static final Logger LOGGER = LoggerFactory.getLogger(SiteService.class);

    public SiteService(AdminAuthMiddleware auth,
                       WriteLock writeLock,
                       StoreWriter<Collection<Site>> storeWriter,
                       RotatingSiteStore siteProvider,
                       ILegacyClientKeyProvider legacyClientKeyProvider) {
        this.auth = auth;
        this.writeLock = writeLock;
        this.storeWriter = storeWriter;
        this.siteProvider = siteProvider;
        this.legacyClientKeyProvider = legacyClientKeyProvider;
    }

    @Override
    public void setupRoutes(Router router) {
        router.post("/api/site/rewrite_metadata").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleRewriteMetadata(ctx);
            }
        }, Role.PRIVILEGED));

        router.get("/api/site/list").handler(
            auth.handle(this::handleSiteList, Role.MAINTAINER, Role.SHARING_PORTAL, Role.METRICS_EXPORT));
        router.get("/api/site/:siteId").handler(
            auth.handle(this::handleSiteById, Role.MAINTAINER, Role.SHARING_PORTAL));
        router.post("/api/site/add").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleSiteAdd(ctx);
            }
        }, Role.MAINTAINER, Role.SHARING_PORTAL));
        router.post("/api/site/enable").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleSiteEnable(ctx);
            }
        }, Role.MAINTAINER));
        router.post("/api/site/set-types").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleSiteTypesSet(ctx);
            }
        }, Role.MAINTAINER, Role.SHARING_PORTAL));
        router.post("/api/site/domain_names").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleSiteDomains(ctx);
            }
        }, Role.MAINTAINER, Role.SHARING_PORTAL));
        router.post("/api/site/app_names").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleSiteAppNames(ctx);
            }
        }, Role.MAINTAINER, Role.SHARING_PORTAL));
        router.post("/api/site/update").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleSiteUpdate(ctx);
            }
        }, Role.MAINTAINER));
    }

    private void handleRewriteMetadata(RoutingContext rc) {
        try {
            storeWriter.rewriteMeta();
            rc.response().end("OK");
        } catch (Exception e) {
            LOGGER.error("Could not rewrite metadata", e);
            rc.fail(500, e);
        }
    }

    private void handleSiteList(RoutingContext rc) {
        try {
            JsonArray ja = new JsonArray();
            final Collection<Site> sites = this.siteProvider.getAllSites().stream()
                    .sorted(Comparator.comparing(Site::getName))
                    .collect(Collectors.toList());
            final Map<Integer, List<LegacyClientKey>> clientKeys = this.legacyClientKeyProvider.getAll().stream()
                    .collect(Collectors.groupingBy(LegacyClientKey::getSiteId));
            final List<LegacyClientKey> emptySiteKeys = new ArrayList<>();
            for (Site site : sites) {
                ja.add(createSiteJsonObject(site, clientKeys, emptySiteKeys));
            }

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(ja.encode());
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private static JsonObject createSiteJsonObject(Site site, Map<Integer, List<LegacyClientKey>> clientKeys, List<LegacyClientKey> emptySiteKeys) {
        JsonObject jo = new JsonObject();

        JsonArray domainNamesJa = new JsonArray();
        site.getDomainNames().forEach(domainNamesJa::add);

        JsonArray appNamesJa = new JsonArray();
        site.getAppNames().forEach(appNamesJa::add);

        jo.put("id", site.getId());
        jo.put("name", site.getName());
        jo.put("description", site.getDescription());
        jo.put("enabled", site.isEnabled());
        jo.put("clientTypes", site.getClientTypes());
        jo.put("domain_names", domainNamesJa);
        jo.put("app_names", appNamesJa);
        jo.put("visible", site.isVisible());
        jo.put("created", site.getCreated());

        JsonArray jr = new JsonArray();
        List<LegacyClientKey> clients = clientKeys.getOrDefault(site.getId(), emptySiteKeys);
        clients.stream()
                .map(LegacyClientKey::getRoles)
                .flatMap(Set::stream)
                .collect(Collectors.toSet())
                .forEach(jr::add);

        jo.put("roles", jr);
        jo.put("client_count", clients.size());
        return jo;
    }

    private void handleSiteById(RoutingContext rc) {
        try {
            final Site site = RequestUtil.getSiteFromUrl(rc, "siteId", this.siteProvider);
            if (site == null) {
                return;
            }

            final Map<Integer, List<LegacyClientKey>> clientKeys = this.legacyClientKeyProvider.getAll().stream()
                    .collect(Collectors.groupingBy(LegacyClientKey::getSiteId));
            final List<LegacyClientKey> emptySiteKeys = new ArrayList<>();

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(createSiteJsonObject(site, clientKeys, emptySiteKeys).encode());
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }
    private void handleSiteAdd(RoutingContext rc) {
        try {
            // refresh manually
            siteProvider.loadContent();

            final String name = rc.queryParam("name").isEmpty() ? "" : rc.queryParam("name").get(0).trim();
            if (!validateSiteName(rc, name)) {
                return;
            }

            List<String> normalizedDomainNames = new ArrayList<>();

            JsonObject body = rc.body().asJsonObject();
            if (body != null) {
                JsonArray domainNamesJa = body.getJsonArray("domain_names");
                if (domainNamesJa != null) {
                    normalizedDomainNames = getNormalizedDomainNames(rc, domainNamesJa);
                    if (normalizedDomainNames == null) return;
                }
            }

            Set<String> normalizedAppNames = new HashSet<>();
            if (body != null) {
                JsonArray appNamesJa = body.getJsonArray("app_names");
                if (appNamesJa != null) {
                    normalizedAppNames = getNormalizedAppNames(rc, appNamesJa);
                    if (normalizedAppNames == null) return;
                }
            }

            boolean enabled = false;
            List<String> enabledFlags = rc.queryParam("enabled");
            if (!enabledFlags.isEmpty()) {
                try {
                    enabled = Boolean.valueOf(enabledFlags.get(0));
                } catch (Exception ex) {
                    ResponseUtil.error(rc, 400, "unable to parse enabled " + ex.getMessage());
                    return;
                }
            }

            Set<ClientType> types = new HashSet<>();
            if (!rc.queryParam("types").isEmpty()) {
                types = getTypes(rc.queryParam("types").get(0));
            }

            String description = rc.queryParam("description").stream().findFirst().orElse(null);

            final List<Site> sites = this.siteProvider.getAllSites()
                    .stream().sorted(Comparator.comparingInt(Site::getId))
                    .collect(Collectors.toList());
            final int siteId = 1 + sites.stream().mapToInt(Site::getId).max().orElse(Const.Data.AdvertisingTokenSiteId);

            final Site newSite = new Site(siteId, name, description, enabled, types, new HashSet<>(normalizedDomainNames), normalizedAppNames, true);
            // add site to the array
            sites.add(newSite);

            // upload to storage
            storeWriter.upload(sites, null);

            // respond with new client created
            rc.response().end(jsonWriter.writeValueAsString(newSite));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleSiteTypesSet(RoutingContext rc) {
        try {
            final Site existingSite = RequestUtil.getSiteFromParam(rc, "id", siteProvider);
            if (existingSite == null) {
                return;
            }

            Set<ClientType> types = getTypes(rc.queryParam("types").get(0));
            if (types == null) {
                ResponseUtil.error(rc, 400, "Invalid Types");
                return;
            }

            existingSite.setClientTypes(types);

            uploadSiteToStoreWriterAndWriteExistingSiteToResponse(existingSite, rc);
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleSiteEnable(RoutingContext rc) {
        try {
            // refresh manually
            siteProvider.loadContent();

            final Site existingSite = RequestUtil.getSiteFromParam(rc, "id", siteProvider);
            if (existingSite == null) {
                return;
            }

            Boolean enabled = false;
            List<String> enabledFlags = rc.queryParam("enabled");
            if (!enabledFlags.isEmpty()) {
                try {
                    enabled = Boolean.valueOf(enabledFlags.get(0));
                } catch (Exception ex) {
                    ResponseUtil.error(rc, 400, "unable to parse enabled " + ex.getMessage());
                    return;
                }
            }

            final List<Site> sites = this.siteProvider.getAllSites()
                    .stream().sorted(Comparator.comparingInt(Site::getId))
                    .collect(Collectors.toList());

            if (existingSite.isEnabled() != enabled) {
                existingSite.setEnabled(enabled);
                storeWriter.upload(sites, null);
            }

            rc.response().end(jsonWriter.writeValueAsString(existingSite));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleSiteDomains(RoutingContext rc) {
        try {
            // refresh manually
            siteProvider.loadContent();

            final Site existingSite = RequestUtil.getSiteFromParam(rc, "id", siteProvider);
            if (existingSite == null) {
                return;
            }

            JsonObject body = rc.body().asJsonObject();
            JsonArray domainNamesJa = body.getJsonArray("domain_names");
            if (domainNamesJa == null) {
                ResponseUtil.error(rc, 400, "required parameters: domain_names");
                return;
            }
            List<String> normalizedDomainNames = getNormalizedDomainNames(rc, domainNamesJa);
            if (normalizedDomainNames == null) return;

            existingSite.setDomainNames(new HashSet<>(normalizedDomainNames));

            uploadSiteToStoreWriterAndWriteExistingSiteToResponse(existingSite, rc);
        } catch (Exception e) {
            ResponseUtil.errorInternal(rc, "set site domain_names failed", e);
        }
    }

    private void handleSiteAppNames(RoutingContext rc) {
        try {
            // refresh manually
            siteProvider.loadContent();

            final Site existingSite = RequestUtil.getSiteFromParam(rc, "id", siteProvider);
            if (existingSite == null) {
                return;
            }

            JsonObject body = rc.body().asJsonObject();
            JsonArray appNamesJa = body.getJsonArray("app_names");
            if (appNamesJa == null) {
                ResponseUtil.error(rc, 400, "required parameters: app_names");
                return;
            }
            Set<String> normalizedAppNames = getNormalizedAppNames(rc, appNamesJa);
            if (normalizedAppNames == null) return;

            existingSite.setAppNames(normalizedAppNames);

            uploadSiteToStoreWriterAndWriteExistingSiteToResponse(existingSite, rc);
        } catch (Exception e) {
            ResponseUtil.errorInternal(rc, "set site app_names failed", e);
        }
    }

    private void handleSiteUpdate(RoutingContext rc) {
        try {
            // refresh manually
            siteProvider.loadContent();

            final Site existingSite = RequestUtil.getSiteFromParam(rc, "id", siteProvider);
            if (existingSite == null) {
                return;
            }
            String description = rc.queryParam("description").stream().findFirst().orElse(null);
            String visibleParam = rc.queryParam("visible").stream().findFirst().orElse(null);
            String name = rc.queryParam("name").stream().findFirst().orElse(null);

            if (description != null) {
                existingSite.setDescription(description);
            }
            if (visibleParam != null) {
                if ("true".equalsIgnoreCase(visibleParam)) {
                    existingSite.setVisible(true);
                } else if ("false".equalsIgnoreCase(visibleParam)) {
                    existingSite.setVisible(false);
                } else {
                    ResponseUtil.error(rc, 400, "Invalid parameter for visible: " + visibleParam);
                }
            }
            if (name != null) {
                if (!validateSiteName(rc, name)) {
                    return;
                }
                existingSite.setName(name);
            }

            uploadSiteToStoreWriterAndWriteExistingSiteToResponse(existingSite, rc);
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private boolean validateSiteName(RoutingContext rc, String name) {
        if (name == null || name.isEmpty()) {
            ResponseUtil.error(rc, 400, "must specify a valid site name");
            return false;
        }

        Optional<Site> existingSite = this.siteProvider.getAllSites()
                .stream().filter(c -> c.getName().equals(name))
                .findFirst();
        if (existingSite.isPresent()) {
            ResponseUtil.error(rc, 400, "site with name " + name + " already exists");
            return false;
        }
        return true;
    }

    private static List<String> getNormalizedDomainNames(RoutingContext rc, JsonArray domainNamesJa) {
        List<String> domainNames = domainNamesJa.stream().map(String::valueOf).collect(Collectors.toList());

        List<String> normalizedDomainNames = new ArrayList<>();
        for (String domain : domainNames) {
            try {
                String tld = getTopLevelDomainName(domain);
                normalizedDomainNames.add(tld);
            } catch (Exception e) {
                ResponseUtil.error(rc, 400, "invalid domain name: " + domain);
                return null;
            }
        }

        boolean containsDuplicates = normalizedDomainNames.stream().distinct().count() < normalizedDomainNames.size();
        if (containsDuplicates) {
            ResponseUtil.error(rc, 400, "duplicate domain_names not permitted");
            return null;
        }
        return normalizedDomainNames;
    }

    private static Set<String> getNormalizedAppNames(RoutingContext rc, JsonArray appNamesJa) {
        List<String> appNames = appNamesJa.stream().map(String::valueOf).collect(Collectors.toList());

        boolean containsDuplicates = appNames.stream().distinct().count() < appNames.size();
        if (containsDuplicates) {
            ResponseUtil.error(rc, 400, "duplicate app_names not permitted");
            return null;
        }
        return new HashSet<>(appNames);
    }

    public static String getTopLevelDomainName(String origin) throws MalformedURLException {
        String host;
        try {
            URL url = new URL(origin);
            host = url.getHost();
        } catch (Exception e) {
            host = origin;
        }
        //InternetDomainName will normalise the domain name to lower case already
        InternetDomainName name = InternetDomainName.from(host);
        //if the domain name has a proper TLD suffix
        if (name.isUnderPublicSuffix()) {
            try {
                return name.topPrivateDomain().toString();
            } catch (Exception e) {
                throw e;
            }
        }
        throw new MalformedURLException();
    }

    private void uploadSiteToStoreWriterAndWriteExistingSiteToResponse(Site existingSite, RoutingContext rc) throws Exception {
        final List<Site> sites = this.siteProvider.getAllSites()
                .stream().sorted(Comparator.comparingInt(Site::getId))
                .collect(Collectors.toList());

        storeWriter.upload(sites, null);
        rc.response().end(jsonWriter.writeValueAsString(existingSite));
    }
}
