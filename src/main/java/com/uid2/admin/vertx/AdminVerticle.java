package com.uid2.admin.vertx;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.uid2.admin.auth.*;
import com.uid2.admin.vertx.service.IService;
import com.uid2.shared.Const;
import com.uid2.shared.Utils;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.ext.web.handler.*;
import io.vertx.ext.web.sstore.LocalSessionStore;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static com.uid2.admin.auth.AuthUtil.isAuthDisabled;

public class AdminVerticle extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminVerticle.class);

    private final JsonObject config;
    private final AuthFactory authFactory;
    private final IAdminUserProvider adminUserProvider;
    private final IService[] services;
    private final ObjectWriter jsonWriter = JsonUtil.createJsonWriter();

    private final List<KeyGenerationResult> validKgrs = new ArrayList<>();
    private final List<KeyGenerationResult> allKgrs = new ArrayList<>();
    private final Set<String> snapshotKeyHashes = new HashSet<>();
    private final Set<String> snapshotKeys = new HashSet<>();

    private final Cache<String, Boolean> comparisonResultCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .recordStats()
            .build();

    public AdminVerticle(JsonObject config,
                         AuthFactory authFactory,
                         IAdminUserProvider adminUserProvider,
                         IService[] services) {
        this.config = config;
        this.authFactory = authFactory;
        this.adminUserProvider = adminUserProvider;
        this.services = services;

        int clientCount = 500;
        for (int i = 0; i < clientCount; i++) {
            validKgrs.add(this.generateFormattedKeyStringAndKeyHash(32));
        }
        snapshotKeyHashes.addAll(validKgrs.stream().map(KeyGenerationResult::getKeyHash).collect(Collectors.toSet()));
        snapshotKeys.addAll(validKgrs.stream().map(KeyGenerationResult::getKey).collect(Collectors.toSet()));

        allKgrs.addAll(validKgrs);
        for (int i = 0; i < (clientCount*10/7)*3; i++) {
            allKgrs.add(this.generateFormattedKeyStringAndKeyHash(32));
        }
    }

    public void start(Promise<Void> startPromise) {
        final Router router = createRoutesSetup();
        final int portOffset = Utils.getPortOffset();
        final int port = Const.Port.ServicePortForAdmin + portOffset;
        vertx.createHttpServer()
                .requestHandler(router)
                .listen(port)
                .onSuccess(server -> {
                    startPromise.complete();
                    LOGGER.info("AdminVerticle instance started on HTTP port: {}", server.actualPort());
                })
                .onFailure(startPromise::fail);
    }

    private Router createRoutesSetup() {
        final Router router = Router.router(vertx);
        router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)));

        final OAuth2Auth oauth2Provider = (OAuth2Auth) authFactory.createAuthProvider(vertx);
        final AuthenticationHandler authHandler = authFactory.createAuthHandler(vertx, router.route("/oauth2-callback"), oauth2Provider);

        router.route().handler(BodyHandler.create());
        router.route().handler(StaticHandler.create("webroot"));

        router.route("/login").handler(authHandler);
        router.route("/adm/*").handler(authHandler);

        router.get("/login").handler(new RedirectToRootHandler(false));
        router.get("/logout").handler(new RedirectToRootHandler(true));
        router.get("/ops/healthcheck").handler(this::handleHealthCheck);
        router.get("/api/token/get").handler(ctx -> handleTokenGet(ctx, oauth2Provider));
        router.get("/protected").handler(this::handleProtected);
        router.get("/test").handler(this::test);

        for (IService service : this.services) {
            service.setupRoutes(router);
        }

        return router;
    }

    private void test(RoutingContext rc) {
        int i = ThreadLocalRandom.current().nextInt(0, allKgrs.size());
        String inputKey = "UID2-C-L-" + i + "-" + allKgrs.get(i).getKey();
        snapshotKeys.contains(inputKey);
//        Boolean cachedResult = comparisonResultCache.getIfPresent(inputKey);
//        if (cachedResult == null) {
//            boolean result = false;
//            for (String snapshotKeyHash : snapshotKeyHashes) {
//                result = this.compareFormattedKeyStringAndKeyHash(inputKey, snapshotKeyHash);
//                if (result) {
//                    break;
//                }
//            }
//            comparisonResultCache.put(inputKey, result);
//        }

        rc.response().end("OK");
    }

    private void test2(RoutingContext rc) {

        rc.response().end("OK");
    }

    private byte[] generateRandomKey(int keyLen) {
        final SecureRandom random = new SecureRandom();
        final byte[] bytes = new byte[keyLen];
        random.nextBytes(bytes);
        return bytes;
    }

    private String generateRandomKeyString(int keyLen) {
        return Utils.toBase64String(generateRandomKey(keyLen));
    }

    private KeyGenerationResult generateFormattedKeyStringAndKeyHash(int keyLen) {
        String key = this.generateRandomKeyString(keyLen);
        String formattedKey = key.length() >= 6 ? new StringBuilder(key).insert(6, ".").toString() : key;

        byte[] keyBytes = Utils.decodeBase64String(key);
        byte[] saltBytes = generateRandomKey(keyLen);

        MessageDigest md = createMessageDigest();
        md.update(saltBytes);
        byte[] hashBytes = md.digest(keyBytes); // This will always generate a byte array of length 512 (64 bytes)

        String hash = Utils.toBase64String(hashBytes); // This will always generate a String with 88 chars (86 + 2 padding)
        String salt = Utils.toBase64String(saltBytes);
        String keyHash = String.format("%s$%s", hash, salt);

        return new KeyGenerationResult(formattedKey, keyHash);
    }

    private boolean compareFormattedKeyStringAndKeyHash(String formattedKey, String keyHash) {
        String inputKey = formattedKey.substring(formattedKey.lastIndexOf("-") + 1).replace(".", "");
        byte[] inputKeyBytes = Utils.decodeBase64String(inputKey);

        byte[] hashBytes = Utils.decodeBase64String(keyHash.split("\\$")[0]);
        byte[] saltBytes = Utils.decodeBase64String(keyHash.split("\\$")[1]);

        MessageDigest md = createMessageDigest();
        md.update(saltBytes);
        byte[] inputHashBytes = md.digest(inputKeyBytes);

        return Arrays.equals(inputHashBytes, hashBytes);
    }

    private MessageDigest createMessageDigest() {
        try {
            return MessageDigest.getInstance("SHA-512");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static class KeyGenerationResult {
        private final String key;
        private final String keyHash;

        public KeyGenerationResult(String key, String keyHash) {
            this.key = key;
            this.keyHash = keyHash;
        }

        public String getKey() {
            return key;
        }

        public String getKeyHash() {
            return keyHash;
        }
    }

    private void handleHealthCheck(RoutingContext rc) {
        rc.response().end("OK");
    }

    private void handleTokenGet(RoutingContext rc, OAuth2Auth oauth2Provider) {
        if (isAuthDisabled(config)) {
            respondWithTestAdminUser(rc);
        } else {
            respondWithRealUser(rc, oauth2Provider);
        }
    }

    private void respondWithRealUser(RoutingContext rc, OAuth2Auth oauth2Provider) {
        if (rc.user() != null) {
            oauth2Provider.userInfo(rc.user())
                    .onFailure(e -> {
                        rc.session().destroy();
                        rc.fail(e);
                    })
                    .onSuccess(userInfo -> {
                        String contact = userInfo.getString("email");
                        if (contact == null) {
                            WebClient.create(rc.vertx())
                                    .getAbs("https://api.github.com/user/emails")
                                    .authentication(new TokenCredentials(rc.user().<String>get("access_token")))
                                    .as(BodyCodec.jsonArray())
                                    .send()
                                    .onFailure(e -> {
                                        rc.session().destroy();
                                        rc.fail(e);
                                    })
                                    .onSuccess(res -> {
                                        JsonArray emails = res.body();
                                        if (emails.size() > 0) {
                                            final String publicEmail = emails.getJsonObject(0).getString("email");
                                            handleEmailContactInfo(rc, publicEmail);
                                        } else {
                                            LOGGER.error("No public emails");
                                            rc.fail(new Throwable("No public emails"));
                                        }
                                    });
                        } else {
                            handleEmailContactInfo(rc, contact);
                        }
                    });
        } else {
            rc.response().setStatusCode(401).end("Not logged in");
        }
    }

    private void respondWithTestAdminUser(RoutingContext rc) {
        // This test user is set up in localstack
        handleEmailContactInfo(rc, "test.user@uidapi.com");
    }

    private void handleEmailContactInfo(RoutingContext rc, String contact) {
        AdminUser adminUser = adminUserProvider.getAdminUserByContact(contact);
        if (adminUser == null) {
            adminUser = AdminUser.unknown(contact);
        }

        try {
            rc.response().end(jsonWriter.writeValueAsString(adminUser));
        } catch (Exception ex) {
            rc.fail(ex);
        }
    }

    private void handleProtected(RoutingContext rc) {
        if (rc.failed()) {
            rc.session().destroy();
            rc.fail(rc.failure());
        } else {
            final String email = rc.user().get("email");
            rc.response().end(email);
        }
    }
}
