package com.uid2.admin.vertx.service;

import com.uid2.admin.secret.IKeypairGenerator;
import com.uid2.admin.secret.IKeypairManager;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.reader.RotatingSiteStore;
import com.uid2.admin.store.writer.ClientSideKeypairStoreWriter;
import com.uid2.admin.util.InputUtil;
import com.uid2.admin.vertx.ResponseUtil;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.auth.Role;
import com.uid2.shared.middleware.AuthMiddleware;
import com.uid2.shared.model.ClientSideKeypair;
import com.uid2.shared.store.reader.RotatingClientSideKeypairStore;
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

public class ClientSideKeypairService implements IService, IKeypairManager {
    private final AuthMiddleware auth;
    private final Clock clock;

    private final WriteLock writeLock;
    private final ClientSideKeypairStoreWriter storeWriter;
    private final RotatingClientSideKeypairStore keypairStore;
    private final RotatingSiteStore siteProvider;
    private final IKeypairGenerator keypairGenerator;
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientSideKeypairService.class);

    public ClientSideKeypairService(AuthMiddleware auth,
                                    WriteLock writeLock,
                                    ClientSideKeypairStoreWriter storeWriter,
                                    RotatingClientSideKeypairStore keypairStore,
                                    RotatingSiteStore siteProvider,
                                    IKeypairGenerator keypairGenerator,
                                    Clock clock) {
        this.auth = auth;
        this.writeLock = writeLock;
        this.storeWriter = storeWriter;
        this.keypairStore = keypairStore;
        this.keypairGenerator = keypairGenerator;
        this.siteProvider = siteProvider;
        this.clock = clock;
    }

    @Override
    public void setupRoutes(Router router) {
        router.post("/api/client_side_keypairs/add").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleAddKeypair(ctx);
            }
        }, Role.ADMINISTRATOR));
        router.post("/api/client_side_keypairs/update").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleUpdateKeypair(ctx);
            }
        }, Role.ADMINISTRATOR));
        router.get("/api/client_side_keypairs/list").handler(
                auth.handle(this::handleListAllKeypairs, Role.ADMINISTRATOR));
        router.get("/api/client_side_keypairs/:subscriptionId").handler(
                auth.handle(this::handleListKeypair, Role.ADMINISTRATOR)
        );
    }

    private void handleAddKeypair(RoutingContext rc){
        final JsonObject body = rc.body().asJsonObject();
        final Integer siteId = body.getInteger("site_id");
        final String contact = body.getString("contact");
        final boolean disabled = body.getBoolean("disabled", false);
        if(contact == null || siteId == null) {
            ResponseUtil.error(rc, 400, "Required parameters: site_id, contact");
            return;
        }
        if(siteProvider.getSite(siteId) == null) {
            ResponseUtil.error(rc, 404, "site_id: " + siteId + " not valid");
            return;
        }
        String normalizedContactEmail = InputUtil.normalizeEmailString(contact);
        if(normalizedContactEmail == null){
            ResponseUtil.error(rc, 400, "contact email: " + contact + " not valid");
            return;
        }

        final ClientSideKeypair newKeypair;
        try {
            newKeypair = createAndSaveSiteKeypair(siteId, contact, disabled);
        } catch (Exception e) {
            rc.fail(500, e);
            return;
        }
        final JsonObject json = toJsonWithPrivateKey(newKeypair);
        rc.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end(json.encode());
    }

    private void handleUpdateKeypair(RoutingContext rc) {
        final JsonObject body = rc.body().asJsonObject();
        final String subscriptionId = body.getString("subscription_id");
        final String contact = body.getString("contact");
        Boolean disabled = body.getBoolean("disabled");

        if(subscriptionId == null) {
            ResponseUtil.error(rc, 400, "Required parameters: subscription_id");
            return;
        }

        ClientSideKeypair keypair = this.keypairStore.getSnapshot().getKeypair(subscriptionId);
        if(keypair == null) {
            ResponseUtil.error(rc, 404, "Failed to find a keypair for subscription id: " + subscriptionId);
            return;
        }

        if(contact == null && disabled == null) {
            ResponseUtil.error(rc, 400, "Updatable parameters: contact, disabled");
            return;
        }
        String normalizedContactEmail;
        if (contact != null) {
            normalizedContactEmail = InputUtil.normalizeEmailString(contact);
            if(normalizedContactEmail == null){
                ResponseUtil.error(rc, 400, "contact email: " + contact + " not valid");
                return;
            }
        } else {
            normalizedContactEmail = keypair.getContact();
        }
        if(disabled == null) {
            disabled = keypair.isDisabled();
        }

        final ClientSideKeypair newKeypair = new ClientSideKeypair(
                keypair.getSubscriptionId(),
                keypair.encodePublicKeyToString(),
                keypair.encodePrivateKeyToString(),
                keypair.getSiteId(),
                normalizedContactEmail,
                keypair.getCreated(),
                disabled);


        Set<ClientSideKeypair> allKeypairs = new HashSet<>(this.keypairStore.getAll());
        allKeypairs.remove(keypair);
        allKeypairs.add(newKeypair);
        try {
            storeWriter.upload(allKeypairs, null);
        }  catch (Exception e) {
            rc.fail(500, e);
            return;
        }

        final JsonObject json = toJsonWithoutPrivateKey(newKeypair);
        rc.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end(json.encode());
    }

    private void handleListAllKeypairs(RoutingContext rc) {
        try {
            final JsonArray ja = new JsonArray();
            this.keypairStore.getSnapshot().getAll().forEach(k -> ja.add(toJsonWithoutPrivateKey(k)));
            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(ja.encode());
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleListKeypair(RoutingContext rc) {
        String subscriptionId;
        try {
            subscriptionId = rc.pathParam("subscriptionId");
        } catch (Exception e) {
            ResponseUtil.error(rc, 400, "Failed to parse subscription id from request path");
            return;
        }

        ClientSideKeypair keypair = this.keypairStore.getSnapshot().getKeypair(subscriptionId);
        if(keypair == null) {
            ResponseUtil.error(rc, 404, "Failed to find a keypair for subscription id: " + subscriptionId);
            return;
        }

        JsonObject jo = toJsonWithPrivateKey(keypair);
        rc.response()
                .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end(jo.encode());
    }





    @Override
    public ClientSideKeypair createAndSaveSiteKeypair(int siteId, String contact, boolean disabled) throws Exception {

        final Instant now = clock.now();

        this.keypairStore.loadContent();
        final Set<String> existingIds = this.keypairStore.getAll().stream().map(ClientSideKeypair::getSubscriptionId).collect(Collectors.toSet());
        final List<ClientSideKeypair> keypairs = new ArrayList<>(this.keypairStore.getAll());
        KeyPair pair = keypairGenerator.generateRandomKeypair();

        String subscriptionId = keypairGenerator.generateRandomSubscriptionId();
        while (existingIds.contains(subscriptionId)) {
            subscriptionId = keypairGenerator.generateRandomSubscriptionId();
        }

        ClientSideKeypair newKeypair = new ClientSideKeypair(
                subscriptionId,
                keypairGenerator.getPublicKeyPrefix() + Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()),
                keypairGenerator.getPrivateKeyPrefix() + Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()),
                siteId,
                contact,
                now,
                disabled);
        keypairs.add(newKeypair);
        storeWriter.upload(keypairs, null);

        return newKeypair;

    }
}
