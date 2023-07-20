package com.uid2.admin.vertx.service;

import com.uid2.admin.auth.AdminKeyset;
import com.uid2.admin.model.ClientType;
import com.uid2.admin.secret.IKeysetKeyManager;
import com.uid2.admin.store.reader.RotatingAdminKeysetStore;
import com.uid2.admin.store.writer.AdminKeysetWriter;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.auth.Role;
import com.uid2.shared.middleware.AuthMiddleware;
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
    private final AdminKeysetWriter storeWriter;
    private final RotatingAdminKeysetStore keysetProvider;
    private final IKeysetKeyManager keyManager;
    private static final Logger LOGGER = LoggerFactory.getLogger(SharingService.class);

    public SharingService(AuthMiddleware auth,
                          WriteLock writeLock,
                          AdminKeysetWriter storeWriter,
                          RotatingAdminKeysetStore keysetProvider,
                          IKeysetKeyManager keyManager) {
        this.auth = auth;
        this.writeLock = writeLock;
        this.storeWriter = storeWriter;
        this.keysetProvider = keysetProvider;
        this.keyManager = keyManager;
    }

    @Override
    public void setupRoutes(Router router) {
        router.get("/api/sharing/lists").handler(
                auth.handle(this::handleListAllAllowlist, Role.SHARING_PORTAL)
        );
        router.get("/api/sharing/list/:siteId").handler(
                auth.handle(this::handleListAllowlist, Role.SHARING_PORTAL)
        );
        router.post("/api/sharing/list/:siteId").handler(
                auth.handle(this::handleSetAllowlist, Role.SHARING_PORTAL)
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
           } catch (Exception e) {
               LOGGER.error("Failed to load key acls");
               rc.fail(500);
           }

            final JsonObject body = rc.body().asJsonObject();

           final JsonArray allowlist = body.getJsonArray("allowlist");
           final JsonArray allowedTypes = body.getJsonArray("allowed_types");
           Integer keysetId = body.getInteger("keyset_id");
           final Integer siteId = body.getInteger("site_id");

           final Map<Integer, AdminKeyset> keysetsById = this.keysetProvider.getSnapshot().getAllKeysets();
           AdminKeyset keyset = keysetsById.get(keysetId);
           AdminKeyset newKeyset = null;

           try {
               newKeyset = setAdminKeyset(rc, allowlist, allowedTypes, siteId, keysetsById, keyset);
           } catch (Exception e) {
               rc.fail(500, e);
           }

           JsonObject jo = jsonFullKeyset(newKeyset);
           rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(jo.encode());
        }
    }


    private void handleListKeyset(RoutingContext rc) {
        int keysetId;
        try {
            keysetId = Integer.parseInt(rc.pathParam("keyset_id"));
        } catch (Exception e) {
            LOGGER.warn("Failed to parse a site id from list request", e);
            rc.fail(400, e);
            return;
        }

        AdminKeyset keyset = this.keysetProvider.getSnapshot().getAllKeysets().get(keysetId);

        if (keyset == null) {
            LOGGER.warn("Failed to find keyset for keyset id: " + keyset);
            rc.fail(404);
            return;
        }

        JsonObject jo = jsonFullKeyset(keyset);
        rc.response()
                .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end(jo.encode());
    }

    private AdminKeyset getDefaultKeyset(Map<Integer, AdminKeyset> keysets, Integer siteId) {
           for(AdminKeyset keyset: keysets.values()) {
               if(keyset.getSiteId() == siteId && keyset.isDefault()) {
                   return keyset;
               }
           }
           return null;
    }

    private void handleListAllKeysets(RoutingContext rc) {
        try {
            JsonArray ja = new JsonArray();
            Map<Integer, AdminKeyset> collection = this.keysetProvider.getSnapshot().getAllKeysets();
            for (Map.Entry<Integer, AdminKeyset> keyset : collection.entrySet()) {
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

    private void handleListAllowlist(RoutingContext rc) {
        int siteId;
        try {
            siteId = Integer.parseInt(rc.pathParam("siteId"));
        } catch (Exception e) {
            LOGGER.warn("Failed to parse a site id from list request", e);
            rc.fail(400, e);
            return;
        }

        AdminKeyset keyset = getDefaultKeyset(this.keysetProvider.getSnapshot().getAllKeysets(), siteId);

        if (keyset == null) {
            LOGGER.warn("Failed to find keyset for site id: " + siteId);
            rc.fail(404);
            return;
        }

        JsonArray listedSites = new JsonArray();
        Set<Integer> allowedSites = keyset.getAllowedSites();
        if(allowedSites != null) {
            allowedSites.stream().sorted().forEach((listedSiteId) -> listedSites.add(listedSiteId));
        }
        JsonObject jo = new JsonObject();
        jo.put("allowlist", listedSites);
        jo.put("allowed_types", keyset.getAllowedTypes());
        jo.put("hash", keyset.hashCode());

        rc.response()
                .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end(jo.encode());
    }

    private void handleListAllAllowlist(RoutingContext rc) {
        try {
            JsonArray ja = new JsonArray();
            Map<Integer, AdminKeyset> collection = this.keysetProvider.getSnapshot().getAllKeysets();
            for (Map.Entry<Integer, AdminKeyset> keyset : collection.entrySet()) {
                JsonArray listedSites = new JsonArray();
                Set<Integer> allowedSites = keyset.getValue().getAllowedSites();
                if(allowedSites != null) {
                    allowedSites.stream().sorted().forEach((listedSiteId) -> listedSites.add(listedSiteId));
                }
                JsonObject jo = new JsonObject();
                jo.put("keyset_id", keyset.getValue().getKeysetId());
                jo.put("site_id", keyset.getValue().getSiteId());
                jo.put("allowlist", listedSites);
                jo.put("allowed_types", keyset.getValue().getAllowedTypes());
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

    private void handleSetAllowlist(RoutingContext rc) {
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


           final Map<Integer, AdminKeyset> keysetsById = this.keysetProvider.getSnapshot().getAllKeysets();
           AdminKeyset keyset = getDefaultKeyset(keysetsById, siteId);

           final JsonObject body = rc.body().asJsonObject();

           final JsonArray allowlist = body.getJsonArray("allowlist");
           final JsonArray allowedTypes = body.getJsonArray("allowed_types");
           final int hash = body.getInteger("hash");

           if (keyset != null &&  hash != keyset.hashCode()) {
               rc.fail(409);
               return;
           }

            AdminKeyset newKeyset = null;

            try {
                newKeyset = setAdminKeyset(rc, allowlist, allowedTypes, siteId, keysetsById, keyset);
            } catch (Exception e) {
                rc.fail(500, e);
            }

           JsonObject jo = new JsonObject();
           jo.put("allowlist", allowlist);
           jo.put("allowed_types", newKeyset.getAllowedTypes());
           jo.put("hash", newKeyset.hashCode());

           rc.response()
                   .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                   .end(jo.encode());
        }
    }

    private AdminKeyset setAdminKeyset(RoutingContext rc, JsonArray allowList, JsonArray allowedTypes, Integer siteId, Map<Integer, AdminKeyset> keysetsById, AdminKeyset keyset)
            throws Exception{
        int keysetId;
        String name;

        if (keyset == null) {
            keysetId = Collections.max(keysetsById.keySet()) + 1;
            name = "";
        } else {
            keysetId = keyset.getKeysetId();
            name = keyset.getName();
        }

        final Set<Integer> newlist = allowList.stream()
                .map(s -> (Integer) s)
                .collect(Collectors.toSet());

        Set<ClientType> newAllowedTypes = null;
        if(allowedTypes == null || allowedTypes.isEmpty()) {
            newAllowedTypes = new HashSet<>();
        } else {
            newAllowedTypes = allowedTypes.stream()
                    .map(s -> Enum.valueOf(ClientType.class, s.toString()))
                    .collect(Collectors.toSet());
        }

        final AdminKeyset newKeyset = new AdminKeyset(keysetId, siteId, name,
                newlist, Instant.now().getEpochSecond(), true, true, newAllowedTypes);

        keysetsById.put(keysetId, newKeyset);
        storeWriter.upload(keysetsById, null);
        //Create a new key
        this.keyManager.addKeysetKey(keysetId);
        return newKeyset;
    }

    private JsonObject jsonFullKeyset(AdminKeyset keyset) {
        JsonObject jo = new JsonObject();
        jo.put("keyset_id", keyset.getKeysetId());
        jo.put("site_id", keyset.getSiteId());
        jo.put("name", keyset.getName());
        jo.put("allowlist", keyset.getAllowedSites());
        jo.put("allowed_types", keyset.getAllowedTypes());
        jo.put("created", keyset.getCreated());
        jo.put("is_enabled", keyset.isEnabled());
        jo.put("is_default", keyset.isDefault());
        return jo;
    }
}
