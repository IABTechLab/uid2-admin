package com.uid2.admin.vertx.service;

import com.uid2.admin.secret.IKeysetKeyManager;
import com.uid2.admin.store.reader.RotatingSiteStore;
import com.uid2.admin.store.writer.KeysetStoreWriter;
import com.uid2.admin.vertx.ResponseUtil;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.Const;
import com.uid2.shared.auth.Keyset;
import com.uid2.shared.auth.Role;
import com.uid2.shared.middleware.AuthMiddleware;
import com.uid2.shared.store.reader.RotatingKeysetProvider;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class SharingService implements IService {
    private final AuthMiddleware auth;

    private final WriteLock writeLock;
    private final KeysetStoreWriter storeWriter;
    private final RotatingKeysetProvider keysetProvider;
    private final RotatingSiteStore siteProvider;
    private final IKeysetKeyManager keyManager;
    private static final Logger LOGGER = LoggerFactory.getLogger(SharingService.class);

    public SharingService(AuthMiddleware auth,
                          WriteLock writeLock,
                          KeysetStoreWriter storeWriter,
                          RotatingKeysetProvider keysetProvider,
                          IKeysetKeyManager keyManager,
                          RotatingSiteStore siteProvider) {
        this.auth = auth;
        this.writeLock = writeLock;
        this.storeWriter = storeWriter;
        this.keysetProvider = keysetProvider;
        this.keyManager = keyManager;
        this.siteProvider = siteProvider;
    }

    @Override
    public void setupRoutes(Router router) {
        router.get("/api/sharing/lists").handler(
                auth.handle(this::handleListAllAllowedSites, Role.SHARING_PORTAL)
        );
        router.get("/api/sharing/list/:siteId").handler(
                auth.handle(this::handleListAllowedSites, Role.SHARING_PORTAL)
        );
        router.post("/api/sharing/list/:siteId").handler(
                auth.handle(this::handleSetAllowedSites, Role.SHARING_PORTAL)
        );

        router.get("/api/sharing/keysets").handler(
                auth.handle(this::handleListAllKeysets, Role.ADMINISTRATOR)
        );
        router.post("/api/sharing/keyset").handler(
                auth.handle(this::handleSetKeyset, Role.ADMINISTRATOR)
        );
        router.get("/api/sharing/keyset/:keyset_id").handler(
                auth.handle(this::handleListKeyset, Role.ADMINISTRATOR)
        );
    }

    private void handleSetKeyset(RoutingContext rc) {
        synchronized (writeLock) {
            try {
                keysetProvider.loadContent();
                siteProvider.loadContent();
            } catch (Exception e) {
                LOGGER.error("Failed to load key acls");
                rc.fail(500);
            }

            final JsonObject body = rc.body().asJsonObject();

            final JsonArray whitelist = body.getJsonArray("allowed_sites");
            Integer keysetId = body.getInteger("keyset_id");
            Integer siteId = body.getInteger("site_id");
            String name = body.getString("name", "");

            if (keysetId == null && siteId == null
                    || keysetId != null && siteId != null) {
                ResponseUtil.error(rc, 400, "You must specify exactly one of: keyset_id, site_id");
                return;
            }

            final Map<Integer, Keyset> keysetsById = this.keysetProvider.getSnapshot().getAllKeysets();

            if (keysetId != null) {
                Keyset keyset = keysetsById.get(keysetId);
                if (keyset == null) {
                    ResponseUtil.error(rc, 404, "Could not find keyset for keyset_id: " + keysetId);
                    return;
                }
                siteId = keyset.getSiteId();
                if (name.equals("")) {
                    name = keyset.getName();
                }
            } else {
                keysetId = Collections.max(keysetsById.keySet()) + 1;
            }

            final Set<Integer> newlist = whitelist.stream()
                    .map(s -> (Integer) s)
                    .collect(Collectors.toSet());

            final Keyset newKeyset = new Keyset(keysetId, siteId, name,
                    newlist, Instant.now().getEpochSecond(), true, true);

            keysetsById.put(keysetId, newKeyset);
            try {
                storeWriter.upload(keysetsById, null);
                //Create a new key
                this.keyManager.addKeysetKey(keysetId);
            } catch (Exception e) {
                rc.fail(500, e);
                return;
            }

            JsonObject jo = jsonFullKeyset(newKeyset);
            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(jo.encode());
        }
    }

    private JsonObject jsonFullKeyset(Keyset keyset) {
        JsonObject jo = new JsonObject();
        jo.put("keyset_id", keyset.getKeysetId());
        jo.put("site_id", keyset.getSiteId());
        jo.put("name", keyset.getName());
        jo.put("allowed_sites", keyset.getAllowedSites());
        jo.put("created", keyset.getCreated());
        jo.put("is_enabled", keyset.isEnabled());
        jo.put("is_default", keyset.isDefault());
        return jo;
    }

    private void handleListKeyset(RoutingContext rc) {
        int keysetId;
        try {
            keysetId = Integer.parseInt(rc.pathParam("keyset_id"));
        } catch (Exception e) {
            LOGGER.warn("Failed to parse a keyset_id from list request", e);
            rc.fail(400, e);
            return;
        }

        Keyset keyset = this.keysetProvider.getSnapshot().getAllKeysets().get(keysetId);

        if (keyset == null) {
            ResponseUtil.error(rc, 404, "Failed to find keyset for keyset_id: " + keysetId);
            return;
        }

        JsonObject jo = jsonFullKeyset(keyset);
        rc.response()
                .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end(jo.encode());
    }

    private Keyset getDefaultKeyset(Map<Integer, Keyset> keysets, Integer siteId) {
        for (Keyset keyset : keysets.values()) {
            if (keyset.getSiteId() == siteId && keyset.isDefault()) {
                return keyset;
            }
        }
        return null;
    }

    private void handleListAllKeysets(RoutingContext rc) {
        try {
            JsonArray ja = new JsonArray();
            Map<Integer, Keyset> collection = this.keysetProvider.getSnapshot().getAllKeysets();
            for (Map.Entry<Integer, Keyset> keyset : collection.entrySet()) {
                JsonObject jo = jsonFullKeyset(keyset.getValue());
                ja.add(jo);
            }

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(ja.encode());
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleListAllowedSites(RoutingContext rc) {
        int siteId;
        try {
            siteId = Integer.parseInt(rc.pathParam("siteId"));
        } catch (Exception e) {
            LOGGER.warn("Failed to parse a site id from list request", e);
            rc.fail(400, e);
            return;
        }

        Keyset keyset = getDefaultKeyset(this.keysetProvider.getSnapshot().getAllKeysets(), siteId);

        if (keyset == null) {
            LOGGER.warn("Failed to find keyset for site id: " + siteId);
            rc.fail(404);
            return;
        }

        JsonArray listedSites = new JsonArray();
        Set<Integer> allowedSites = keyset.getAllowedSites();
        if (allowedSites != null) {
            allowedSites.stream().sorted().forEach((listedSiteId) -> listedSites.add(listedSiteId));
        }
        JsonObject jo = new JsonObject();
        jo.put("allowed_sites", listedSites);
        jo.put("hash", keyset.hashCode());

        rc.response()
                .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end(jo.encode());
    }

    private void handleListAllAllowedSites(RoutingContext rc) {
        try {
            JsonArray ja = new JsonArray();
            Map<Integer, Keyset> collection = this.keysetProvider.getSnapshot().getAllKeysets();
            for (Map.Entry<Integer, Keyset> keyset : collection.entrySet()) {
                JsonArray listedSites = new JsonArray();
                Set<Integer> allowedSites = keyset.getValue().getAllowedSites();
                if (allowedSites != null) {
                    allowedSites.stream().sorted().forEach((listedSiteId) -> listedSites.add(listedSiteId));
                }
                JsonObject jo = new JsonObject();
                jo.put("keyset_id", keyset.getValue().getKeysetId());
                jo.put("site_id", keyset.getValue().getSiteId());
                jo.put("allowed_sites", listedSites);
                jo.put("hash", keyset.getValue().hashCode());
                ja.add(jo);
            }

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(ja.encode());
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleSetAllowedSites(RoutingContext rc) {
        synchronized (writeLock) {
            int siteId;
            try {
                siteId = Integer.parseInt(rc.pathParam("siteId"));
            } catch (Exception e) {
                LOGGER.warn("Failed to parse a site id from list request", e);
                rc.fail(400, e);
                return;
            }

            try {
                keysetProvider.loadContent();
            } catch (Exception e) {
                LOGGER.error("Failed to load key acls");
                rc.fail(500);
            }


            final Map<Integer, Keyset> collection = this.keysetProvider.getSnapshot().getAllKeysets();
            Keyset keyset = getDefaultKeyset(collection, siteId);

            final JsonObject body = rc.body().asJsonObject();

           final JsonArray whitelist = body.getJsonArray("allowed_sites");
           final int hash = body.getInteger("hash");

            if (keyset != null && hash != keyset.hashCode()) {
                rc.fail(409);
                return;
            }

            Integer keysetId;
            String name;

            if (keyset == null) {
                keysetId = Collections.max(collection.keySet()) + 1;
                name = "";
            } else {
                keysetId = keyset.getKeysetId();
                name = keyset.getName();
            }

            final Set<Integer> newlist = whitelist.stream()
                    .map(s -> (Integer) s)
                    .collect(Collectors.toSet());

            final Keyset newKeyset = new Keyset(keysetId, siteId, name,
                    newlist, Instant.now().getEpochSecond(), true, true);

            collection.put(keysetId, newKeyset);
            try {
                storeWriter.upload(collection, null);
                //Create new key for keyset
                this.keyManager.addKeysetKey(keysetId);
            } catch (Exception e) {
                rc.fail(500, e);
                return;
            }

           JsonObject jo = new JsonObject();
           jo.put("allowed_sites", whitelist);
           jo.put("hash", newKeyset.hashCode());

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(jo.encode());
        }
    }
}
