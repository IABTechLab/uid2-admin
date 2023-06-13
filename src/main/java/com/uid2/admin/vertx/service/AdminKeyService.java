package com.uid2.admin.vertx.service;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.auth.AdminUser;
import com.uid2.admin.auth.AdminUserProvider;
import com.uid2.admin.secret.IKeyGenerator;
import com.uid2.admin.store.writer.AdminUserStoreWriter;
import com.uid2.admin.store.writer.ClientKeyStoreWriter;
import com.uid2.admin.store.writer.EncryptionKeyStoreWriter;
import com.uid2.admin.store.writer.KeyAclStoreWriter;
import com.uid2.admin.vertx.JsonUtil;
import com.uid2.admin.vertx.RequestUtil;
import com.uid2.admin.vertx.ResponseUtil;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.auth.Role;
import com.uid2.shared.middleware.AuthMiddleware;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class AdminKeyService implements IService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminKeyService.class);

    private final AuthMiddleware auth;
    private final WriteLock writeLock;
    private final AdminUserStoreWriter storeWriter;
    private final AdminUserProvider adminUserProvider;
    private final IKeyGenerator keyGenerator;
    private final ObjectWriter jsonWriter = JsonUtil.createJsonWriter();
    private final String adminKeyPrefix;
    private final ClientKeyStoreWriter clientKeyStoreWriter;
    private final EncryptionKeyStoreWriter encryptionKeyStoreWriter;
    private final KeyAclStoreWriter keyAclStoreWriter;

    public AdminKeyService(JsonObject config,
                           AuthMiddleware auth,
                           WriteLock writeLock,
                           AdminUserStoreWriter storeWriter,
                           AdminUserProvider adminUserProvider,
                           IKeyGenerator keyGenerator,
                           ClientKeyStoreWriter clientKeyStoreWriter,
                           EncryptionKeyStoreWriter encryptionKeyStoreWriter,
                           KeyAclStoreWriter keyAclStoreWriter) {
        this.auth = auth;
        this.writeLock = writeLock;
        this.storeWriter = storeWriter;
        this.adminUserProvider = adminUserProvider;
        this.keyGenerator = keyGenerator;
        this.clientKeyStoreWriter = clientKeyStoreWriter;
        this.encryptionKeyStoreWriter = encryptionKeyStoreWriter;
        this.keyAclStoreWriter = keyAclStoreWriter;
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

        router.post("/api/admin/roles").blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleAdminRoles(ctx);
            }
        }, Role.ADMINISTRATOR));
        router.post("/api/admin/rewrite_metadata").blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleRewriteMetadata(ctx);
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

            final String name = rc.queryParam("name").isEmpty() ? "" : rc.queryParam("name").get(0).trim();
            if (name == null || name.isEmpty()) {
                ResponseUtil.error(rc, 400, "must specify a valid admin name");
                return;
            }

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
                    .stream().sorted(Comparator.comparing(AdminUser::getCreated))
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
            storeWriter.upload(admins);

            // respond with new admin created
            rc.response().end(jsonWriter.writeValueAsString(newAdmin));
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
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
            storeWriter.upload(admins);

            // respond with admin deleted
            rc.response().end(jsonWriter.writeValueAsString(a));
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
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
            storeWriter.upload(admins);

            // respond with admin disabled/enabled
            rc.response().end(response.encode());
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
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
                    .stream().sorted(Comparator.comparing(AdminUser::getCreated))
                    .collect(Collectors.toList());

            AdminUser a = existingAdmin.get();
            a.setRoles(roles);

            // upload to storage
            storeWriter.upload(admins);

            // return client with new key
            rc.response().end(jsonWriter.writeValueAsString(a));
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            rc.fail(500, e);
        }
    }

    private void handleRewriteMetadata(RoutingContext rc) {
        try {
            clientKeyStoreWriter.rewriteMeta();
            encryptionKeyStoreWriter.rewriteMeta();
            keyAclStoreWriter.rewriteMeta();
            JsonObject json = new JsonObject();
            json.put("Result", "Successfully rewrote global metadata files of Client Keys/Encryption Keys/Encryption Key ACLs");
            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(jsonWriter.writeValueAsString(json));

        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            rc.fail(500, e);
        }
    }
}
