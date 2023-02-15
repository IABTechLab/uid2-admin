package com.uid2.admin.vertx.service;

import com.uid2.admin.secret.IEncryptionKeyManager;
import com.uid2.admin.model.Site;
import com.uid2.admin.store.reader.ISiteStore;
import com.uid2.admin.store.writer.KeyAclStoreWriter;
import com.uid2.admin.vertx.RequestUtil;
import com.uid2.admin.vertx.ResponseUtil;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.auth.EncryptionKeyAcl;
import com.uid2.shared.auth.Role;
import com.uid2.shared.store.reader.RotatingKeyAclProvider;
import com.uid2.shared.middleware.AuthMiddleware;
import com.uid2.shared.model.SiteUtil;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.*;

public class KeyAclService implements IService {
    private final AuthMiddleware auth;
    private final WriteLock writeLock;
    private final KeyAclStoreWriter storeWriter;
    private final RotatingKeyAclProvider keyAclProvider;
    private final ISiteStore siteProvider;
    private final IEncryptionKeyManager keyManager;
    private static final Logger LOGGER = LoggerFactory.getLogger(KeyAclService.class);

    public KeyAclService(AuthMiddleware auth,
                         WriteLock writeLock,
                         KeyAclStoreWriter storeWriter,
                         RotatingKeyAclProvider keyAclProvider,
                         ISiteStore siteProvider,
                         IEncryptionKeyManager keyManager) {
        this.auth = auth;
        this.writeLock = writeLock;
        this.storeWriter = storeWriter;
        this.keyAclProvider = keyAclProvider;
        this.siteProvider = siteProvider;
        this.keyManager = keyManager;
    }

    @Override
    public void setupRoutes(Router router) {
        router.get("/api/keys_acl/list").handler(
                auth.handle(this::handleKeyAclList, Role.CLIENTKEY_ISSUER));

        router.post("/api/keys_acl/rewrite_metadata").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleRewriteMetadata(ctx);
            }
        }, Role.CLIENTKEY_ISSUER));

        router.post("/api/keys_acl/reset").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleKeyAclReset(ctx);
            }
        }, Role.CLIENTKEY_ISSUER));
        router.post("/api/keys_acl/update").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleKeyAclUpdate(ctx);
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

    private void handleKeyAclList(RoutingContext ctx) {
        try {
            JsonArray ja = new JsonArray();
            Map<Integer, EncryptionKeyAcl> collection = this.keyAclProvider.getSnapshot().getAllAcls();
            for (Map.Entry<Integer, EncryptionKeyAcl> acl : collection.entrySet()) {
                ja.add(toJson(acl.getKey(), acl.getValue()));
            }

            ctx.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(ja.encode());
        } catch (Exception e) {
            ctx.fail(500, e);
        }
    }

    private void handleKeyAclReset(RoutingContext ctx) {
        try {
            // refresh manually
            keyAclProvider.loadContent();

            final Site existingSite = RequestUtil.getSite(ctx, "site_id", siteProvider);
            if (existingSite == null) return;

            Boolean isWhitelist = RequestUtil.getKeyAclType(ctx);
            if (isWhitelist == null) return;

            this.keyManager.addSiteKey(existingSite.getId());

            final EncryptionKeyAcl newAcl = new EncryptionKeyAcl(isWhitelist, new HashSet<>());
            final Map<Integer, EncryptionKeyAcl> collection = this.keyAclProvider.getSnapshot().getAllAcls();
            collection.put(existingSite.getId(), newAcl);

            storeWriter.upload(collection, null);

            ctx.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(toJson(existingSite.getId(), newAcl).encode());
        } catch (Exception e) {
            ctx.fail(500, e);
        }
    }

    private void handleKeyAclUpdate(RoutingContext ctx) {
        try {
            // refresh manually
            keyAclProvider.loadContent();

            final Site site = RequestUtil.getSite(ctx, "site_id", siteProvider);
            if (site == null) return;

            final Map<Integer, EncryptionKeyAcl> collection = this.keyAclProvider.getSnapshot().getAllAcls();
            final EncryptionKeyAcl acl = collection.get(site.getId());
            if (acl == null) {
                ResponseUtil.error(ctx, 404, "ACL not found");
                return;
            }

            final Set<Integer> addedSites = RequestUtil.getIds(ctx.queryParam("add"));
            if (addedSites == null) {
                ResponseUtil.error(ctx, 400, "invalid added sites");
                return;
            }

            final Set<Integer> removedSites = RequestUtil.getIds(ctx.queryParam("remove"));
            if (removedSites == null) {
                ResponseUtil.error(ctx, 400, "invalid removed sites");
                return;
            }

            boolean added = false;
            boolean removed = false;
            for (int addedSiteId : addedSites) {
                if (addedSiteId == site.getId()) {
                    continue;
                } else if (!SiteUtil.isValidSiteId(addedSiteId)) {
                    ResponseUtil.error(ctx, 400, "invalid added site id: " + addedSiteId);
                    return;
                } else if (this.siteProvider.getSite(addedSiteId) == null) {
                    ResponseUtil.error(ctx, 404, "unknown added site id: " + addedSiteId);
                    return;
                } else if (acl.getAccessList().add(addedSiteId)) {
                    added = true;
                }
            }
            for (int removedSiteId : removedSites) {
                if (acl.getAccessList().remove(removedSiteId)) {
                    removed = true;
                }
            }

            // we need a new site key if somebody is losing access to the current site one
            final boolean needNewSiteKey = (acl.getIsWhitelist() && removed) || (!acl.getIsWhitelist() && added);
            if (needNewSiteKey) {
                this.keyManager.addSiteKey(site.getId());
            }

            if (added || removed) {
                storeWriter.upload(collection, null);
            }

            ctx.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(toJson(site.getId(), acl).encode());
        } catch (Exception e) {
            ctx.fail(500, e);
        }
    }

    private JsonObject toJson(int siteId, EncryptionKeyAcl acl) {
        JsonArray listedSites = new JsonArray();
        acl.getAccessList().stream().sorted().forEach((listedSiteId) -> listedSites.add(listedSiteId));

        JsonObject jo = new JsonObject();
        jo.put("site_id", siteId);
        jo.put(acl.getIsWhitelist() ? "whitelist" : "blacklist", listedSites);

        return jo;
    }
}
