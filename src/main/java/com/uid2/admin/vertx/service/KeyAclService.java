// Copyright (c) 2021 The Trade Desk, Inc
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice,
//    this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice,
//    this list of conditions and the following disclaimer in the documentation
//    and/or other materials provided with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
// POSSIBILITY OF SUCH DAMAGE.

package com.uid2.admin.vertx.service;

import com.uid2.admin.Constants;
import com.uid2.admin.audit.Actions;
import com.uid2.admin.audit.AuditMiddleware;
import com.uid2.admin.audit.OperationModel;
import com.uid2.admin.audit.Type;
import com.uid2.admin.secret.IEncryptionKeyManager;
import com.uid2.admin.model.Site;
import com.uid2.admin.store.ISiteStore;
import com.uid2.admin.store.IStorageManager;
import com.uid2.admin.vertx.RequestUtil;
import com.uid2.admin.vertx.ResponseUtil;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.auth.EncryptionKeyAcl;
import com.uid2.shared.auth.Role;
import com.uid2.shared.auth.RotatingKeyAclProvider;
import com.uid2.shared.middleware.AuthMiddleware;
import com.uid2.shared.model.SiteUtil;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.*;
import java.util.function.Function;

public class KeyAclService implements IService {
    private final AuditMiddleware audit;
    private final AuthMiddleware auth;
    private final WriteLock writeLock;
    private final IStorageManager storageManager;
    private final RotatingKeyAclProvider keyAclProvider;
    private final ISiteStore siteProvider;
    private final IEncryptionKeyManager keyManager;

    public KeyAclService(AuditMiddleware audit,
                         AuthMiddleware auth,
                         WriteLock writeLock,
                         IStorageManager storageManager,
                         RotatingKeyAclProvider keyAclProvider,
                         ISiteStore siteProvider,
                         IEncryptionKeyManager keyManager) {
        this.audit = audit;
        this.auth = auth;
        this.writeLock = writeLock;
        this.storageManager = storageManager;
        this.keyAclProvider = keyAclProvider;
        this.siteProvider = siteProvider;
        this.keyManager = keyManager;
    }

    @Override
    public void setupRoutes(Router router) {
        router.get("/api/keys_acl/list").handler(
                auth.handle(ctx -> this.handleKeyAclList(ctx, audit.handle(ctx)), Role.CLIENTKEY_ISSUER));
        router.post("/api/keys_acl/reset").blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleKeyAclReset(ctx, audit.handle(ctx));
            }
        }, Role.CLIENTKEY_ISSUER));
        router.post("/api/keys_acl/update").blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleKeyAclUpdate(ctx, audit.handle(ctx));
            }
        }, Role.CLIENTKEY_ISSUER));
    }

    @Override
    public Collection<OperationModel> qldbSetup(){
        try {
            Map<Integer, EncryptionKeyAcl> mapAcl = keyAclProvider.getSnapshot().getAllAcls();
            Collection<OperationModel> newModels = new HashSet<>();
            for (int i : mapAcl.keySet()) {
                JsonObject jo = toJson(i, mapAcl.get(i));
                newModels.add(new OperationModel(Type.KEYACL, String.valueOf(i), null,
                        DigestUtils.sha256Hex(jo.toString()), null));
            }
            return newModels;
        } catch (Exception e) {
            e.printStackTrace();
            return new HashSet<>();
        }
    }

    @Override
    public Type tableType(){
        return Type.KEYACL;
    }

    private void handleKeyAclList(RoutingContext rc, Function<List<OperationModel>, Boolean> fxn) {
        try {
            JsonArray ja = new JsonArray();
            Map<Integer, EncryptionKeyAcl> collection = this.keyAclProvider.getSnapshot().getAllAcls();
            for (Map.Entry<Integer, EncryptionKeyAcl> acl : collection.entrySet()) {
                ja.add(toJson(acl.getKey(), acl.getValue()));
            }

            List<OperationModel> modelList = Collections.singletonList(new OperationModel(Type.KEYACL,
                    Constants.DEFAULT_ITEM_KEY, Actions.LIST, null, "list keyacl"));
            if(!fxn.apply(modelList)){
                ResponseUtil.error(rc, 500, "failed");
                return;
            }

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(ja.encode());
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleKeyAclReset(RoutingContext rc, Function<List<OperationModel>, Boolean> fxn) {
        try {
            // refresh manually
            keyAclProvider.loadContent();

            final Site existingSite = RequestUtil.getSite(rc, "site_id", siteProvider);
            if (existingSite == null) return;

            Boolean isWhitelist = RequestUtil.getKeyAclType(rc);
            if (isWhitelist == null) return;

            this.keyManager.addSiteKey(existingSite.getId());

            final EncryptionKeyAcl newAcl = new EncryptionKeyAcl(isWhitelist, new HashSet<>());
            final Map<Integer, EncryptionKeyAcl> collection = this.keyAclProvider.getSnapshot().getAllAcls();
            collection.put(existingSite.getId(), newAcl);

            List<OperationModel> modelList = Collections.singletonList(new OperationModel(Type.KEYACL, String.valueOf(existingSite.getId()),
                    Actions.UPDATE, DigestUtils.sha256Hex(toJson(existingSite.getId(), newAcl).toString()), "reset acl of " + existingSite.getId()));
            if(!fxn.apply(modelList)){
                ResponseUtil.error(rc, 500, "failed");
                return;
            }

            storageManager.uploadKeyAcls(keyAclProvider, collection);

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(toJson(existingSite.getId(), newAcl).encode());
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleKeyAclUpdate(RoutingContext rc, Function<List<OperationModel>, Boolean> fxn) {
        try {
            // refresh manually
            keyAclProvider.loadContent();

            final Site site = RequestUtil.getSite(rc, "site_id", siteProvider);
            if (site == null) return;

            final Map<Integer, EncryptionKeyAcl> collection = this.keyAclProvider.getSnapshot().getAllAcls();
            final EncryptionKeyAcl acl = collection.get(site.getId());
            if (acl == null) {
                ResponseUtil.error(rc, 404, "ACL not found");
                return;
            }

            final Set<Integer> addedSites = RequestUtil.getIds(rc.queryParam("add"));
            if (addedSites == null) {
                ResponseUtil.error(rc, 400, "invalid added sites");
                return;
            }

            final Set<Integer> removedSites = RequestUtil.getIds(rc.queryParam("remove"));
            if (removedSites == null) {
                ResponseUtil.error(rc, 400, "invalid removed sites");
                return;
            }

            boolean added = false;
            boolean removed = false;
            for (int addedSiteId : addedSites) {
                if (addedSiteId == site.getId()) {
                    continue;
                } else if (!SiteUtil.isValidSiteId(addedSiteId)) {
                    ResponseUtil.error(rc, 400, "invalid added site id: " + addedSiteId);
                    return;
                } else if (this.siteProvider.getSite(addedSiteId) == null) {
                    ResponseUtil.error(rc, 404, "unknown added site id: " + addedSiteId);
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

            List<OperationModel> modelList = Collections.singletonList(new OperationModel(Type.KEYACL, String.valueOf(site.getId()),
                    Actions.UPDATE, DigestUtils.sha256Hex(toJson(site.getId(), acl).toString()), "updated acl of " + site.getId()));
            if(!fxn.apply(modelList)){
                ResponseUtil.error(rc, 500, "failed");
                return;
            }

            if (added || removed) {
                storageManager.uploadKeyAcls(keyAclProvider, collection);
            }

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(toJson(site.getId(), acl).encode());
        } catch (Exception e) {
            rc.fail(500, e);
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
