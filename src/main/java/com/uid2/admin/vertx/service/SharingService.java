package com.uid2.admin.vertx.service;

import com.uid2.admin.store.reader.ISiteStore;
import com.uid2.admin.store.writer.KeyAclStoreWriter;
import com.uid2.admin.store.writer.KeysetStoreWriter;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.auth.EncryptionKeyAcl;
import com.uid2.shared.auth.Keyset;
import com.uid2.shared.auth.Role;
import com.uid2.shared.middleware.AuthMiddleware;
import com.uid2.shared.store.reader.RotatingKeyAclProvider;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(SharingService.class);

    public SharingService(AuthMiddleware auth,
                          WriteLock writeLock,
                          KeysetStoreWriter storeWriter,
                          RotatingKeysetProvider keysetProvider) {
        this.auth = auth;
        this.writeLock = writeLock;
        this.storeWriter = storeWriter;
        this.keysetProvider = keysetProvider;
    }

    @Override
    public void setupRoutes(Router router) {
        router.get("/api/sharing/lists").handler(
                auth.handle(this::handleKeyAclListAll, Role.SHARING_PORTAL)
        );
        router.get("/api/sharing/list/:siteId").handler(
                auth.handle(this::handleKeyAclList, Role.SHARING_PORTAL)
        );
        router.post("/api/sharing/list/:siteId").handler(
                auth.handle(this::handleKeyAclSet, Role.SHARING_PORTAL)
        );
    }

    private Keyset getDefaultKeyset(Map<Integer, Keyset> keysets, Integer siteId) {
           for(Keyset keyset: keysets.values()) {
               if(keyset.getSiteId() == siteId && keyset.isDefault()) {
                   return keyset;
               }
           }
           return null;
    }

    private void handleKeyAclList(RoutingContext rc) {
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
        keyset.getAllowedSites().stream().sorted().forEach((listedSiteId) -> listedSites.add(listedSiteId));
        JsonObject jo = new JsonObject();
        jo.put("allowlist", listedSites);
        jo.put("hash", keyset.hashCode());

        rc.response()
                .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end(jo.encode());
    }

    private void handleKeyAclListAll(RoutingContext rc) {
        try {
            JsonArray ja = new JsonArray();
            Map<Integer, Keyset> collection = this.keysetProvider.getSnapshot().getAllKeysets();
            for (Map.Entry<Integer, Keyset> keyset : collection.entrySet()) {
                JsonArray listedSites = new JsonArray();
                keyset.getValue().getAllowedSites().stream().sorted().forEach((listedSiteId) -> listedSites.add(listedSiteId));

                JsonObject jo = new JsonObject();
                jo.put("site_id", keyset.getValue().getSiteId());
                jo.put("keyset_id", keyset.getValue().getKeysetId());
                jo.put("allowlist", listedSites);
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

    private void handleKeyAclSet(RoutingContext rc) {
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

           final JsonArray whitelist = body.getJsonArray("allowlist");
           final int whitelist_hash = body.getInteger("hash");


           Set<Integer> old_list;
           if (keyset != null) {
               old_list = keyset.getAllowedSites();
           } else {
               old_list = new HashSet<>();
           }

           if (keyset != null && whitelist_hash != keyset.hashCode()) {
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
               storeWriter.upload(collection.values(), null);
           } catch (Exception e) {
               rc.fail(500, e);
               return;
           }

           JsonObject jo = new JsonObject();
           jo.put("allowlist", whitelist);
           jo.put("hash", newKeyset.hashCode());

           rc.response()
                   .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                   .end(jo.encode());
        }
    }

    private int computeWhitelistHash(Set<Integer> list)
    {
        return Objects.hash(list);
    }
}
