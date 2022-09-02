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

import com.amazonaws.util.StringUtils;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.Constants;
import com.uid2.admin.audit.Actions;
import com.uid2.admin.audit.IAuditMiddleware;
import com.uid2.admin.audit.OperationModel;
import com.uid2.admin.audit.Type;
import com.uid2.admin.auth.AdminUser;
import com.uid2.admin.auth.AdminUserProvider;
import com.uid2.admin.secret.IKeyGenerator;
import com.uid2.admin.store.IStorageManager;
import com.uid2.admin.vertx.JsonUtil;
import com.uid2.admin.vertx.RequestUtil;
import com.uid2.admin.vertx.ResponseUtil;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.auth.Role;
import com.uid2.shared.middleware.AuthMiddleware;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.codec.digest.DigestUtils;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AdminKeyService implements IService {
    private final IAuditMiddleware audit;
    private final AuthMiddleware auth;
    private final WriteLock writeLock;
    private final IStorageManager storageManager;
    private final AdminUserProvider adminUserProvider;
    private final IKeyGenerator keyGenerator;
    private final ObjectWriter jsonWriter = JsonUtil.createJsonWriter();
    private final String adminKeyPrefix;

    public AdminKeyService(JsonObject config,
                           IAuditMiddleware audit,
                           AuthMiddleware auth,
                           WriteLock writeLock,
                           IStorageManager storageManager,
                           AdminUserProvider adminUserProvider,
                           IKeyGenerator keyGenerator) {
        this.audit = audit;
        this.auth = auth;
        this.writeLock = writeLock;
        this.storageManager = storageManager;
        this.adminUserProvider = adminUserProvider;
        this.keyGenerator = keyGenerator;

        this.adminKeyPrefix = config.getString("admin_key_prefix");
    }

    @Override
    public void setupRoutes(Router router) {

        router.get("/api/admin/metadata").handler(
                auth.handle(this::handleAdminMetadata, Role.ADMINISTRATOR));

        router.get("/api/admin/list").handler(
                auth.handle(ctx -> this.handleAdminList(ctx, audit.handle(ctx)), Role.ADMINISTRATOR));

        router.get("/api/admin/reveal").handler(
                auth.handle(ctx -> this.handleAdminReveal(ctx, audit.handle(ctx)), Role.ADMINISTRATOR));

        router.post("/api/admin/add").blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleAdminAdd(ctx, audit.handle(ctx));
            }
        }, Role.ADMINISTRATOR));

        router.post("/api/admin/del").blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleAdminDel(ctx, audit.handle(ctx));
            }
        }, Role.ADMINISTRATOR));

        router.post("/api/admin/disable").blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleAdminDisable(ctx, audit.handle(ctx));
            }
        }, Role.ADMINISTRATOR));

        router.post("/api/admin/enable").blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleAdminEnable(ctx, audit.handle(ctx));
            }
        }, Role.ADMINISTRATOR));

        router.post("/api/admin/rekey").blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleAdminRekey(ctx, audit.handle(ctx));
            }
        }, Role.ADMINISTRATOR));

        router.post("/api/admin/roles").blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleAdminRoles(ctx, audit.handle(ctx));
            }
        }, Role.ADMINISTRATOR));
    }

    @Override
    public Collection<OperationModel> qldbSetup(){
        try {
            Collection<AdminUser> adminUsers = adminUserProvider.getAll();
            Collection<OperationModel> newModels = new HashSet<>();
            for (AdminUser a : adminUsers) {
                newModels.add(new OperationModel(Type.ADMIN, a.getName(), null,
                        DigestUtils.sha256Hex(jsonWriter.writeValueAsString(a)), null));
            }
            return newModels;
        } catch (Exception e) {
            e.printStackTrace();
            return new HashSet<>();
        }
    }

    @Override
    public Type tableType(){
        return Type.ADMIN;
    }

    private void handleAdminMetadata(RoutingContext rc) {
        try {
            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(adminUserProvider.getMetadata().encode());
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleAdminList(RoutingContext rc, Function<List<OperationModel>, Boolean> fxn) {
        try {
            JsonArray ja = new JsonArray();
            Collection<AdminUser> collection = this.adminUserProvider.getAll();
            for (AdminUser a : collection) {
                ja.add(adminToJson(a));
            }

            List<OperationModel> modelList = Collections.singletonList(new OperationModel(Type.ADMIN, Constants.DEFAULT_ITEM_KEY, Actions.LIST,
                    null, "list admins"));
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

    private void handleAdminReveal(RoutingContext rc, Function<List<OperationModel>, Boolean> fxn) {
        try {
            AdminUser a = getAdminUser(rc.queryParam("name"));
            if (a == null) {
                ResponseUtil.error(rc, 404, "admin not found");
                return;
            }

            List<OperationModel> modelList = Collections.singletonList(new OperationModel(Type.ADMIN, a.getName(), Actions.REVEAL,
                    DigestUtils.sha256Hex(jsonWriter.writeValueAsString(a)), "revealed " + a.getName()));
            if(!fxn.apply(modelList)){
                ResponseUtil.error(rc, 500, "failed");
                return;
            }
            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(jsonWriter.writeValueAsString(a));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleAdminAdd(RoutingContext rc, Function<List<OperationModel>, Boolean> fxn) {
        try {
            // refresh manually
            adminUserProvider.loadContent(adminUserProvider.getMetadata());

            final String name = rc.queryParam("name").get(0);
            Optional<AdminUser> existingAdmin = this.adminUserProvider.getAll()
                    .stream().filter(a -> name.equals(a.getName()))
                    .findFirst();
            if (existingAdmin.isPresent()) {
                ResponseUtil.error(rc, 400, "admin existed");
                return;
            }

            Set<Role> roles = RequestUtil.getRoles(rc.queryParam("roles").get(0));
            if (roles == null) {
                ResponseUtil.error(rc, 400, "incorrect or none roles specified");
                return;
            }

            List<AdminUser> admins = this.adminUserProvider.getAll()
                    .stream().sorted((a, b) -> (int) (a.getCreated() - b.getCreated()))
                    .collect(Collectors.toList());

            // create a random key
            String key = keyGenerator.generateRandomKeyString(32);
            if (this.adminKeyPrefix != null) key = this.adminKeyPrefix + key;

            // create new admin
            long created = Instant.now().getEpochSecond();
            AdminUser newAdmin = new AdminUser(key, name, name, created, roles, false);

            // add admin to the array
            admins.add(newAdmin);

            List<OperationModel> modelList = Collections.singletonList(new OperationModel(Type.ADMIN, name, Actions.CREATE,
                    DigestUtils.sha256Hex(jsonWriter.writeValueAsString(newAdmin)), "created " + name));
            if(!fxn.apply(modelList)){
                ResponseUtil.error(rc, 500, "failed");
                return;
            }

            // upload to storage
            storageManager.uploadAdminUsers(adminUserProvider, admins);

            // respond with new admin created
            rc.response().end(jsonWriter.writeValueAsString(newAdmin));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleAdminDel(RoutingContext rc, Function<List<OperationModel>, Boolean> fxn) {
        try {
            // refresh manually
            adminUserProvider.loadContent(adminUserProvider.getMetadata());

            AdminUser a = getAdminUser(rc.queryParam("name"));
            if (a == null) {
                ResponseUtil.error(rc, 404, "admin not found");
                return;
            }

            List<AdminUser> admins = this.adminUserProvider.getAll()
                    .stream().sorted((x, y) -> (int) (x.getCreated() - y.getCreated()))
                    .collect(Collectors.toList());

            // delete admin from the array
            admins.remove(a);

            List<OperationModel> modelList = Collections.singletonList(new OperationModel(Type.ADMIN, a.getName(), Actions.DELETE,
                    DigestUtils.sha256Hex(jsonWriter.writeValueAsString(a)), "deleted " + a.getName()));
            if(!fxn.apply(modelList)){
                ResponseUtil.error(rc, 500, "failed");
                return;
            }

            // upload to storage
            storageManager.uploadAdminUsers(adminUserProvider, admins);

            // respond with admin deleted
            rc.response().end(jsonWriter.writeValueAsString(a));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleAdminDisable(RoutingContext rc, Function<List<OperationModel>, Boolean> fxn) {
        handleAdminDisable(rc,  fxn, true);
    }

    private void handleAdminEnable(RoutingContext rc, Function<List<OperationModel>, Boolean> fxn) {
        handleAdminDisable(rc, fxn, false);
    }

    private void handleAdminDisable(RoutingContext rc, Function<List<OperationModel>, Boolean> fxn, boolean disableFlag) {
        try {
            // refresh manually
            adminUserProvider.loadContent(adminUserProvider.getMetadata());

            AdminUser a = getAdminUser(rc.queryParam("name"));
            if (a == null) {
                ResponseUtil.error(rc, 404, "admin not found");
                return;
            }

            List<AdminUser> admins = this.adminUserProvider.getAll()
                    .stream().sorted((x, y) -> (int) (x.getCreated() - y.getCreated()))
                    .collect(Collectors.toList());

            if (a.isDisabled() == disableFlag) {
                rc.fail(400, new Exception("no change needed"));
                return;
            }

            a.setDisabled(disableFlag);

            JsonObject response = adminToJson(a);

            List<OperationModel> modelList = Collections.singletonList(new OperationModel(Type.ADMIN, a.getName(), disableFlag ? Actions.DISABLE : Actions.ENABLE,
                    DigestUtils.sha256Hex(jsonWriter.writeValueAsString(a)), (disableFlag ? "disabled " : "enabled ") + a.getName()));
            if(!fxn.apply(modelList)){
                ResponseUtil.error(rc, 500, "failed");
                return;
            }

            // upload to storage
            storageManager.uploadAdminUsers(adminUserProvider, admins);

            // respond with admin disabled/enabled
            rc.response().end(response.encode());
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleAdminRekey(RoutingContext rc, Function<List<OperationModel>, Boolean> fxn) {
        try {
            // refresh manually
            adminUserProvider.loadContent(adminUserProvider.getMetadata());

            AdminUser a = getAdminUser(rc.queryParam("name"));
            if (a == null) {
                ResponseUtil.error(rc, 404, "admin not found");
                return;
            }

            List<AdminUser> admins = this.adminUserProvider.getAll()
                    .stream().sorted((x, y) -> (int) (x.getCreated() - y.getCreated()))
                    .collect(Collectors.toList());

            String newKey = keyGenerator.generateRandomKeyString(32);
            if (this.adminKeyPrefix != null) newKey = this.adminKeyPrefix + newKey;
            a.setKey(newKey);

            List<OperationModel> modelList = Collections.singletonList(new OperationModel(Type.ADMIN, a.getName(), Actions.UPDATE,
                    DigestUtils.sha256Hex(jsonWriter.writeValueAsString(a)), "rekeyed " + a.getName()));
            if(!fxn.apply(modelList)){
                ResponseUtil.error(rc, 500, "failed");
                return;
            }

            // upload to storage
            storageManager.uploadAdminUsers(adminUserProvider, admins);

            // return admin with new key
            rc.response().end(jsonWriter.writeValueAsString(a));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleAdminRoles(RoutingContext rc, Function<List<OperationModel>, Boolean> fxn) {
        try {
            // refresh manually
            adminUserProvider.loadContent(adminUserProvider.getMetadata());

            AdminUser a = getAdminUser(rc.queryParam("name"));
            if (a == null) {
                ResponseUtil.error(rc, 404, "admin not found");
                return;
            }

            Set<Role> roles = RequestUtil.getRoles(rc.queryParam("roles").get(0));
            if (roles == null) {
                ResponseUtil.error(rc, 400, "incorrect or none roles specified");
                return;
            }

            List<AdminUser> admins = this.adminUserProvider.getAll()
                    .stream().sorted((x, y) -> (int) (x.getCreated() - y.getCreated()))
                    .collect(Collectors.toList());

            a.setRoles(roles);

            List<String> stringRoleList = new ArrayList<>();
            for (Role role : roles) {
                stringRoleList.add(role.toString());
            }
            List<OperationModel> modelList = Collections.singletonList(new OperationModel(Type.ADMIN, a.getName(), Actions.UPDATE,
                    DigestUtils.sha256Hex(jsonWriter.writeValueAsString(a)), "set roles of " + a.getName() +
                    " to {" + StringUtils.join(",", stringRoleList.toArray(new String[0])) + "}"));
            if(!fxn.apply(modelList)){
                ResponseUtil.error(rc, 500, "failed");
                return;
            }

            // upload to storage
            storageManager.uploadAdminUsers(adminUserProvider, admins);

            // return client with new key
            rc.response().end(jsonWriter.writeValueAsString(a));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    /**
     * Writes an AdminUser to Json format, without the sensitive key field.
     * @param a the AdminUser to write
     * @return a JsonObject representing a, without a key field.
     */
    public JsonObject adminToJson(AdminUser a){
        JsonObject jo = new JsonObject();

        jo.put("name", a.getName());
        jo.put("contact", a.getContact());
        jo.put("roles", RequestUtil.getRolesSpec(a.getRoles()));
        jo.put("created", a.getCreated());
        jo.put("disabled", a.isDisabled());

        return jo;
    }

    /**
     * Returns an arbitrary admin with the same name as the first item in the passed list.
     * @param names a list of names of admins
     * @return an arbitrary admin with the same name as names.get(0), or null if names is empty
     * or no admin with the specified name exists.
     */
    private AdminUser getAdminUser(List<String> names){
        if(names.isEmpty()){
            return null;
        }
        final String name = names.get(0);
        Optional<AdminUser> existingAdmin = this.adminUserProvider.getAll()
                .stream().filter(a -> name.equals(a.getName()))
                .findFirst();
        return existingAdmin.orElse(null);
    }
}
