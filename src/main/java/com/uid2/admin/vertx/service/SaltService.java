package com.uid2.admin.vertx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uid2.admin.auth.AdminAuthMiddleware;
import com.uid2.admin.job.JobDispatcher;
import com.uid2.admin.job.jobsync.EncryptedFilesSyncJob;
import com.uid2.admin.job.jobsync.PrivateSiteDataSyncJob;
import com.uid2.admin.salt.SaltRotation;
import com.uid2.admin.salt.TargetDate;
import com.uid2.admin.store.writer.SaltStoreWriter;
import com.uid2.admin.vertx.RequestUtil;
import com.uid2.admin.vertx.ResponseUtil;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.client.Uid2Helper;
import com.uid2.shared.audit.AuditParams;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.SaltEntry;
import com.uid2.shared.store.reader.RotatingCloudEncryptionKeyProvider;
import com.uid2.shared.store.salt.RotatingSaltProvider;
import com.uid2.shared.util.Mapper;
import com.uid2.shared.util.URLConnectionHttpClient;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.uid2.admin.vertx.Endpoints.*;

public class SaltService implements IService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SaltService.class);
    private static final Duration[] SALT_ROTATION_AGE_THRESHOLDS = new Duration[]{
            Duration.ofDays(30),
            Duration.ofDays(60),
            Duration.ofDays(90),
            Duration.ofDays(120),
            Duration.ofDays(150),
            Duration.ofDays(180),
            Duration.ofDays(210),
            Duration.ofDays(240),
            Duration.ofDays(270),
            Duration.ofDays(300),
            Duration.ofDays(330),
            Duration.ofDays(360),
            Duration.ofDays(390)
    };

    private final AdminAuthMiddleware auth;
    private final WriteLock writeLock;
    private final SaltStoreWriter storageManager;
    private final RotatingSaltProvider saltProvider;
    private final SaltRotation saltRotation;

    private final JsonObject config;
    private final JobDispatcher jobDispatcher;
    private final WriteLock jobDispatcherWriteLock;
    private final RotatingCloudEncryptionKeyProvider rotatingCloudEncryptionKeyProvider;

    // Simulation vars
    private static final ObjectMapper OBJECT_MAPPER = Mapper.getInstance();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final URLConnectionHttpClient HTTP_CLIENT = new URLConnectionHttpClient(null);

    private static final String CLIENT_API_KEY = "UID2-C-L-999-fCXrMM.fsR3mDqAXELtWWMS+xG1s7RdgRTMqdOH2qaAo=";
    private static final String CLIENT_API_SECRET = "DzBzbjTJcYL0swDtFs2krRNu+g1Eokm2tBU4dEuD0Wk=";

    private static final String OPERATOR_URL = "http://proxy:80";
    private static final Map<String, String> OPERATOR_HEADERS = Map.of("Authorization", String.format("Bearer %s", CLIENT_API_KEY));

    private static final int IDENTITY_COUNT = 10_000;
    private static final int TIMESTAMP_LENGTH = 8;

    public SaltService(AdminAuthMiddleware auth,
                       WriteLock writeLock,
                       SaltStoreWriter storageManager,
                       RotatingSaltProvider saltProvider,
                       SaltRotation saltRotation,

                       JsonObject config,
                       JobDispatcher jobDispatcher,
                       RotatingCloudEncryptionKeyProvider rotatingCloudEncryptionKeyProvider) {
        this.auth = auth;
        this.writeLock = writeLock;
        this.storageManager = storageManager;
        this.saltProvider = saltProvider;
        this.saltRotation = saltRotation;

        this.config = config;
        this.jobDispatcher = jobDispatcher;
        this.jobDispatcherWriteLock = new WriteLock();
        this.rotatingCloudEncryptionKeyProvider = rotatingCloudEncryptionKeyProvider;
    }

    public SaltService(AdminAuthMiddleware auth,
                       WriteLock writeLock,
                       SaltStoreWriter storageManager,
                       RotatingSaltProvider saltProvider,
                       SaltRotation saltRotation) {
        this(auth, writeLock, storageManager, saltProvider, saltRotation, null, null, null);
    }

    @Override
    public void setupRoutes(Router router) {
        router.get(API_SALT_SNAPSHOTS.toString()).handler(
                auth.handle(this::handleSaltSnapshots, Role.MAINTAINER));

        router.post(API_SALT_REBUILD.toString()).blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleSaltRebuild(ctx);
            }
        }, new AuditParams(List.of(), Collections.emptyList()), Role.MAINTAINER));

        router.post(API_SALT_ROTATE.toString()).blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleSaltRotate(ctx);
            }
        }, new AuditParams(List.of("fraction", "target_date"), Collections.emptyList()), Role.SUPER_USER, Role.SECRET_ROTATION));

        router.post("/api/salt/simulate").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleSaltSimulate(ctx);
            }
        }, new AuditParams(List.of("fraction", "target_date"), Collections.emptyList()), Role.SUPER_USER, Role.SECRET_ROTATION));
    }

    private void handleSaltSnapshots(RoutingContext rc) {
        try {
            final JsonArray ja = new JsonArray();
            saltProvider.getSnapshots().stream()
                    .forEachOrdered(s -> ja.add(toJson(s)));

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(ja.encode());
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            rc.fail(500, e);
        }
    }

    private void handleSaltRebuild(RoutingContext rc) {
        try {
            Instant now = Instant.now();

            // force refresh
            saltProvider.loadContent();

            // mark all the referenced files as ready to archive
            storageManager.archiveSaltLocations();

            // Unlike in regular salt rotation, this should be based on the currently effective snapshot.
            // The latest snapshot may be in the future, and we may have changes that shouldn't be activated yet.
            var effectiveSnapshot = saltProvider.getSnapshot(now);

            var result = saltRotation.rotateSaltsZero(effectiveSnapshot, TargetDate.now(), now);
            if (!result.hasSnapshot()) {
                ResponseUtil.error(rc, 200, result.getReason());
                return;
            }

            storageManager.upload(result.getSnapshot());

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(toJson(result.getSnapshot()).encode());
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            rc.fail(500, e);
        }
    }

    private void handleSaltRotate(RoutingContext rc) {
        try {
            final Optional<Double> fraction = RequestUtil.getDouble(rc, "fraction");
            if (fraction.isEmpty()) return;

            LOGGER.info("Salt rotation age thresholds in seconds: {}", Arrays.stream(SALT_ROTATION_AGE_THRESHOLDS).map(Duration::toSeconds).collect(Collectors.toList()));

            final TargetDate targetDate =
                    RequestUtil.getDate(rc, "target_date", DateTimeFormatter.ISO_LOCAL_DATE)
                            .map(TargetDate::new)
                            .orElse(TargetDate.now().plusDays(1));

            // Force refresh
            saltProvider.loadContent();

            // Mark all the referenced files as ready to archive
            storageManager.archiveSaltLocations();

            final List<RotatingSaltProvider.SaltSnapshot> snapshots = saltProvider.getSnapshots();
            final RotatingSaltProvider.SaltSnapshot lastSnapshot = snapshots.getLast();

            final SaltRotation.Result result = saltRotation.rotateSalts(lastSnapshot, SALT_ROTATION_AGE_THRESHOLDS, fraction.get(), targetDate);
            if (!result.hasSnapshot()) {
                ResponseUtil.error(rc, 200, result.getReason());
                return;
            }

            storageManager.upload(result.getSnapshot());

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(toJson(result.getSnapshot()).encode());
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            rc.fail(500, e);
        }
    }

    private void handleSaltSimulate(RoutingContext rc) {
        try {
            final Optional<Double> fraction = RequestUtil.getDouble(rc, "fraction");
            if (fraction.isEmpty()) return;
            final Optional<Double> iterations = RequestUtil.getDouble(rc, "iterations");
            if (iterations.isEmpty() || iterations.get() < 1) return;

            TargetDate targetDate =
                    RequestUtil.getDate(rc, "target_date", DateTimeFormatter.ISO_LOCAL_DATE)
                            .map(TargetDate::new)
                            .orElse(TargetDate.now().plusDays(1));

            SaltRotation.Result result = null;
            for (int i = 0; i < iterations.get(); i++) {
                LOGGER.info("Iteration {}/{}", i + 1, iterations.get());

                // force refresh
                this.saltProvider.loadContent();

                // mark all the referenced files as ready to archive
                storageManager.archiveSaltLocations();

                final List<RotatingSaltProvider.SaltSnapshot> snapshots = this.saltProvider.getSnapshots();
                final RotatingSaltProvider.SaltSnapshot lastSnapshot = snapshots.getLast();

                result = saltRotation.rotateSalts(lastSnapshot, SALT_ROTATION_AGE_THRESHOLDS, fraction.get(), targetDate);
                if (!result.hasSnapshot()) {
                    ResponseUtil.error(rc, 200, result.getReason());
                    return;
                }
                storageManager.upload(result.getSnapshot(), i);

                PrivateSiteDataSyncJob privateSiteDataSyncJob = new PrivateSiteDataSyncJob(config, jobDispatcherWriteLock);
                jobDispatcher.enqueue(privateSiteDataSyncJob);
                CompletableFuture<Boolean> privateSiteDataSyncJobFuture = jobDispatcher.executeNextJob();
                privateSiteDataSyncJobFuture.get();

                EncryptedFilesSyncJob encryptedFilesSyncJob = new EncryptedFilesSyncJob(config, jobDispatcherWriteLock, rotatingCloudEncryptionKeyProvider);
                jobDispatcher.enqueue(encryptedFilesSyncJob);
                CompletableFuture<Boolean> encryptedFilesSyncJobFuture = jobDispatcher.executeNextJob();
                encryptedFilesSyncJobFuture.get();

                // Run some assertions against operator
                // Step 1. Generate DII with associated salts
                Map<String, SaltEntry> emailToSaltMap = new LinkedHashMap<>();
                for (int j = 0; j < IDENTITY_COUNT; j++) {
                    String email = randomEmail();
                    SaltEntry salt = getSalt(email, result.getSnapshot());
                    emailToSaltMap.put(email, salt);
                }
                List<String> emails = emailToSaltMap.keySet().stream().toList();

                // Step 2. Call /v3/identity/map with DII
                JsonNode operatorResponse = v3IdentityMap(emails);
                JsonNode emailMappings = operatorResponse.at("/body/email");

                // Step 3. Assert
                for (int j = 0; j < IDENTITY_COUNT; j++) {
                    String email = emails.get(j);
                    SaltEntry salt = emailToSaltMap.get(email);
                    boolean missingPrevSalt = salt.previousSalt() == null || salt.previousSalt().isBlank();
                    boolean missingPrevKey = salt.previousKeySalt() == null
                            || salt.previousKeySalt().key() == null || salt.previousKeySalt().key().isBlank()
                            || salt.previousKeySalt().salt() == null || salt.previousKeySalt().salt().isBlank();

                    JsonNode mapping = emailMappings.get(j);
                    String uid = mapping.at("/u").asText();
                    String prevUid = mapping.at("/p").asText(null);
                    byte[] uidBytes = Base64.getDecoder().decode(uid);
                    byte[] prevUidBytes = prevUid == null ? null : Base64.getDecoder().decode(prevUid);

                    if (uidBytes.length == 32) {
                        // v2 UID
                        int x = 0;
                    } else {
                        // v4 UID
                        int x = 0;
                    }

                    if (prevUidBytes != null) {
                        if (prevUidBytes.length == 32) {
                            // v2 prev UID
                            int x = 0;
                        } else {
                            // v4 prev UID
                            int x = 0;
                        }
                    } else {
                        if (!missingPrevKey && !missingPrevSalt) {
                            LOGGER.error("Invalid previous UID state - both previous salt and previous key found");
                        } else if (!missingPrevKey) {
                            LOGGER.error("Expected: v4 prev UID | Actual: null prev UID");
                        } else if (!missingPrevSalt) {
                            LOGGER.error("Expected: v2 prev UID | Actual: null prev UID");
                        } else {
                            // Valid
                        }

                        int x = 0;
                    }
                }

                targetDate = targetDate.plusDays(1);
            }

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(toJson(result.getSnapshot()).encode());
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            rc.fail(500, e);
        }
    }

    private JsonObject toJson(RotatingSaltProvider.SaltSnapshot snapshot) {
        JsonObject jo = new JsonObject();
        jo.put("effective", snapshot.getEffective().toEpochMilli());
        jo.put("expires", snapshot.getExpires().toEpochMilli());
        jo.put("salts_count", snapshot.getAllRotatingSalts().length);
        jo.put("min_last_updated", Arrays.stream(snapshot.getAllRotatingSalts())
                .map(SaltEntry::lastUpdated)
                .min(Long::compare).orElse(null));
        jo.put("max_last_updated", Arrays.stream(snapshot.getAllRotatingSalts())
                .map(SaltEntry::lastUpdated)
                .max(Long::compare).orElse(null));
        return jo;
    }

    private SaltEntry getSalt(String identityString, RotatingSaltProvider.SaltSnapshot snapshot) {
        String firstLevelSalt = snapshot.getFirstLevelSalt();
        String identityHashString = toBase64String(getSha256Bytes(identityString, null));
        byte[] flh = getSha256Bytes(identityHashString, firstLevelSalt);
        return snapshot.getRotatingSalt(flh);
    }

    private byte[] getSha256Bytes(String input, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(input.getBytes());
            if (salt != null) {
                md.update(salt.getBytes());
            }
            return md.digest();
        } catch (Exception e) {
            throw new RuntimeException("Trouble Generating SHA256", e);
        }
    }

    private String toBase64String(byte[] b) {
        return Base64.getEncoder().encodeToString(b);
    }

    private JsonNode v3IdentityMap(List<String> emails) throws Exception {
        StringBuilder reqBody = new StringBuilder("{ \"email\": [");
        for (String email : emails) {
            reqBody.append(String.format("\"%s\"", email));
            if (!emails.getLast().equals(email)) {
                reqBody.append(",");
            }
        }
        reqBody.append("] }");

        V2Envelope envelope = v2CreateEnvelope(reqBody.toString(), CLIENT_API_SECRET);
        HttpResponse<String> response = HTTP_CLIENT.post(String.format("%s/v3/identity/map", OPERATOR_URL), envelope.envelope(), OPERATOR_HEADERS);
        return v2DecryptEncryptedResponse(response.body(), envelope.nonce(), CLIENT_API_SECRET);
    }

    private static String randomEmail() {
        return "email_" + Math.abs(SECURE_RANDOM.nextLong()) + "@example.com";
    }

    private V2Envelope v2CreateEnvelope(String payload, String secret) throws Exception {
        // Unencrypted envelope payload = timestamp + nonce + raw payload
        Instant timestamp = Instant.now();

        int nonceLength = 8;
        byte[] nonce = new byte[nonceLength];
        SECURE_RANDOM.nextBytes(nonce);

        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);

        ByteBuffer writer = ByteBuffer.allocate(TIMESTAMP_LENGTH + nonce.length + payloadBytes.length);
        writer.putLong(timestamp.toEpochMilli());
        writer.put(nonce);
        writer.put(payloadBytes);

        // Encrypted envelope = 1 + iv + encrypted envelope payload + tag
        byte envelopeVersion = 1;

        byte[] encrypted = encryptGDM(writer.array(), base64ToByteArray(secret)); // iv + encrypted envelope payload + tag

        ByteBuffer envelopeBuffer = ByteBuffer.allocate(1 + encrypted.length);
        envelopeBuffer.put(envelopeVersion);
        envelopeBuffer.put(encrypted);
        return new V2Envelope(byteArrayToBase64(envelopeBuffer.array()), nonce);
    }

    private JsonNode v2DecryptEncryptedResponse(String encryptedResponse, byte[] nonceInRequest, String secret) throws Exception {
        Constructor<Uid2Helper> cons = Uid2Helper.class.getDeclaredConstructor(String.class);
        cons.setAccessible(true);
        Uid2Helper uid2Helper = cons.newInstance(secret);
        String decryptedResponse = uid2Helper.decrypt(encryptedResponse, nonceInRequest);
        return OBJECT_MAPPER.readTree(decryptedResponse);
    }

    private byte[] encryptGDM(byte[] b, byte[] secretBytes) throws Exception {
        Class<?> clazz = Class.forName("com.uid2.client.Uid2Encryption");
        Method encryptGDMMethod = clazz.getDeclaredMethod("encryptGCM", byte[].class, byte[].class, byte[].class);
        encryptGDMMethod.setAccessible(true);
        return (byte[]) encryptGDMMethod.invoke(clazz, b, null, secretBytes);
    }

    private static byte[] base64ToByteArray(String str) {
        return Base64.getDecoder().decode(str);
    }

    private static String byteArrayToBase64(byte[] b) {
        return Base64.getEncoder().encodeToString(b);
    }

    private record V2Envelope(String envelope, byte[] nonce) {
    }
}
