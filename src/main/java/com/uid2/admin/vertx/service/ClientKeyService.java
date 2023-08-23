package com.uid2.admin.vertx.service;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.secret.IEncryptionKeyManager;
import com.uid2.admin.managers.KeysetManager;
import com.uid2.shared.secret.IKeyGenerator;
import com.uid2.admin.store.writer.ClientKeyStoreWriter;
import com.uid2.admin.vertx.JsonUtil;
import com.uid2.admin.vertx.RequestUtil;
import com.uid2.admin.vertx.ResponseUtil;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.auth.ClientKey;
import com.uid2.shared.auth.Role;
import com.uid2.shared.middleware.AuthMiddleware;
import com.uid2.shared.model.Site;
import com.uid2.shared.store.ISiteStore;
import com.uid2.shared.store.reader.RotatingClientKeyProvider;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ClientKeyService implements IService {
    private final AuthMiddleware auth;
    private final WriteLock writeLock;
    private final ClientKeyStoreWriter storeWriter;
    private final RotatingClientKeyProvider clientKeyProvider;
    private final ISiteStore siteProvider;
    private final KeysetManager keysetManager;
    private final IKeyGenerator keyGenerator;
    private final ObjectWriter jsonWriter = JsonUtil.createJsonWriter();
    private final String clientKeyPrefix;
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientKeyService.class);

    public ClientKeyService(JsonObject config,
                            AuthMiddleware auth,
                            WriteLock writeLock,
                            ClientKeyStoreWriter storeWriter,
                            RotatingClientKeyProvider clientKeyProvider,
                            ISiteStore siteProvider,
                            KeysetManager keysetManager,
                            IKeyGenerator keyGenerator) {
        this.auth = auth;
        this.writeLock = writeLock;
        this.storeWriter = storeWriter;
        this.clientKeyProvider = clientKeyProvider;
        this.siteProvider = siteProvider;
        this.keysetManager = keysetManager;
        this.keyGenerator = keyGenerator;

        this.clientKeyPrefix = config.getString("client_key_prefix");
    }

    @Override
    public void setupRoutes(Router router) {
        router.get("/api/client/metadata").handler(
                auth.handle(this::handleClientMetadata, Role.CLIENTKEY_ISSUER));
        router.post("/api/client/rewrite_metadata").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleRewriteMetadata(ctx);
            }
        }, Role.CLIENTKEY_ISSUER));
        router.get("/api/client/list").handler(
                auth.handle(this::handleClientList, Role.CLIENTKEY_ISSUER));
        router.get("/api/client/reveal").handler(
                auth.handle(this::handleClientReveal, Role.CLIENTKEY_ISSUER));

        router.post("/api/client/add").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleClientAdd(ctx);
            }
        }, Role.CLIENTKEY_ISSUER));

        router.post("/api/client/del").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleClientDel(ctx);
            }
        }, Role.ADMINISTRATOR));

        router.post("/api/client/update").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleClientUpdate(ctx);
            }
        }, Role.CLIENTKEY_ISSUER));

        router.post("/api/client/disable").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleClientDisable(ctx);
            }
        }, Role.CLIENTKEY_ISSUER));

        router.post("/api/client/enable").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleClientEnable(ctx);
            }
        }, Role.CLIENTKEY_ISSUER));

        router.post("/api/client/roles").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleClientRoles(ctx);
            }
        }, Role.CLIENTKEY_ISSUER));

        router.post("/api/client/rename").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleClientRename(ctx);
            }
        }, Role.CLIENTKEY_ISSUER));
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
            Collection<ClientKey> collection = this.clientKeyProvider.getAll();
            for (ClientKey c : collection) {
                JsonObject jo = new JsonObject();
                ja.add(jo);

                jo.put("name", c.getName());
                jo.put("contact", c.getContact());
                jo.put("roles", RequestUtil.getRolesSpec(c.getRoles()));
                jo.put("created", c.getCreated());
                jo.put("site_id", c.getSiteId());
                jo.put("disabled", c.isDisabled());
            }

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(ja.encode());
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleClientReveal(RoutingContext rc) {
        try {
            final String name = rc.queryParam("name").get(0);
            Optional<ClientKey> existingClient = this.clientKeyProvider.getAll()
                    .stream().filter(c -> c.getName().equals(name))
                    .findFirst();
            if (!existingClient.isPresent()) {
                ResponseUtil.error(rc, 404, "client not found");
                return;
            }

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(jsonWriter.writeValueAsString(existingClient.get()));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleClientAdd(RoutingContext rc) {
        try {
            // refresh manually
            clientKeyProvider.loadContent(clientKeyProvider.getMetadata());

            final String name = rc.queryParam("name").get(0);
            Optional<ClientKey> existingClient = this.clientKeyProvider.getAll()
                    .stream().filter(c -> c.getName().equals(name))
                    .findFirst();
            if (existingClient.isPresent()) {
                ResponseUtil.error(rc, 400, "key existed");
                return;
            }

            Set<Role> roles = RequestUtil.getRoles(rc.queryParam("roles").get(0));
            if (roles == null) {
                ResponseUtil.error(rc, 400, "incorrect or none roles specified");
                return;
            }

            final Site site = RequestUtil.getSite(rc, "site_id", this.siteProvider);
            if (site == null) return;

            List<ClientKey> clients = this.clientKeyProvider.getAll()
                    .stream().sorted((a, b) -> (int) (a.getCreated() - b.getCreated()))
                    .collect(Collectors.toList());

            // create random key and secret
            String key = keyGenerator.generateFormattedKeyString(32);
            if (this.clientKeyPrefix != null) key = this.clientKeyPrefix + site.getId() + "-" + key;

            String secret = keyGenerator.generateRandomKeyString(32);

            // add new client to array
            Instant created = Instant.now();
            ClientKey newClient = new ClientKey(key, secret, created)
                    .withNameAndContact(name)
                    .withSiteId(site.getId())
                    .withRoles(roles);
            if (!newClient.hasValidSiteId()) {
                ResponseUtil.error(rc, 400, "invalid site id");
                return;
            }

            // add client to the array
            clients.add(newClient);

            // upload to storage
            storeWriter.upload(clients, null);

            this.keysetManager.createKeysetForClient(newClient);

            // respond with new client created
            rc.response().end(jsonWriter.writeValueAsString(newClient));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleClientDel(RoutingContext rc) {
        try {
            // refresh manually
            clientKeyProvider.loadContent(clientKeyProvider.getMetadata());

            final String name = rc.queryParam("name").get(0);
            Optional<ClientKey> existingClient = this.clientKeyProvider.getAll()
                    .stream().filter(c -> c.getName().equals(name))
                    .findFirst();
            if (!existingClient.isPresent()) {
                ResponseUtil.error(rc, 404, "client key not found");
                return;
            }

            List<ClientKey> clients = this.clientKeyProvider.getAll()
                    .stream().sorted((a, b) -> (int) (a.getCreated() - b.getCreated()))
                    .collect(Collectors.toList());

            // delete client from the array
            ClientKey c = existingClient.get();
            clients.remove(c);

            // upload to storage
            storeWriter.upload(clients, null);

            // respond with client deleted
            rc.response().end(jsonWriter.writeValueAsString(c));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleClientUpdate(RoutingContext rc) {
        try {
            // refresh manually
            clientKeyProvider.loadContent(clientKeyProvider.getMetadata());

            final String name = rc.queryParam("name").get(0);
            final ClientKey existingClient = this.clientKeyProvider.getAll()
                    .stream().filter(c -> c.getName().equals(name))
                    .findFirst().orElse(null);
            if (existingClient == null) {
                ResponseUtil.error(rc, 404, "client not found");
                return;
            }

            final Site site = RequestUtil.getSite(rc, "site_id", this.siteProvider);
            if (site == null) return;

            existingClient.withSiteId(site.getId());

            List<ClientKey> clients = this.clientKeyProvider.getAll()
                    .stream().sorted((a, b) -> (int) (a.getCreated() - b.getCreated()))
                    .collect(Collectors.toList());

            // upload to storage
            storeWriter.upload(clients, null);

            this.keysetManager.createKeysetForClient(existingClient);

            // return the updated client
            rc.response().end(jsonWriter.writeValueAsString(existingClient));
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

            final String name = rc.queryParam("name").get(0);
            Optional<ClientKey> existingClient = this.clientKeyProvider.getAll()
                    .stream().filter(c -> c.getName().equals(name))
                    .findFirst();
            if (!existingClient.isPresent()) {
                ResponseUtil.error(rc, 404, "client key not found");
                return;
            }

            List<ClientKey> clients = this.clientKeyProvider.getAll()
                    .stream().sorted((a, b) -> (int) (a.getCreated() - b.getCreated()))
                    .collect(Collectors.toList());

            ClientKey c = existingClient.get();
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

            final String name = rc.queryParam("name").get(0);
            Optional<ClientKey> existingClient = this.clientKeyProvider.getAll()
                    .stream().filter(c -> c.getName().equals(name))
                    .findFirst();
            if (!existingClient.isPresent()) {
                ResponseUtil.error(rc, 404, "client not found");
                return;
            }

            Set<Role> roles = RequestUtil.getRoles(rc.queryParam("roles").get(0));
            if (roles == null) {
                ResponseUtil.error(rc, 400, "incorrect or none roles specified");
                return;
            }

            List<ClientKey> clients = this.clientKeyProvider.getAll()
                    .stream().sorted((a, b) -> (int) (a.getCreated() - b.getCreated()))
                    .collect(Collectors.toList());

            ClientKey c = existingClient.get();
            c.withRoles(roles);

            // upload to storage
            storeWriter.upload(clients, null);

            this.keysetManager.createKeysetForClient(c);

            // return client with new key
            rc.response().end(jsonWriter.writeValueAsString(c));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleClientRename(RoutingContext rc) {
        try {
            // refresh manually
            clientKeyProvider.loadContent(clientKeyProvider.getMetadata());

            final String oldName = rc.queryParam("oldName").get(0);
            final String newName = rc.queryParam("newName").get(0);
            final ClientKey existingClient = this.clientKeyProvider.getAll()
                    .stream().filter(c -> c.getName().equals(oldName))
                    .findFirst().orElse(null);
            if (existingClient == null) {
                ResponseUtil.error(rc, 404, "client not found");
                return;
            }
            final ClientKey existingClientWithNewName = this.clientKeyProvider.getAll()
                    .stream().filter(c -> c.getName().equals(newName))
                    .findFirst().orElse(null);
            if (existingClientWithNewName != null) {
                ResponseUtil.error(rc, 400, "already exist a client with name " + newName);
                return;
            }

            existingClient.withNameAndContact(newName);

            List<ClientKey> clients = this.clientKeyProvider.getAll()
                    .stream().sorted((a, b) -> (int) (a.getCreated() - b.getCreated()))
                    .collect(Collectors.toList());

            // upload to storage
            storeWriter.upload(clients, null);

            // return the updated client
            rc.response().end(jsonWriter.writeValueAsString(existingClient));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }
}
