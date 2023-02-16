package com.uid2.admin.vertx.service;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.model.Site;
import com.uid2.admin.store.reader.RotatingSiteStore;
import com.uid2.admin.store.writer.StoreWriter;
import com.uid2.admin.vertx.JsonUtil;
import com.uid2.admin.vertx.RequestUtil;
import com.uid2.admin.vertx.ResponseUtil;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.Const;
import com.uid2.shared.auth.ClientKey;
import com.uid2.shared.auth.Role;
import com.uid2.shared.middleware.AuthMiddleware;
import com.uid2.shared.store.IClientKeyProvider;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.*;
import java.util.stream.Collectors;

public class SiteService implements IService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SiteService.class);

    private final AuthMiddleware auth;
    private final WriteLock writeLock;
    private final StoreWriter<Collection<Site>> storeWriter;
    private final RotatingSiteStore siteProvider;
    private final IClientKeyProvider clientKeyProvider;
    private final ObjectWriter jsonWriter = JsonUtil.createJsonWriter();

    public SiteService(AuthMiddleware auth,
                       WriteLock writeLock,
                       StoreWriter<Collection<Site>> storeWriter,
                       RotatingSiteStore siteProvider,
                       IClientKeyProvider clientKeyProvider) {
        this.auth = auth;
        this.writeLock = writeLock;
        this.storeWriter = storeWriter;
        this.siteProvider = siteProvider;
        this.clientKeyProvider = clientKeyProvider;
    }

    @Override
    public void setupRoutes(Router router) {
        router.post("/api/site/rewrite_metadata").blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleRewriteMetadata(ctx);
            }
        }, Role.CLIENTKEY_ISSUER));

        router.get("/api/site/list").handler(
                auth.handle(this::handleSiteList, Role.CLIENTKEY_ISSUER));
        router.post("/api/site/add").blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleSiteAdd(ctx);
            }
        }, Role.CLIENTKEY_ISSUER));
        router.post("/api/site/enable").blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleSiteEnable(ctx);
            }
        }, Role.CLIENTKEY_ISSUER));
    }

    private void handleRewriteMetadata(RoutingContext ctx) {
        try {
            storeWriter.rewriteMeta();
            ctx.response().end("OK");
        } catch (Exception e) {
            LOGGER.error("Could not rewrite metadata", e);
            ctx.fail(500, e);
        }
    }

    private void handleSiteList(RoutingContext ctx) {
        try {
            JsonArray ja = new JsonArray();
            final Collection<Site> sites = this.siteProvider.getAllSites().stream()
                    .sorted(Comparator.comparing(Site::getName))
                    .collect(Collectors.toList());
            final Map<Integer, List<ClientKey>> clientKeys = this.clientKeyProvider.getAll().stream()
                    .collect(Collectors.groupingBy(ClientKey::getSiteId));
            final List<ClientKey> emptySiteKeys = new ArrayList<>();
            for (Site site : sites) {
                JsonObject jo = new JsonObject();
                ja.add(jo);

                jo.put("id", site.getId());
                jo.put("name", site.getName());
                jo.put("enabled", site.isEnabled());

                List<ClientKey> clients = clientKeys.getOrDefault(site.getId(), emptySiteKeys);
                JsonArray jr = new JsonArray();
                clients.stream()
                        .map(c -> c.getRoles()).flatMap(Set::stream).collect(Collectors.toSet())
                        .forEach(r -> jr.add(r));

                jo.put("roles", jr);
                jo.put("client_count", clients.size());
            }

            ctx.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(ja.encode());
        } catch (Exception e) {
            ctx.fail(500, e);
        }
    }

    private void handleSiteAdd(RoutingContext ctx) {
        try {
            // refresh manually
            siteProvider.loadContent();

            final String name = ctx.queryParam("name").isEmpty() ? "" : ctx.queryParam("name").get(0).trim();
            if (name == null || name.isEmpty()) {
                ResponseUtil.error(ctx, 400, "must specify a valid site name");
                return;
            }

            Optional<Site> existingSite = this.siteProvider.getAllSites()
                    .stream().filter(c -> c.getName().equals(name))
                    .findFirst();
            if (existingSite.isPresent()) {
                ResponseUtil.error(ctx, 400, "site existed");
                return;
            }

            boolean enabled = false;
            List<String> enabledFlags = ctx.queryParam("enabled");
            if (!enabledFlags.isEmpty()) {
                try {
                    enabled = Boolean.valueOf(enabledFlags.get(0));
                } catch (Exception e) {
                    ResponseUtil.error(ctx, 400, "unable to parse enabled " + e.getMessage());
                    return;
                }
            }

            final List<Site> sites = this.siteProvider.getAllSites()
                    .stream().sorted(Comparator.comparingInt(Site::getId))
                    .collect(Collectors.toList());
            final int siteId = 1 + sites.stream().mapToInt(Site::getId).max().orElse(Const.Data.AdvertisingTokenSiteId);
            final Site newSite = new Site(siteId, name, enabled);

            // add site to the array
            sites.add(newSite);

            // upload to storage
            storeWriter.upload(sites, null);

            // respond with new client created
            ctx.response().end(jsonWriter.writeValueAsString(newSite));
        } catch (Exception e) {
            ctx.fail(500, e);
        }
    }

    private void handleSiteEnable(RoutingContext ctx) {
        try {
            // refresh manually
            siteProvider.loadContent();

            final Site existingSite = RequestUtil.getSite(ctx, "id", siteProvider);
            if (existingSite == null) {
                return;
            }

            Boolean enabled = false;
            List<String> enabledFlags = ctx.queryParam("enabled");
            if (!enabledFlags.isEmpty()) {
                try {
                    enabled = Boolean.valueOf(enabledFlags.get(0));
                } catch (Exception e) {
                    ResponseUtil.error(ctx, 400, "unable to parse enabled " + e.getMessage());
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

            ctx.response().end(jsonWriter.writeValueAsString(existingSite));
        } catch (Exception e) {
            ctx.fail(500, e);
        }
    }
}
