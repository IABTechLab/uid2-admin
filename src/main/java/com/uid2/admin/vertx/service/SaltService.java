package com.uid2.admin.vertx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uid2.admin.auth.AdminAuthMiddleware;
import com.uid2.admin.job.JobDispatcher;
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

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
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
    private static final int IV_LENGTH = 12;

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

    private void handleSaltSimulate(RoutingContext rc) {
        try {
            final double fraction = RequestUtil.getDouble(rc, "fraction").orElse(0.002740);

            final int preMigrationIterations = RequestUtil.getDouble(rc, "preMigrationIterations").orElse(0.0).intValue();
            final int migrationV4Iterations = RequestUtil.getDouble(rc, "migrationV4Iterations").orElse(0.0).intValue();
            final int migrationV2V3Iterations = RequestUtil.getDouble(rc, "migrationV2V3Iterations").orElse(0.0).intValue();

            TargetDate targetDate =
                    RequestUtil.getDate(rc, "target_date", DateTimeFormatter.ISO_LOCAL_DATE)
                            .map(TargetDate::new)
                            .orElse(TargetDate.now().plusDays(1));

            SaltRotation.Result result = null;
            List<String> emails = new ArrayList<>();
            Map<String, Map<SaltEntry, String>> emailToUidMapping = new HashMap<>();
            for (int j = 0; j < IDENTITY_COUNT; j++) {
                String email = randomEmail();
                emails.add(email);
                emailToUidMapping.put(email, new HashMap<>());
            }

            saltRotation.setEnableV4RawUid(false);
            for (int i = 0; i < preMigrationIterations; i++) {
                LOGGER.info("Step 1 - Pre-migration Iteration {}/{}", i + 1, preMigrationIterations);
                simulationIteration(rc, fraction, targetDate, i, emails, emailToUidMapping);
                targetDate = targetDate.plusDays(1);
            }

            saltRotation.setEnableV4RawUid(true);
            for (int i = 0; i < migrationV4Iterations; i++) {
                LOGGER.info("Step 2 - Migration V4 Iteration {}/{}", i + 1, migrationV4Iterations);
                simulationIteration(rc, fraction, targetDate, i, emails, emailToUidMapping);
                targetDate = targetDate.plusDays(1);
            }

            saltRotation.setEnableV4RawUid(false);
            for (int i = 0; i < migrationV2V3Iterations; i++) {
                LOGGER.info("Step 3 - Migration V2/V3 Iteration {}/{}", i + 1, migrationV2V3Iterations);
                simulationIteration(rc, fraction, targetDate, i, emails, emailToUidMapping);
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

    private void simulationIteration(
            RoutingContext rc, double fraction, TargetDate targetDate, int iteration,
            List<String> emails, Map<String, Map<SaltEntry, String>> emailToUidMapping
    ) throws Exception {
        // Rotate salts
        SaltRotation.Result result = rotateSalts(rc, fraction, targetDate, iteration);

        Map<String, SaltEntry> emailToSaltMap = new LinkedHashMap<>();
        for (String email : emails) {
            emailToSaltMap.put(email, getSalt(email, result.getSnapshot()));
        }

        // Call /v3/identity/map
        JsonNode operatorResponse = v3IdentityMap(emails);
        JsonNode emailMappings = operatorResponse.at("/body/email");

        Map<SaltEntry, Integer> validSaltCount = new HashMap<>();
        Map<SaltEntry, Integer> invalidSaltCount = new HashMap<>();
        int skippedUidCount = 0;
        int inconsistentUidCount = 0;
        int validUidV2Count = 0;
        int validUidV4Count = 0;
        int validPrevUidV2Count = 0;
        int validPrevUidV4Count = 0;
        int validNoPrevUidCount = 0;
        int invalidUidCount = 0;
        int invalidPrevUidCount = 0;
        for (int j = 0; j < IDENTITY_COUNT; j++) {
            String email = emails.get(j);
            SaltEntry salt = emailToSaltMap.get(email);

            // Assert salt state
            boolean missingSalt = salt.currentSalt() == null;
            boolean missingKey = salt.currentKeySalt() == null || salt.currentKeySalt().key() == null || salt.currentKeySalt().salt() == null;
            boolean missingPrevSalt = salt.previousSalt() == null;
            boolean missingPrevKey = salt.previousKeySalt() == null || salt.previousKeySalt().key() == null || salt.previousKeySalt().salt() == null;
            if (!assertSaltState(missingSalt, missingKey, missingPrevSalt, missingPrevKey)) {
                invalidSaltCount.put(salt, invalidSaltCount.getOrDefault(salt, 0) + 1);
                skippedUidCount++;
                continue;
            }
            validSaltCount.put(salt, validSaltCount.getOrDefault(salt, 0) + 1);

            // Assert current UID
            JsonNode mapping = emailMappings.get(j);
            String uid = mapping.at("/u").asText(null);
            byte[] uidBytes = uid == null ? null : Base64.getDecoder().decode(uid);
            boolean isV4Uid = uidBytes != null && uidBytes.length == 33;
            boolean isV2Uid = uidBytes != null && uidBytes.length == 32;
            if (!assertUidConsistency(uid, email, salt, emailToUidMapping)) {
                inconsistentUidCount++;
            }
            boolean validCurrentUid = assertCurrentUid(uidBytes, isV4Uid, isV2Uid, missingKey, missingSalt, email, salt, result.getSnapshot());
            if (validCurrentUid) {
                if (isV4Uid) {
                    validUidV4Count++;
                } else {
                    validUidV2Count++;
                }
            } else {
                invalidUidCount++;
            }

            // Assert previous UID
            String prevUid = mapping.at("/p").asText(null);
            byte[] prevUidBytes = prevUid == null ? null : Base64.getDecoder().decode(prevUid);
            boolean isPrevV4Uid = prevUidBytes != null && prevUidBytes.length == 33;
            boolean isPrevV2Uid = prevUidBytes != null && prevUidBytes.length == 32;
            boolean validPrevUid = assertPrevUid(prevUidBytes, isPrevV4Uid, isPrevV2Uid, missingPrevKey, missingPrevSalt, isV4Uid, isV2Uid, email, salt, result.getSnapshot());
            if (validPrevUid) {
                if (prevUidBytes != null) {
                    if (isPrevV4Uid) {
                        validPrevUidV4Count++;
                    } else {
                        validPrevUidV2Count++;
                    }
                } else {
                    validNoPrevUidCount++;
                }
            } else {
                invalidPrevUidCount++;
            }
        }

        LOGGER.info("UID simulation - salt assertions: target_date={} " +
                        "valid_salts={} invalid_salts={}",
                targetDate,
                validSaltCount.size(), invalidSaltCount.size());
        LOGGER.info("UID simulation - raw UID assertions: target_date={} " +
                        "valid_v4_count={} valid_v2_count={} invalid_count={} inconsistent_count={} skipped_count={} " +
                        "valid_prev_v4_count={} valid_prev_v2_count={} valid_no_prev_count={} invalid_prev_count={}",
                targetDate,
                validUidV4Count, validUidV2Count, invalidUidCount, inconsistentUidCount, skippedUidCount,
                validPrevUidV4Count, validPrevUidV2Count, validNoPrevUidCount, invalidPrevUidCount);
    }

    private SaltRotation.Result rotateSalts(RoutingContext rc, double fraction, TargetDate targetDate, int iteration) throws Exception {
        // force refresh
        this.saltProvider.loadContent();

        // mark all the referenced files as ready to archive
        storageManager.archiveSaltLocations();

        final List<RotatingSaltProvider.SaltSnapshot> snapshots = this.saltProvider.getSnapshots();
        final RotatingSaltProvider.SaltSnapshot lastSnapshot = snapshots.getLast();

        SaltRotation.Result result = saltRotation.rotateSalts(lastSnapshot, SALT_ROTATION_AGE_THRESHOLDS, fraction, targetDate);
        if (!result.hasSnapshot()) {
            ResponseUtil.error(rc, 200, result.getReason());
            return null;
        }
        storageManager.upload(result.getSnapshot(), iteration);

        PrivateSiteDataSyncJob privateSiteDataSyncJob = new PrivateSiteDataSyncJob(config, jobDispatcherWriteLock);
        jobDispatcher.enqueue(privateSiteDataSyncJob);
        CompletableFuture<Boolean> privateSiteDataSyncJobFuture = jobDispatcher.executeNextJob();
        privateSiteDataSyncJobFuture.get();

        return result;
    }

    private boolean assertSaltState(boolean missingSalt, boolean missingKey, boolean missingPrevSalt, boolean missingPrevKey) {
        if (missingSalt && missingKey) {
            LOGGER.error("Invalid salt state - salt and key are both missing");
            return false;
        } else if (!missingSalt && !missingKey) {
            LOGGER.error("Invalid salt state - salt and key are both present");
            return false;
        } else if (!missingPrevSalt && !missingPrevKey) {
            LOGGER.error("Invalid salt state - previous salt and previous key are both present");
            return false;
        }

        return true;
    }

    private boolean assertUidConsistency(
            String uid, String email, SaltEntry salt,
            Map<String, Map<SaltEntry, String>> emailToUidMapping
    ) {
        String seenUid = emailToUidMapping.get(email).get(salt);
        if (seenUid != null) {
            if (!seenUid.equals(uid)) {
                LOGGER.error("Invalid UID state - Inconsistent UID");
                return false;
            }
        } else {
            emailToUidMapping.get(email).put(salt, uid);
        }
        return true;
    }

    private boolean assertCurrentUid(
            byte[] uidBytes, boolean isV4Uid, boolean isV2Uid, boolean missingKey, boolean missingSalt,
            String email, SaltEntry salt, RotatingSaltProvider.SaltSnapshot snapshot
    ) {
        if (uidBytes != null) {
            if (isV4Uid) {
                if (assertV4Uid(uidBytes, email, salt.currentKeySalt(), snapshot)) {
                    if (!missingKey) {
                        return true;
                    } else {
                        LOGGER.error("Invalid UID state - v4 UID generated with null key");
                        return false;
                    }
                } else {
                    return false;
                }
            } else if (isV2Uid) {
                if (!missingSalt) {
                    return true;
                } else {
                    LOGGER.error("Invalid UID state - v2 UID generated with null salt");
                    return false;
                }
            } else {
                LOGGER.error("Invalid UID state - length is not 33 (v4) or 32 (v2) bytes");
                return false;
            }
        } else {
            LOGGER.error("Invalid UID state - null UID");
            return false;
        }
    }

    private boolean assertPrevUid(
            byte[] prevUidBytes, boolean isPrevV4Uid, boolean isPrevV2Uid, boolean missingPrevKey, boolean missingPrevSalt,
            boolean isV4Uid, boolean isV2Uid,
            String email, SaltEntry salt, RotatingSaltProvider.SaltSnapshot snapshot
    ) {
        if (prevUidBytes != null) {
            if (isPrevV4Uid) {
                if (isV2Uid) {
                    LOGGER.error("Invalid previous UID state - v2 UID with v4 prev UID");
                    return false;
                } else if (isV4Uid) {
                    if (assertV4Uid(prevUidBytes, email, salt.previousKeySalt(), snapshot)) {
                        if (!missingPrevKey) {
                            return true;
                        } else {
                            LOGGER.error("Invalid previous UID state - v4 UID generated with null key");
                            return false;
                        }
                    } else {
                        return false;
                    }
                } else {
                    LOGGER.error("Invalid previous UID state - invalid UID");
                    return false;
                }
            } else if (isPrevV2Uid) {
                if (!missingPrevSalt) {
                    return true;
                } else {
                    LOGGER.error("Invalid previous UID state - v2 UID generated with null salt");
                    return false;
                }
            } else {
                LOGGER.error("Invalid previous UID state - length is not 33 (v4) or 32 (v2) bytes");
                return false;
            }
        } else {
            if (!missingPrevKey && !missingPrevSalt) {
                LOGGER.error("Invalid previous UID state - both previous salt and previous key found");
                return false;
            } else if (!missingPrevKey) { // Add rollback assertions
                LOGGER.error("Expected: v4 prev UID | Actual: null prev UID");
                return false;
            } else if (!missingPrevSalt) { // Add rollback assertions
                LOGGER.error("Expected: v2 prev UID | Actual: null prev UID");
                return false;
            } else {
                return true;
            }
        }
    }

    private boolean assertV4Uid(byte[] uid, String identityString, SaltEntry.KeyMaterial key, RotatingSaltProvider.SaltSnapshot snapshot) {
        if (uid == null) {
            LOGGER.error("UID is null");
            return false;
        }
        if (uid.length != 33) {
            LOGGER.error("UID length is not 33 bytes");
            return false;
        }

        byte metadata = uid[0];
        if (metadata != 0b00100000) {
            LOGGER.error("Invalid metadata");
            return false;
        }

        byte[] keyIdBytes = Arrays.copyOfRange(uid, 1, 4);
        int extractedKeyId = (keyIdBytes[0] & 0xFF) | ((keyIdBytes[1] & 0xFF) << 8) | ((keyIdBytes[2] & 0xFF) << 16);
        if (extractedKeyId != key.id()) {
            LOGGER.error("Invalid key ID");
            return false;
        }

        byte[] firstLevelHash = getFirstLevelHash(identityString, snapshot);
        byte[] firstLevelHashLast16Bytes = Arrays.copyOfRange(firstLevelHash, firstLevelHash.length - 16, firstLevelHash.length);
        byte[] iv = generateIV(key.salt(), firstLevelHashLast16Bytes, metadata, key.id());
        byte[] extractedIV = Arrays.copyOfRange(uid, 4, 16);
        if (!Arrays.equals(iv, extractedIV)) {
            LOGGER.error("Invalid IV");
            return false;
        }

        try {
            byte[] encryptedFirstLevelHash = encryptHash(key.key(), firstLevelHashLast16Bytes, iv);
            byte[] extractedEncryptedHash = Arrays.copyOfRange(uid, 16, 32);
            if (!Arrays.equals(encryptedFirstLevelHash, extractedEncryptedHash)) {
                LOGGER.error("Invalid encrypted hash");
                return false;
            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            return false;
        }

        byte checksum = generateChecksum(Arrays.copyOfRange(uid, 0, 32));
        byte extractedChecksum = uid[32];
        if (checksum != extractedChecksum) {
            LOGGER.error("Invalid checksum");
            return false;
        }

        return true;
    }

    private byte[] generateIV(String salt, byte[] firstLevelHashLast16Bytes, byte metadata, int keyId) {
        String ivBase = salt
                .concat(Arrays.toString(firstLevelHashLast16Bytes))
                .concat(Byte.toString(metadata))
                .concat(String.valueOf(keyId));
        return Arrays.copyOfRange(getSha256Bytes(ivBase, null), 0, IV_LENGTH);
    }

    private byte[] encryptHash(String encryptionKey, byte[] hash, byte[] iv) throws Exception {
        // Set up AES256-CTR cipher
        Cipher aesCtr = Cipher.getInstance("AES/CTR/NoPadding");
        SecretKeySpec secretKey = new SecretKeySpec(encryptionKey.getBytes(), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(padIV16Bytes(iv));

        aesCtr.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
        return aesCtr.doFinal(hash);
    }

    private byte generateChecksum(byte[] data) {
        // Simple XOR checksum of all bytes
        byte checksum = 0;
        for (byte b : data) {
            checksum ^= b;
        }
        return checksum;
    }

    private byte[] padIV16Bytes(byte[] iv) {
        // Pad the 12-byte IV to 16 bytes for AES-CTR (standard block size)
        byte[] paddedIV = new byte[16];
        System.arraycopy(iv, 0, paddedIV, 0, 12);
        // Remaining 4 bytes are already zero-initialized (counter starts at 0)
        return paddedIV;
    }

    private byte[] getFirstLevelHash(String identityString, RotatingSaltProvider.SaltSnapshot snapshot) {
        String firstLevelSalt = snapshot.getFirstLevelSalt();
        String identityHashString = toBase64String(getSha256Bytes(identityString, null));
        return getSha256Bytes(identityHashString, firstLevelSalt);
    }

    private SaltEntry getSalt(String identityString, RotatingSaltProvider.SaltSnapshot snapshot) {
        return snapshot.getRotatingSalt(getFirstLevelHash(identityString, snapshot));
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
