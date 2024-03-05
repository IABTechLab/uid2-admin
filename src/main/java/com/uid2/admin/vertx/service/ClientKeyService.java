package com.uid2.admin.vertx.service;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.auth.AdminAuthMiddleware;
import com.uid2.admin.auth.RevealedKey;
import com.uid2.admin.legacy.LegacyClientKey;
import com.uid2.admin.legacy.LegacyClientKeyStoreWriter;
import com.uid2.admin.legacy.RotatingLegacyClientKeyProvider;
import com.uid2.admin.managers.KeysetManager;
import com.uid2.admin.vertx.JsonUtil;
import com.uid2.admin.vertx.RequestUtil;
import com.uid2.admin.vertx.ResponseUtil;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.Site;
import com.uid2.shared.secret.IKeyGenerator;
import com.uid2.shared.secret.KeyHashResult;
import com.uid2.shared.secret.KeyHasher;
import com.uid2.shared.store.ISiteStore;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ClientKeyService implements IService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientKeyService.class);
    private static final ObjectWriter JSON_WRITER = JsonUtil.createJsonWriter();

    private final AdminAuthMiddleware auth;
    private final WriteLock writeLock;
    private final LegacyClientKeyStoreWriter storeWriter;
    private final RotatingLegacyClientKeyProvider clientKeyProvider;
    private final ISiteStore siteProvider;
    private final KeysetManager keysetManager;
    private final IKeyGenerator keyGenerator;
    private final KeyHasher keyHasher;
    private final String clientKeyPrefix;

    public ClientKeyService(JsonObject config,
                            AdminAuthMiddleware auth,
                            WriteLock writeLock,
                            LegacyClientKeyStoreWriter storeWriter,
                            RotatingLegacyClientKeyProvider clientKeyProvider,
                            ISiteStore siteProvider,
                            KeysetManager keysetManager,
                            IKeyGenerator keyGenerator,
                            KeyHasher keyHasher) {
        this.auth = auth;
        this.writeLock = writeLock;
        this.storeWriter = storeWriter;
        this.clientKeyProvider = clientKeyProvider;
        this.siteProvider = siteProvider;
        this.keysetManager = keysetManager;
        this.keyGenerator = keyGenerator;
        this.keyHasher = keyHasher;
        this.clientKeyPrefix = config.getString("client_key_prefix");
    }

    @Override
    public void setupRoutes(Router router) {
        router.get("/api/client/metadata").handler(
            auth.handle(this::handleClientMetadata, Role.DEFAULT));
        router.post("/api/client/rewrite_metadata").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleRewriteMetadata(ctx);
            }
        }, Role.PRIVILEGED));

        router.get("/api/client/list").handler(
            auth.handle(this::handleClientList, Role.DEFAULT));

        router.get("/api/client/list/:siteId").handler(
            auth.handle(this::handleClientListBySite, Role.DEFAULT, Role.SHARING_PORTAL));

        router.get("/api/client/keyId").handler(
            auth.handle(this::handleClientByKeyId, Role.DEFAULT, Role.SHARING_PORTAL));

        router.get("/api/client/contact").handler(
            auth.handle(this::handleClientByContact, Role.DEFAULT, Role.SHARING_PORTAL));

        router.get("/api/client/reveal").handler(
            auth.handle(this::handleClientReveal, Role.PRIVILEGED));

        router.post("/api/client/add").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleClientAdd(ctx);
            }
        }, Role.DEFAULT, Role.SHARING_PORTAL));

        router.post("/api/client/del").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleClientDel(ctx);
            }
        }, Role.SUPER_USER));

        router.post("/api/client/update").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleClientUpdate(ctx);
            }
        }, Role.DEFAULT));

        router.post("/api/client/disable").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleClientDisable(ctx);
            }
        }, Role.DEFAULT, Role.SHARING_PORTAL));

        router.post("/api/client/enable").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleClientEnable(ctx);
            }
        }, Role.DEFAULT));

        router.post("/api/client/roles").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleClientRoles(ctx);
            }
        }, Role.PRIVILEGED, Role.SHARING_PORTAL));

        router.post("/api/client/contact").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleClientContact(ctx);
            }
        }, Role.DEFAULT));

        router.post("/api/client/rename").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleClientRename(ctx);
            }
        }, Role.DEFAULT, Role.SHARING_PORTAL));
    }

    private void handleRewriteMetadata(RoutingContext rc) {
        try {
            storeWriter.rewriteMeta();
            rc.response().end("OK");
        } catch (Exception e) {
            LOGGER.error("Could not rewrite metadata", e);
            rc.fail(500, e);
        }
    }

    private void handleClientMetadata(RoutingContext rc) {
        try {
            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(clientKeyProvider.getMetadata().encode());
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleClientList(RoutingContext rc) {
        try {
            JsonArray ja = new JsonArray();
            Collection<LegacyClientKey> collection = this.clientKeyProvider.getAll();
            for (LegacyClientKey c : collection) {
                ja.add(createClientKeyJsonObject(c));
            }

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(ja.encode());
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleClientListBySite(RoutingContext rc) {
        try {
            final Site site = RequestUtil.getSiteFromUrl(rc, "siteId", this.siteProvider);
            if (site == null) {
                return;
            }

            JsonArray ja = new JsonArray();
            List<LegacyClientKey> collection = this.clientKeyProvider.getAll().stream().filter(legacyClientKey -> legacyClientKey.getSiteId() == site.getId()).collect(Collectors.toList());
            for (LegacyClientKey c : collection) {
                ja.add(createClientKeyJsonObject(c));
            }

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(ja.encode());
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleClientByKeyId(RoutingContext rc) {
        try {
            String keyId = rc.queryParam("keyId").stream().findFirst().orElse(null);
            if (keyId == null) {
                ResponseUtil.error(rc, 400, "unable to parse keyId ");
                return;
            }

            LegacyClientKey clientKey = getClientKeyByKeyId(keyId);

            if (clientKey == null) {
                ResponseUtil.error(rc, 404, "unable to find key");
                return;
            }

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(createClientKeyJsonObject(clientKey).encode());
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private LegacyClientKey getClientKeyByKeyId(String keyId) {
        return this.clientKeyProvider.getAll().stream().filter(legacyClientKey -> legacyClientKey.getKeyId().equals(keyId)).findFirst().orElse(null);
    }

    private void handleClientByContact(RoutingContext rc) {
        try {
            String contact = rc.queryParam("contact").stream().findFirst().orElse(null);
            if (contact == null) {
                ResponseUtil.error(rc, 400, "unable to parse contact ");
                return;
            }

            LegacyClientKey clientKey = getClientKeyByContact(contact);

            if (clientKey == null) {
                ResponseUtil.error(rc, 404, "unable to find key");
                return;
            }

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(createClientKeyJsonObject(clientKey).encode());
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private LegacyClientKey getClientKeyByContact(String contact) {
        return this.clientKeyProvider.getAll().stream().filter(legacyClientKey -> legacyClientKey.getContact().equals(contact)).findFirst().orElse(null);
    }

    private static JsonObject createClientKeyJsonObject(LegacyClientKey clientKey) {
        JsonObject returnObject = new JsonObject();

        returnObject.put("key_id", clientKey.getKeyId());
        returnObject.put("name", clientKey.getName());
        returnObject.put("contact", clientKey.getContact());
        returnObject.put("roles", RequestUtil.getRolesSpec(clientKey.getRoles()));
        returnObject.put("created", clientKey.getCreated());
        returnObject.put("site_id", clientKey.getSiteId());
        returnObject.put("disabled", clientKey.isDisabled());
        returnObject.put("service_id", clientKey.getServiceId());
        return returnObject;
    }

    private void handleClientReveal(RoutingContext rc) {
        try {
            final String contact = rc.queryParam("contact").get(0);
            Optional<LegacyClientKey> existingClient = this.clientKeyProvider.getAll()
                    .stream().filter(c -> c.getContact().equals(contact))
                    .findFirst();
            if (existingClient.isEmpty()) {
                ResponseUtil.error(rc, 404, "client not found");
                return;
            }

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(JSON_WRITER.writeValueAsString(existingClient.get().toClientKey()));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private String[] generateKeyAndKeyId(Site site) throws Exception {
        String keyCommonPrefix = this.clientKeyPrefix != null ? (this.clientKeyPrefix + site.getId() + "-") : "";
        String key = keyCommonPrefix + keyGenerator.generateFormattedKeyString(32);
        String keyId = key.substring(0, keyCommonPrefix.length() + 5);

        // Check if keyId is duplicated
        Optional<LegacyClientKey> existingClientKeyId = this.clientKeyProvider.getAll()
                .stream().filter(c -> c.getKeyId().equals(keyId))
                .findFirst();
        if (existingClientKeyId.isPresent()) {
            return generateKeyAndKeyId(site);
        }
        return new String[]{key, keyId};
    }

    private void handleClientAdd(RoutingContext rc) {
        try {
            // refresh manually
            clientKeyProvider.loadContent(clientKeyProvider.getMetadata());

            final String name = rc.queryParam("name").get(0);
            if (name.isEmpty()) {
                ResponseUtil.error(rc, 400, "name cannot be blank");
                return;
            }

            Set<Role> roles = RequestUtil.getRoles(rc.queryParam("roles").get(0));
            if (roles == null) {
                ResponseUtil.error(rc, 400, "incorrect or none roles specified");
                return;
            }

            final Site site = RequestUtil.getSiteFromParam(rc, "site_id", this.siteProvider);
            if (site == null) return;

            final int serviceId = this.getServiceId(rc);

            List<LegacyClientKey> clients = getAllClientKeys();

            // create random key and secret
            String[] generatedKeyAndKeyId = generateKeyAndKeyId(site);
            String key = generatedKeyAndKeyId[0];
            String keyId = generatedKeyAndKeyId[1];

            KeyHashResult khr = keyHasher.hashKey(key);
            String secret = keyGenerator.generateRandomKeyString(32);

            // add new client to array
            Instant created = Instant.now();
            LegacyClientKey newClient = new LegacyClientKey(
                    key,
                    khr.getHash(),
                    khr.getSalt(),
                    secret,
                    name,
                    site.getName() + '_' + keyId,
                    created.getEpochSecond(),
                    roles,
                    site.getId(),
                    false,
                    serviceId,
                    keyId
            );
            if (!newClient.hasValidSiteId()) {
                ResponseUtil.error(rc, 400, "invalid site id");
                return;
            }

            // add client to the array
            clients.add(newClient);

            // upload to storage
            storeWriter.upload(clients, null);

            this.keysetManager.createKeysetForClient(newClient.toClientKey());

            // respond with new client created
            rc.response().end(JSON_WRITER.writeValueAsString(new RevealedKey<>(newClient.toClientKey(), key)));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleClientDel(RoutingContext rc) {
        try {
            // refresh manually
            clientKeyProvider.loadContent(clientKeyProvider.getMetadata());

            final String contact = rc.queryParam("contact").get(0);
            Optional<LegacyClientKey> existingClient = this.clientKeyProvider.getAll()
                    .stream().filter(c -> c.getContact().equals(contact))
                    .findFirst();
            if (existingClient.isEmpty()) {
                ResponseUtil.error(rc, 404, "client key not found");
                return;
            }

            List<LegacyClientKey> clients = this.clientKeyProvider.getAll()
                    .stream().sorted((a, b) -> (int) (a.getCreated() - b.getCreated()))
                    .collect(Collectors.toList());

            // delete client from the array
            LegacyClientKey c = existingClient.get();
            clients.remove(c);

            // upload to storage
            storeWriter.upload(clients, null);

            // respond with client deleted
            rc.response().end(JSON_WRITER.writeValueAsString(c.toClientKey()));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleClientUpdate(RoutingContext rc) {
        try {
            // refresh manually
            clientKeyProvider.loadContent(clientKeyProvider.getMetadata());

            final String contact = rc.queryParam("contact").get(0);
            final LegacyClientKey existingClient = getClientKeyByContact(contact);
            if (existingClient == null) {
                ResponseUtil.error(rc, 404, "client not found");
                return;
            }

            final Site site = RequestUtil.getSiteFromParam(rc, "site_id", this.siteProvider);
            if (site == null) return;

            final int serviceId = this.getServiceId(rc);

            existingClient
                    .withSiteId(site.getId())
                    .withServiceId(serviceId);

            List<LegacyClientKey> clients = getAllClientKeys();

            // upload to storage
            storeWriter.upload(clients, null);

            this.keysetManager.createKeysetForClient(existingClient.toClientKey());

            // return the updated client
            rc.response().end(JSON_WRITER.writeValueAsString(existingClient.toClientKey()));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleClientDisable(RoutingContext rc) {
        handleClientDisable(rc, true);
    }

    private void handleClientEnable(RoutingContext rc) {
        handleClientDisable(rc, false);
    }

    private void handleClientDisable(RoutingContext rc, boolean disableFlag) {
        try {
            // refresh manually
            clientKeyProvider.loadContent(clientKeyProvider.getMetadata());

            final String contact = java.net.URLDecoder.decode(rc.queryParam("contact").get(0), "UTF-8");
            Optional<LegacyClientKey> existingClient = this.clientKeyProvider.getAll()
                    .stream().filter(c -> c.getContact().equals(contact))
                    .findFirst();
            if (existingClient.isEmpty()) {
                ResponseUtil.error(rc, 404, "client key not found");
                return;
            }

            List<LegacyClientKey> clients = getAllClientKeys();

            LegacyClientKey c = existingClient.get();
            if (c.isDisabled() == disableFlag) {
                ResponseUtil.error(rc, 400, "no change needed");
                return;
            }

            c.setDisabled(disableFlag);

            JsonObject response = new JsonObject();
            response.put("name", c.getName());
            response.put("contact", c.getContact());
            response.put("created", c.getCreated());
            response.put("disabled", c.isDisabled());

            // upload to storage
            storeWriter.upload(clients, null);

            // respond with client disabled/enabled
            rc.response().end(response.encode());
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleClientRoles(RoutingContext rc) {
        try {
            // refresh manually
            clientKeyProvider.loadContent(clientKeyProvider.getMetadata());

            final String contact = rc.queryParam("contact").get(0);
            Optional<LegacyClientKey> existingClient = this.clientKeyProvider.getAll()
                    .stream().filter(c -> c.getContact().equals(contact))
                    .findFirst();
            if (existingClient.isEmpty()) {
                ResponseUtil.error(rc, 404, "client not found");
                return;
            }

            Set<Role> roles = RequestUtil.getRoles(rc.queryParam("roles").get(0));
            if (roles == null) {
                ResponseUtil.error(rc, 400, "incorrect or none roles specified");
                return;
            }

            List<LegacyClientKey> clients = getAllClientKeys();

            LegacyClientKey c = existingClient.get();
            c.withRoles(roles);

            // upload to storage
            storeWriter.upload(clients, null);

            this.keysetManager.createKeysetForClient(c.toClientKey());

            // return client with new key
            rc.response().end(JSON_WRITER.writeValueAsString(c.toClientKey()));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleClientContact(RoutingContext rc) {
        try {
            // refresh manually
            clientKeyProvider.loadContent(clientKeyProvider.getMetadata());

            final String oldContact = rc.queryParam("oldContact").get(0);
            Optional<LegacyClientKey> existingClient = this.clientKeyProvider.getAll()
                    .stream().filter(c -> c.getContact().equals(oldContact))
                    .findFirst();
            if (existingClient.isEmpty()) {
                ResponseUtil.error(rc, 404, "client not found");
                return;
            }

            final String newContact = rc.queryParam("newContact").get(0);
            if (newContact.isEmpty()) {
                ResponseUtil.error(rc, 400, "new contact cannot be blank");
                return;
            }

            Optional<LegacyClientKey> existingClientWithNewContact = this.clientKeyProvider.getAll()
                    .stream().filter(c -> c.getContact().equals(newContact))
                    .findFirst();
            if (existingClientWithNewContact.isPresent()) {
                ResponseUtil.error(rc, 400, "Client with contact already exists. Contact must be unique.");
                return;
            }

            List<LegacyClientKey> clients = getAllClientKeys();

            LegacyClientKey existingClientObject = existingClient.get();
            existingClientObject.withContact(newContact);

            // upload to storage
            storeWriter.upload(clients, null);

            this.keysetManager.createKeysetForClient(existingClientObject.toClientKey());

            // return client with new key
            rc.response().end(JSON_WRITER.writeValueAsString(existingClientObject.toClientKey()));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleClientRename(RoutingContext rc) {
        try {
            // refresh manually
            clientKeyProvider.loadContent(clientKeyProvider.getMetadata());

            final String contact = rc.queryParam("contact").get(0);
            final String newName = rc.queryParam("newName").get(0);
            if (newName.isEmpty()) {
                ResponseUtil.error(rc, 400, "new name cannot be blank");
                return;
            }

            final LegacyClientKey existingClient = getClientKeyByContact(contact);
            if (existingClient == null) {
                ResponseUtil.error(rc, 404, "client not found");
                return;
            }

            existingClient.withName(newName);

            List<LegacyClientKey> clients = getAllClientKeys();

            // upload to storage
            storeWriter.upload(clients, null);

            // return the updated client
            rc.response().end(JSON_WRITER.writeValueAsString(existingClient.toClientKey()));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private List<LegacyClientKey> getAllClientKeys() {
        return this.clientKeyProvider.getAll()
                .stream().sorted((a, b) -> (int) (a.getCreated() - b.getCreated()))
                .collect(Collectors.toList());
    }

    private int getServiceId(RoutingContext rc) {
        int serviceId = 0;
        try {
            if (rc.queryParam("service_id") == null || rc.queryParam("service_id").size() == 0 || rc.queryParam("service_id").get(0) == null) {
                return serviceId;
            }
            serviceId = Integer.parseInt(rc.queryParam("service_id").get(0));
        } catch (NumberFormatException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return serviceId;
    }
}
