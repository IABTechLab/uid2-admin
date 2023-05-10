package com.uid2.admin.vertx.service;

import com.uid2.admin.store.reader.ISiteStore;
import com.uid2.admin.store.writer.KeyAclStoreWriter;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.auth.EncryptionKeyAcl;
import com.uid2.shared.auth.Role;
import com.uid2.shared.middleware.AuthMiddleware;
import com.uid2.shared.store.reader.RotatingKeyAclProvider;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

public class SharingService implements IService {
    private final AuthMiddleware auth;

    private final WriteLock writeLock;
    private final KeyAclStoreWriter storeWriter;
    private final RotatingKeyAclProvider keyAclProvider;
    private static final Logger LOGGER = LoggerFactory.getLogger(SharingService.class);

    public SharingService(AuthMiddleware auth,
                          WriteLock writeLock,
                          KeyAclStoreWriter storeWriter,
                          RotatingKeyAclProvider keyAclProvider) {
        this.auth = auth;
        this.writeLock = writeLock;
        this.storeWriter = storeWriter;
        this.keyAclProvider = keyAclProvider;
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


    private void handleKeyAclList(RoutingContext rc) {
        int site_id;
        try {
            site_id = Integer.parseInt(rc.pathParam("siteId"));
        } catch (Exception e) {
            LOGGER.warn("Failed to parse a site id from list request", e);
            rc.fail(400, e);
            return;
        }

        EncryptionKeyAcl acl = this.keyAclProvider.getSnapshot().getAllAcls().get(site_id);

        if (acl == null) {
            LOGGER.warn("Failed to find acl for site id: " + site_id);
            rc.fail(404);
            return;
        }

        JsonArray listedSites = new JsonArray();
        acl.getAccessList().stream().sorted().forEach((listedSiteId) -> listedSites.add(listedSiteId));
        JsonObject jo = new JsonObject();
        jo.put("whitelist", listedSites);
        jo.put("whitelist_hash", computeWhitelistHash(acl.getAccessList()));

        rc.response()
                .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end(jo.encode());
    }

    private void handleKeyAclListAll(RoutingContext rc) {
        try {
            JsonArray ja = new JsonArray();
            Map<Integer, EncryptionKeyAcl> collection = this.keyAclProvider.getSnapshot().getAllAcls();
            for (Map.Entry<Integer, EncryptionKeyAcl> acl : collection.entrySet()) {
                JsonArray listedSites = new JsonArray();
                acl.getValue().getAccessList().stream().sorted().forEach((listedSiteId) -> listedSites.add(listedSiteId));

                JsonObject jo = new JsonObject();
                jo.put("site_id", acl.getKey());
                jo.put("whitelist", listedSites);
                jo.put("whitelist_hash", computeWhitelistHash(acl.getValue().getAccessList()));
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
           int site_id;
           try {
               site_id = Integer.parseInt(rc.pathParam("siteId"));
           } catch (Exception e) {
               LOGGER.warn("Failed to parse a site id from list request", e);
               rc.fail(400, e);
               return;
           }

           try {
               keyAclProvider.loadContent();
           } catch (Exception e) {
               LOGGER.error("Failed to load key acls");
               rc.fail(500);
           }


           final Map<Integer, EncryptionKeyAcl> collection = this.keyAclProvider.getSnapshot().getAllAcls();
           EncryptionKeyAcl acl = collection.get(site_id);

           final JsonObject body = rc.body().asJsonObject();

           final JsonArray whitelist = body.getJsonArray("whitelist");
           final int whitelist_hash = body.getInteger("whitelist_hash");


           Set<Integer> old_list;
           if (acl != null) {
               old_list = acl.getAccessList();
           } else {
               old_list = new HashSet<>();
           }

           if (acl != null && whitelist_hash != computeWhitelistHash(old_list)) {
               rc.fail(409);
               return;
           }

           final EncryptionKeyAcl newAcl = new EncryptionKeyAcl(true, whitelist.stream()
                   .map(s -> (Integer) s)
                   .collect(Collectors.toSet()));

           collection.put(site_id, newAcl);
           try {
               storeWriter.upload(collection, null);
           } catch (Exception e) {
               rc.fail(500, e);
               return;
           }

           JsonObject jo = new JsonObject();
           jo.put("whitelist", whitelist);
           jo.put("whitelist_hash", whitelist_hash);

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
