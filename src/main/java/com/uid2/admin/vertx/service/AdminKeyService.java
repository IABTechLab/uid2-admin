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

import com.fasterxml.jackson.databind.ObjectWriter;
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

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class AdminKeyService implements IService {
    private final AuthMiddleware auth;
    private final WriteLock writeLock;
    private final IStorageManager storageManager;
    private final AdminUserProvider adminUserProvider;
    private final IKeyGenerator keyGenerator;
    private final ObjectWriter jsonWriter = JsonUtil.createJsonWriter();
    private final String adminKeyPrefix;

    public AdminKeyService(JsonObject config,
                           AuthMiddleware auth,
                           WriteLock writeLock,
                           IStorageManager storageManager,
                           AdminUserProvider adminUserProvider,
                           IKeyGenerator keyGenerator) {
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
                auth.handle(this::handleAdminList, Role.ADMINISTRATOR));

        router.get("/api/admin/reveal").handler(
                auth.handle(this::handleAdminReveal, Role.ADMINISTRATOR));

        router.post("/api/admin/add").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleAdminAdd(ctx);
            }
        }, Role.ADMINISTRATOR));

        router.post("/api/admin/del").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleAdminDel(ctx);
            }
        }, Role.ADMINISTRATOR));

        router.post("/api/admin/disable").blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleAdminDisable(ctx);
            }
        }, Role.ADMINISTRATOR));

        router.post("/api/admin/enable").blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleAdminEnable(ctx);
            }
        }, Role.ADMINISTRATOR));

        router.post("/api/admin/rekey").blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleAdminRekey(ctx);
            }
        }, Role.ADMINISTRATOR));

        router.post("/api/admin/roles").blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleAdminRoles(ctx);
            }
        }, Role.ADMINISTRATOR));
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

    private void handleAdminList(RoutingContext rc) {
        try {
            JsonArray ja = new JsonArray();
            Collection<AdminUser> collection = this.adminUserProvider.getAll();
            for (AdminUser a : collection) {
                JsonObject jo = new JsonObject();
                ja.add(jo);

                jo.put("name", a.getName());
                jo.put("contact", a.getContact());
                jo.put("roles", RequestUtil.getRolesSpec(a.getRoles()));
                jo.put("created", a.getCreated());
                jo.put("disabled", a.isDisabled());
            }

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(ja.encode());
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleAdminReveal(RoutingContext rc) {
        try {
            final String name = rc.queryParam("name").get(0);
            Optional<AdminUser> existingAdmin = this.adminUserProvider.getAll()
                    .stream().filter(a -> a.getName().equals(name))
                    .findFirst();
            if (!existingAdmin.isPresent()) {
                ResponseUtil.error(rc, 404, "admin not found");
                return;
            }

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(jsonWriter.writeValueAsString(existingAdmin.get()));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleAdminAdd(RoutingContext rc) {
        try {
            // refresh manually
            adminUserProvider.loadContent(adminUserProvider.getMetadata());

            final String name = rc.queryParam("name").get(0);
            Optional<AdminUser> existingAdmin = this.adminUserProvider.getAll()
                    .stream().filter(a -> a.getName().equals(name))
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

            // upload to storage
            storageManager.uploadAdminUsers(adminUserProvider, admins);

            // respond with new admin created
            rc.response().end(jsonWriter.writeValueAsString(newAdmin));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleAdminDel(RoutingContext rc) {
        try {
            // refresh manually
            adminUserProvider.loadContent(adminUserProvider.getMetadata());

            final String name = rc.queryParam("name").get(0);
            Optional<AdminUser> existingAdmin = this.adminUserProvider.getAll()
                    .stream().filter(a -> a.getName().equals(name))
                    .findFirst();
            if (!existingAdmin.isPresent()) {
                ResponseUtil.error(rc, 404, "admin not found");
                return;
            }

            List<AdminUser> admins = this.adminUserProvider.getAll()
                    .stream().sorted((a, b) -> (int) (a.getCreated() - b.getCreated()))
                    .collect(Collectors.toList());

            // delete admin from the array
            AdminUser a = existingAdmin.get();
            admins.remove(a);

            // upload to storage
            storageManager.uploadAdminUsers(adminUserProvider, admins);

            // respond with admin deleted
            rc.response().end(jsonWriter.writeValueAsString(a));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleAdminDisable(RoutingContext rc) {
        handleAdminDisable(rc, true);
    }

    private void handleAdminEnable(RoutingContext rc) {
        handleAdminDisable(rc, false);
    }

    private void handleAdminDisable(RoutingContext rc, boolean disableFlag) {
        try {
            // refresh manually
            adminUserProvider.loadContent(adminUserProvider.getMetadata());

            final String name = rc.queryParam("name").get(0);
            Optional<AdminUser> existingAdmin = this.adminUserProvider.getAll()
                    .stream().filter(a -> a.getName().equals(name))
                    .findFirst();
            if (!existingAdmin.isPresent()) {
                ResponseUtil.error(rc, 404, "admin not found");
                return;
            }

            List<AdminUser> admins = this.adminUserProvider.getAll()
                    .stream().sorted((a, b) -> (int) (a.getCreated() - b.getCreated()))
                    .collect(Collectors.toList());

            AdminUser a = existingAdmin.get();
            if (a.isDisabled() == disableFlag) {
                rc.fail(400, new Exception("no change needed"));
                return;
            }

            a.setDisabled(disableFlag);

            JsonObject response = new JsonObject();
            response.put("name", a.getName());
            response.put("contact", a.getContact());
            response.put("created", a.getCreated());
            response.put("disabled", a.isDisabled());

            // upload to storage
            storageManager.uploadAdminUsers(adminUserProvider, admins);

            // respond with admin disabled/enabled
            rc.response().end(response.encode());
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleAdminRekey(RoutingContext rc) {
        try {
            // refresh manually
            adminUserProvider.loadContent(adminUserProvider.getMetadata());

            final String name = rc.queryParam("name").get(0);
            Optional<AdminUser> existingAdmin = this.adminUserProvider.getAll()
                    .stream().filter(a -> a.getName().equals(name))
                    .findFirst();
            if (!existingAdmin.isPresent()) {
                ResponseUtil.error(rc, 404, "admin not found");
                return;
            }

            List<AdminUser> admins = this.adminUserProvider.getAll()
                    .stream().sorted((a, b) -> (int) (a.getCreated() - b.getCreated()))
                    .collect(Collectors.toList());

            AdminUser a = existingAdmin.get();
            String newKey = keyGenerator.generateRandomKeyString(32);
            if (this.adminKeyPrefix != null) newKey = this.adminKeyPrefix + newKey;
            a.setKey(newKey);

            // upload to storage
            storageManager.uploadAdminUsers(adminUserProvider, admins);

            // return admin with new key
            rc.response().end(jsonWriter.writeValueAsString(a));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleAdminRoles(RoutingContext rc) {
        try {
            // refresh manually
            adminUserProvider.loadContent(adminUserProvider.getMetadata());

            final String name = rc.queryParam("name").get(0);
            Optional<AdminUser> existingAdmin = this.adminUserProvider.getAll()
                    .stream().filter(a -> a.getName().equals(name))
                    .findFirst();
            if (!existingAdmin.isPresent()) {
                ResponseUtil.error(rc, 404, "admin not found");
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

            AdminUser a = existingAdmin.get();
            a.setRoles(roles);

            // upload to storage
            storageManager.uploadAdminUsers(adminUserProvider, admins);

            // return client with new key
            rc.response().end(jsonWriter.writeValueAsString(a));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }
}
