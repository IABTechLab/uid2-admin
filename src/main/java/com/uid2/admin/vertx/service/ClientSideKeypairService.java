package com.uid2.admin.vertx.service;

import com.uid2.admin.auth.AdminAuthMiddleware;
import com.uid2.admin.managers.KeysetManager;
import com.uid2.admin.secret.IKeypairGenerator;
import com.uid2.admin.secret.IKeypairManager;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.writer.ClientSideKeypairStoreWriter;
import com.uid2.admin.vertx.ResponseUtil;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.audit.AuditParams;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.ClientSideKeypair;
import com.uid2.shared.store.reader.RotatingClientSideKeypairStore;
import com.uid2.shared.store.reader.RotatingSiteStore;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyPair;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.uid2.admin.store.writer.ClientSideKeypairStoreWriter.toJsonWithPrivateKey;
import static com.uid2.admin.store.writer.ClientSideKeypairStoreWriter.toJsonWithoutPrivateKey;
import static com.uid2.admin.vertx.Endpoints.*;

public class ClientSideKeypairService implements IService, IKeypairManager {
    private final AdminAuthMiddleware auth;
    private final Clock clock;
    private final WriteLock writeLock;
    private final ClientSideKeypairStoreWriter storeWriter;
    private final RotatingClientSideKeypairStore keypairStore;
    private final RotatingSiteStore siteProvider;
    private final KeysetManager keysetManager;
    private final IKeypairGenerator keypairGenerator;
    private final String publicKeyPrefix;
    private final String privateKeyPrefix;
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientSideKeypairService.class);

    public ClientSideKeypairService(JsonObject config,
                                    AdminAuthMiddleware auth,
                                    WriteLock writeLock,
                                    ClientSideKeypairStoreWriter storeWriter,
                                    RotatingClientSideKeypairStore keypairStore,
                                    RotatingSiteStore siteProvider,
                                    KeysetManager keysetManager,
                                    IKeypairGenerator keypairGenerator,
                                    Clock clock) {
        this.auth = auth;
        this.writeLock = writeLock;
        this.storeWriter = storeWriter;
        this.keypairStore = keypairStore;
        this.keypairGenerator = keypairGenerator;
        this.siteProvider = siteProvider;
        this.keysetManager = keysetManager;
        this.clock = clock;
        this.publicKeyPrefix = config.getString("client_side_keypair_public_prefix");
        this.privateKeyPrefix = config.getString("client_side_keypair_private_prefix");
    }

    @Override
    public void setupRoutes(Router router) {
        router.post(API_CLIENT_SIDE_KEYPAIRS_ADD.toString()).blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleAddKeypair(ctx);
            }
        }, new AuditParams(Collections.emptyList(), List.of("site_id", "name", "contact", "disabled")), Role.MAINTAINER, Role.SHARING_PORTAL));
        router.post(API_CLIENT_SIDE_KEYPAIRS_UPDATE.toString()).blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleUpdateKeypair(ctx);
            }
        }, new AuditParams(Collections.emptyList(), List.of("subscription_id", "name", "contact", "disabled")), Role.MAINTAINER, Role.SHARING_PORTAL));
        router.post(API_CLIENT_SIDE_KEYPAIRS_DELETE.toString()).blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleDeleteKeypair(ctx);
            }
        }, new AuditParams(Collections.emptyList(), List.of("subscription_id")), Role.PRIVILEGED, Role.SHARING_PORTAL));
        router.get(API_CLIENT_SIDE_KEYPAIRS_LIST.toString()).handler(
            auth.handle(this::handleListAllKeypairs, Role.MAINTAINER, Role.METRICS_EXPORT));
        router.get(API_CLIENT_SIDE_KEYPAIRS_SUBSCRIPTIONID.toString()).handler(
            auth.handle(this::handleListKeypair, Role.MAINTAINER)
        );
    }

    private void handleAddKeypair(RoutingContext rc) {
        final JsonObject body = rc.body().asJsonObject();
        final Integer siteId = body.getInteger("site_id");
        final String contact = body.getString("contact", "");
        final boolean disabled = body.getBoolean("disabled", false);
        final String name = body.getString("name");
        if (siteId == null) {
            ResponseUtil.error(rc, 400, "Required parameters: site_id");
            return;
        }
        if (siteProvider.getSite(siteId) == null) {
            ResponseUtil.error(rc, 404, "site_id: " + siteId + " not valid");
            return;
        }

        final ClientSideKeypair newKeypair;
        try {
            newKeypair = createAndSaveSiteKeypair(siteId, contact, disabled, name);
        } catch (Exception e) {
            ResponseUtil.errorInternal(rc, "failed to upload keypairs", e);
            return;
        }

        try {
            this.keysetManager.createKeysetForSite(siteId);
        } catch (Exception e) {
            ResponseUtil.errorInternal(rc, "failed to create keyset", e);
            return;
        }

        final JsonObject json = createKeypairJsonObject(toJsonWithoutPrivateKey(newKeypair));
        rc.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end(json.encode());
    }

    private void handleUpdateKeypair(RoutingContext rc) {
        JsonObject body = getRequestBody(rc);
        if (body == null) return;

        String contact = body.getString("contact");
        Boolean disabled = body.getBoolean("disabled");
        String name = body.getString("name");

        ClientSideKeypair keypair = validateAndGetKeypair(rc, body);
        if (keypair == null) return;


        if (contact == null && disabled == null && name == null) {
            ResponseUtil.error(rc, 400, "Updatable parameters: contact, disabled, name");
            return;
        }

        if (contact == null) {
            contact = keypair.getContact();
        }

        if (disabled == null) {
            disabled = keypair.isDisabled();
        }

        if (name == null) {
            name = keypair.getName();
        }

        final ClientSideKeypair newKeypair = new ClientSideKeypair(
                keypair.getSubscriptionId(),
                keypair.encodePublicKeyToString(),
                keypair.encodePrivateKeyToString(),
                keypair.getSiteId(),
                contact,
                keypair.getCreated(),
                disabled,
                name);


        Set<ClientSideKeypair> allKeypairs = new HashSet<>(this.keypairStore.getAll());
        allKeypairs.remove(keypair);
        allKeypairs.add(newKeypair);
        try {
            storeWriter.upload(allKeypairs, null);
        } catch (Exception e) {
            ResponseUtil.errorInternal(rc, "failed to upload keypairs", e);
            return;
        }

        final JsonObject json = createKeypairJsonObject(toJsonWithoutPrivateKey(newKeypair));
        rc.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end(json.encode());
    }

    private void handleDeleteKeypair(RoutingContext rc) {
        JsonObject body = getRequestBody(rc);
        if (body == null) return;

        ClientSideKeypair keypair = validateAndGetKeypair(rc, body);
        if (keypair == null) return;

        Set<ClientSideKeypair> allKeypairs = new HashSet<>(this.keypairStore.getAll());
        allKeypairs.remove(keypair);

        try {
            storeWriter.upload(allKeypairs, null);
        } catch (Exception e) {
            ResponseUtil.errorInternal(rc, "failed to upload keypairs", e);
            return;
        }

        JsonObject responseJson = new JsonObject()
                .put("success", true)
                .put("deleted_keypair", createKeypairJsonObject(toJsonWithoutPrivateKey(keypair)));
        rc.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end(responseJson.encode());
    }

    public Iterable<ClientSideKeypair> getKeypairsBySite(int siteId) {
        return this.keypairStore.getSnapshot().getSiteKeypairs(siteId);
    }

    private void handleListAllKeypairs(RoutingContext rc) {
        final JsonArray ja = new JsonArray();
        this.keypairStore.getSnapshot().getAll().forEach(k -> ja.add(createKeypairJsonObject(toJsonWithoutPrivateKey(k))));
        rc.response()
                .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end(ja.encode());
    }

    private JsonObject createKeypairJsonObject(JsonObject jo) {
        String siteName = this.siteProvider.getSite(jo.getInteger("site_id")).getName();
        jo.put("site_name", siteName);
        return jo;
    }

    private void handleListKeypair(RoutingContext rc) {

        String subscriptionId = rc.pathParam("subscriptionId");

        ClientSideKeypair keypair = this.keypairStore.getSnapshot().getKeypair(subscriptionId);
        if (keypair == null) {
            ResponseUtil.error(rc, 404, "Failed to find a keypair for subscription id: " + subscriptionId);
            return;
        }

        JsonObject jo = createKeypairJsonObject(toJsonWithPrivateKey(keypair));
        rc.response()
                .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end(jo.encode());
    }

    @Override
    public ClientSideKeypair createAndSaveSiteKeypair(int siteId, String contact, boolean disabled, String name) throws Exception {

        final Instant now = clock.now();

        this.keypairStore.loadContent();
        final Set<String> existingIds = this.keypairStore.getAll().stream().map(ClientSideKeypair::getSubscriptionId).collect(Collectors.toSet());
        final List<ClientSideKeypair> keypairs = new ArrayList<>(this.keypairStore.getAll());
        KeyPair pair = keypairGenerator.generateKeypair();

        String subscriptionId = keypairGenerator.generateRandomSubscriptionId();
        while (existingIds.contains(subscriptionId)) {
            subscriptionId = keypairGenerator.generateRandomSubscriptionId();
        }

        ClientSideKeypair newKeypair = new ClientSideKeypair(
                subscriptionId,
                this.publicKeyPrefix + Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()),
                this.privateKeyPrefix + Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()),
                siteId,
                contact,
                now,
                disabled,
                name);
        keypairs.add(newKeypair);
        storeWriter.upload(keypairs, null);

        return newKeypair;

    }

    private JsonObject getRequestBody(RoutingContext rc) {
        JsonObject body = rc.body() != null ? rc.body().asJsonObject() : null;
        if (body == null) {
            ResponseUtil.error(rc, 400, "json payload required but not provided");
        }
        return body;
    }

    private ClientSideKeypair validateAndGetKeypair(RoutingContext rc, JsonObject body) {
        String subscriptionId = body.getString("subscription_id");
        if (subscriptionId == null) {
            ResponseUtil.error(rc, 400, "Required parameters: subscription_id");
            return null;
        }

        ClientSideKeypair keypair = this.keypairStore.getSnapshot().getKeypair(subscriptionId);
        if (keypair == null) {
            ResponseUtil.error(rc, 404, "Failed to find a keypair for subscription id: " + subscriptionId);
            return null;
        }
        return keypair;
    }
}
