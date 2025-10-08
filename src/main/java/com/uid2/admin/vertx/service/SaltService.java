package com.uid2.admin.vertx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uid2.admin.auth.AdminAuthMiddleware;
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
import com.uid2.shared.store.salt.RotatingSaltProvider;
import com.uid2.shared.util.Mapper;
import com.uid2.shared.util.URLConnectionHttpClient;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

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

    // Simulation vars
    private static final ObjectMapper OBJECT_MAPPER = Mapper.getInstance();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final URLConnectionHttpClient HTTP_CLIENT = new URLConnectionHttpClient(null);

    private static final String CLIENT_API_KEY = "UID2-C-L-999-fCXrMM.fsR3mDqAXELtWWMS+xG1s7RdgRTMqdOH2qaAo=";
    private static final String CLIENT_API_SECRET = "DzBzbjTJcYL0swDtFs2krRNu+g1Eokm2tBU4dEuD0Wk=";

    private static final String OPERATOR_URL = "http://publicoperator:8080";
    private static final Map<String, String> OPERATOR_HEADERS = Map.of("Authorization", String.format("Bearer %s", CLIENT_API_KEY));

    private static final int IDENTITY_COUNT = 10_000;
    private static final int TIMESTAMP_LENGTH = 8;
    private static final int IV_LENGTH = 12;

    public SaltService(AdminAuthMiddleware auth,
                       WriteLock writeLock,
                       SaltStoreWriter storageManager,
                       RotatingSaltProvider saltProvider,
                       SaltRotation saltRotation) {
        this.auth = auth;
        this.writeLock = writeLock;
        this.storageManager = storageManager;
        this.saltProvider = saltProvider;
        this.saltRotation = saltRotation;
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

        router.post("/api/salt/fastForward").blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleSaltFastForward(ctx);
            }
        }, new AuditParams(List.of("fraction"), Collections.emptyList()), Role.MAINTAINER, Role.SECRET_ROTATION));

        router.post("/api/salt/benchmark").blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleSaltBenchmark(ctx);
            }
        }, new AuditParams(List.of(), Collections.emptyList()), Role.MAINTAINER, Role.SECRET_ROTATION));

        router.post("/api/salt/compare").blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleSaltCompare(ctx);
            }
        }, new AuditParams(List.of(), Collections.emptyList()), Role.MAINTAINER, Role.SECRET_ROTATION));

        router.post("/api/salt/simulateToken").blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleSaltSimulateToken(ctx);
            }
        }, new AuditParams(List.of("fraction", "target_date"), Collections.emptyList()), Role.MAINTAINER, Role.SECRET_ROTATION));

        router.post("/api/salt/simulate").blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleSaltSimulate(ctx);
            }
        }, new AuditParams(List.of("fraction", "target_date"), Collections.emptyList()), Role.MAINTAINER, Role.SECRET_ROTATION));
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

            LOGGER.info("Salt rotation age thresholds in seconds: {}", Arrays.stream(SALT_ROTATION_AGE_THRESHOLDS).map(Duration::toSeconds).toList());

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

    private void handleSaltFastForward(RoutingContext rc) {
        try {
            final double fraction = RequestUtil.getDouble(rc, "fraction").orElse(0.002740);
            final int iterations = RequestUtil.getDouble(rc, "fast_forward_iterations").orElse(0.0).intValue();
            final boolean enableV4 = RequestUtil.getBoolean(rc, "enable_v4", false).orElse(false);

            TargetDate targetDate =
                    RequestUtil.getDate(rc, "target_date", DateTimeFormatter.ISO_LOCAL_DATE)
                            .map(TargetDate::new)
                            .orElse(TargetDate.now().plusDays(1));

            saltProvider.loadContent();
            storageManager.archiveSaltLocations();

            var snapshot = saltProvider.getSnapshots().getLast();

            saltRotation.setEnableV4RawUid(enableV4);
            var result = saltRotation.rotateSaltsFastForward(snapshot, SALT_ROTATION_AGE_THRESHOLDS, fraction, targetDate, iterations);
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

    private void handleSaltBenchmark(RoutingContext rc) {
        try {
            final String candidateOperatorUrl = RequestUtil.getString(rc, "candidate_operator_url").orElse("");
            final String candidateApiKey = RequestUtil.getString(rc, "candidate_api_key").orElse("");
            final String candidateApiSecret = RequestUtil.getString(rc, "candidate_api_secret").orElse("");

            List<List<String>> emails = new ArrayList<>();

            for (int i = 0; i < 100; i++) {
                emails.add(new ArrayList<>());

                for (int j = 0; j < IDENTITY_COUNT; j++) {
                    String email = randomEmail();
                    emails.get(i).add(email);
                }
            }

            long before = System.currentTimeMillis();
            for (int i = 0; i < emails.size(); i++) {
                LOGGER.info("Identity Map V3: {}/{}", i + 1, emails.size());
                v3IdentityMap(emails.get(i), candidateOperatorUrl, candidateApiKey, candidateApiSecret);
            }
            long after = System.currentTimeMillis();
            long duration = after - before;
            LOGGER.info("UID Benchmark: duration={}ms", duration);

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end();
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            rc.fail(500, e);
        }
    }

    private void handleSaltCompare(RoutingContext rc) {
        try {
            final String referenceOperatorUrl = RequestUtil.getString(rc, "reference_operator_url").orElse("");
            final String referenceApiKey = RequestUtil.getString(rc, "reference_api_key").orElse("");
            final String referenceApiSecret = RequestUtil.getString(rc, "reference_api_secret").orElse("");
            final String candidateOperatorUrl = RequestUtil.getString(rc, "candidate_operator_url").orElse("");
            final String candidateApiKey = RequestUtil.getString(rc, "candidate_api_key").orElse("");
            final String candidateApiSecret = RequestUtil.getString(rc, "candidate_api_secret").orElse("");

            List<String> emails = new ArrayList<>();
            Map<String, SaltEntry> emailToSaltMap = new HashMap<>();
            RotatingSaltProvider.SaltSnapshot snapshot = saltProvider.getSnapshots().getLast();
            for (int i = 0; i < IDENTITY_COUNT; i++) {
                String email = randomEmail();
                emails.add(email);

                SaltEntry salt = getSalt(email, snapshot);
                emailToSaltMap.put(email, salt);
            }

            // Construct identity map args
            JsonNode referenceResponse = v3IdentityMap(emails, referenceOperatorUrl, referenceApiKey, referenceApiSecret);
            JsonNode candidateResponse = v3IdentityMap(emails, candidateOperatorUrl, candidateApiKey, candidateApiSecret);

            JsonNode referenceMappings = referenceResponse.at("/body/email");
            JsonNode candidateMappings = candidateResponse.at("/body/email");

            int candidateV4UidCount = 0;
            int candidateV2UidCount = 0;
            int candidateInvalidV4UidCount = 0;
            int candidateInvalidV2UidCount = 0;
            int candidateNullUidCount = 0;
            int matchCount = 0;
            int mismatchCount = 0;
            for (int i = 0; i < IDENTITY_COUNT; i++) {
                String email = emails.get(i);
                SaltEntry salt = emailToSaltMap.get(email);
                boolean isV4 = salt.currentKeySalt() != null && salt.currentKeySalt().key() != null && salt.currentKeySalt().salt() != null;

                String referenceUid = referenceMappings.get(i).at("/u").asText();
                String candidateUid = candidateMappings.get(i).at("/u").asText();

                byte[] referenceUidBytes = referenceUid == null ? null : Base64.getDecoder().decode(referenceUid);
                byte[] candidateUidBytes = candidateUid == null ? null : Base64.getDecoder().decode(candidateUid);

                // First, check candidate UID is valid
                if (candidateUidBytes == null) {
                    LOGGER.error("CANDIDATE - UID is null");
                    candidateNullUidCount++;
                } else if (isV4) {
                    if (candidateUidBytes.length != 33) {
                        LOGGER.error("CANDIDATE - salt is v4 but UID length is {}", candidateUidBytes.length);
                        candidateInvalidV4UidCount++;
                    } else if (assertV4Uid(candidateUidBytes, email, salt.currentKeySalt(), snapshot)) {
                        candidateV4UidCount++;
                    }
                } else {
                    if (candidateUidBytes.length != 32) {
                        LOGGER.error("CANDIDATE - salt is v2 but UID length is {}", candidateUidBytes.length);
                        candidateInvalidV2UidCount++;
                    } else {
                        candidateV2UidCount++;
                    }
                }

                // Then, check if reference and candidate raw UIDs match
                if (!isV4) {
                    if (!Arrays.equals(referenceUidBytes, candidateUidBytes)) {
                        LOGGER.error("Reference and candidate UIDs do not match for {} - Reference={} | Candidate={}", email, referenceUid, candidateUid);
                        mismatchCount++;
                    } else {
                        matchCount++;
                    }
                }
            }

            LOGGER.info("UID Consistency between Reference and Candidate operators: " +
                            "candidate_v4_uid_count={} candidate_v2_uid_count={} " +
                            "candidate_v4_invalid_uid_count={} candidate_v2_invalid_uid_count={} candidate_null_uid_count={} " +
                            "match_count={} mismatch_count={}",
                    candidateV4UidCount, candidateV2UidCount,
                    candidateInvalidV4UidCount, candidateInvalidV2UidCount, candidateNullUidCount,
                    matchCount, mismatchCount);

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end();
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            rc.fail(500, e);
        }
    }

    private void handleSaltSimulateToken(RoutingContext rc) {
        try {
            final double fraction = RequestUtil.getDouble(rc, "fraction").orElse(0.002740);
            final int iterations = RequestUtil.getDouble(rc, "iterations").orElse(0.0).intValue();
            final boolean enableV4 = RequestUtil.getBoolean(rc, "enable_v4", false).orElse(false);

            TargetDate targetDate =
                    RequestUtil.getDate(rc, "target_date", DateTimeFormatter.ISO_LOCAL_DATE)
                            .map(TargetDate::new)
                            .orElse(TargetDate.now().plusDays(1));

            // Step 1. Run /v2/token/generate for all emails
            List<String> emails = new ArrayList<>();
            Map<String, Boolean> preRotationEmailToSaltMap = new HashMap<>();
            RotatingSaltProvider.SaltSnapshot snapshot = saltProvider.getSnapshots().getLast();
            for (int j = 0; j < IDENTITY_COUNT; j++) {
                String email = randomEmail();
                emails.add(email);

                SaltEntry salt = getSalt(email, snapshot);
                boolean isV4 = salt.currentKeySalt() != null && salt.currentKeySalt().key() != null && salt.currentKeySalt().salt() != null;
                preRotationEmailToSaltMap.put(email, isV4);
            }

            Map<String, String> emailToRefreshTokenMap = new HashMap<>();
            Map<String, String> emailToRefreshResponseKeyMap = new HashMap<>();
            for (int i = 0; i < emails.size(); i++) {
                LOGGER.info("Step 1 - Token Generate {}/{}", i + 1, emails.size());
                String email = emails.get(i);

                JsonNode tokens = v2TokenGenerate(email);
                String refreshToken = tokens.at("/body/refresh_token").asText();
                String refreshResponseKey = tokens.at("/body/refresh_response_key").asText();

                emailToRefreshTokenMap.put(email, refreshToken);
                emailToRefreshResponseKeyMap.put(email, refreshResponseKey);
            }

            // Step 2. Rotate salts
            saltRotation.setEnableV4RawUid(enableV4);
            for (int i = 0; i < iterations; i++) {
                LOGGER.info("Step 2 - Rotate Salts {}/{}", i + 1, iterations);
                snapshot = rotateSalts(rc, fraction, targetDate.plusDays(i), i);
            }

            Map<String, Boolean> postRotationEmailToSaltMap = new HashMap<>();
            for (String email : emails) {
                SaltEntry salt = getSalt(email, snapshot);
                boolean isV4 = salt.currentKeySalt() != null && salt.currentKeySalt().key() != null && salt.currentKeySalt().salt() != null;
                postRotationEmailToSaltMap.put(email, isV4);
            }

            // Step 3. Run /v2/token/refresh for all emails
            v2LoadSalts();
            Map<String, Boolean> emailToRefreshSuccessMap = new HashMap<>();
            for (int i = 0; i < emails.size(); i++) {
                LOGGER.info("Step 3 - Token Refresh {}/{}", i + 1, emails.size());
                String email = emails.get(i);

                try {
                    JsonNode response = v2TokenRefresh(emailToRefreshTokenMap.get(email), emailToRefreshResponseKeyMap.get(email));
                    emailToRefreshSuccessMap.put(email, response != null);
                } catch (Exception e) {
                    LOGGER.error(e.getMessage(), e);
                    emailToRefreshSuccessMap.put(email, false);
                }
            }

            LOGGER.info(
                    "UID token simulation: success_count={} failure_count={} " +
                            "v2_to_v4_count={} v4_to_v4_count={} v4_to_v2_count={} v2_to_v2_count={}",
                    emailToRefreshSuccessMap.values().stream().filter(x -> x).count(),
                    emailToRefreshSuccessMap.values().stream().filter(x -> !x).count(),
                    emails.stream().filter(email -> !preRotationEmailToSaltMap.get(email) && postRotationEmailToSaltMap.get(email)).count(),
                    emails.stream().filter(email -> preRotationEmailToSaltMap.get(email) && postRotationEmailToSaltMap.get(email)).count(),
                    emails.stream().filter(email -> preRotationEmailToSaltMap.get(email) && !postRotationEmailToSaltMap.get(email)).count(),
                    emails.stream().filter(email -> !preRotationEmailToSaltMap.get(email) && !postRotationEmailToSaltMap.get(email)).count());

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end();
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            rc.fail(500, e);
        }
    }

    private void handleSaltSimulate(RoutingContext rc) {
        try {
            final double fraction = RequestUtil.getDouble(rc, "fraction").orElse(0.002740);

            final int preMigrationIterations = RequestUtil.getDouble(rc, "pre_migration_iterations").orElse(0.0).intValue();
            final int migrationV4Iterations = RequestUtil.getDouble(rc, "migration_v4_iterations").orElse(0.0).intValue();
            final int migrationV2Iterations = RequestUtil.getDouble(rc, "migration_v2_iterations").orElse(0.0).intValue();

            TargetDate targetDate =
                    RequestUtil.getDate(rc, "target_date", DateTimeFormatter.ISO_LOCAL_DATE)
                            .map(TargetDate::new)
                            .orElse(TargetDate.now().plusDays(1));

            RotatingSaltProvider.SaltSnapshot firstSnapshot = saltProvider.getSnapshots().getLast();
            List<String> emails = new ArrayList<>();
            Map<String, Map<Long, Map<String, String>>> emailToUidMapping = new HashMap<>();
            for (int j = 0; j < IDENTITY_COUNT; j++) {
                String email = randomEmail();
                emails.add(email);

                SaltEntry salt = getSalt(email, firstSnapshot);
                emailToUidMapping.put(email, Map.of(salt.id(), new HashMap<>()));
            }

            saltRotation.setEnableV4RawUid(false);
            for (int i = 0; i < preMigrationIterations; i++) {
                LOGGER.info("Step 1 - Pre-migration Iteration {}/{}", i + 1, preMigrationIterations);
                simulationIteration(rc, fraction, targetDate, i, false, emails, emailToUidMapping);
                targetDate = targetDate.plusDays(1);
            }

            saltRotation.setEnableV4RawUid(true);
            for (int i = 0; i < migrationV4Iterations; i++) {
                LOGGER.info("Step 2 - Migration V4 Iteration {}/{}", i + 1, migrationV4Iterations);
                simulationIteration(rc, fraction, targetDate, preMigrationIterations + i, true, emails, emailToUidMapping);
                targetDate = targetDate.plusDays(1);
            }

            saltRotation.setEnableV4RawUid(false);
            for (int i = 0; i < migrationV2Iterations; i++) {
                LOGGER.info("Step 3 - Migration V2 Iteration {}/{}", i + 1, migrationV2Iterations);
                simulationIteration(rc, fraction, targetDate, preMigrationIterations + migrationV4Iterations + i, false, emails, emailToUidMapping);
                targetDate = targetDate.plusDays(1);
            }

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end();
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            rc.fail(500, e);
        }
    }

    private void simulationIteration(
            RoutingContext rc, double fraction, TargetDate targetDate, int iteration, boolean enabledV4Uid,
            List<String> emails, Map<String, Map<Long, Map<String, String>>> emailToUidMapping
    ) throws Exception {
        // Rotate salts
        RotatingSaltProvider.SaltSnapshot snapshot = rotateSalts(rc, fraction, targetDate, iteration);

        Map<String, SaltEntry> emailToSaltMap = new LinkedHashMap<>();
        for (String email : emails) {
            emailToSaltMap.put(email, getSalt(email, snapshot));
        }

        // Call /v3/identity/map
        JsonNode operatorResponse = v3IdentityMap(emails);
        JsonNode emailMappings = operatorResponse.at("/body/email");

        Map<Long, Integer> validSaltCount = new HashMap<>();
        Map<Long, Integer> invalidSaltCount = new HashMap<>();
        int skippedUidCount = 0;
        int inconsistentUidCount = 0;
        int validUidV4Count = 0;
        int validUidV2Count = 0;
        int validPrevUidV4Count = 0;
        int validPrevUidV2Count = 0;
        int validNoPrevUidCount = 0;
        int invalidUidCount = 0;
        int invalidPrevUidCount = 0;
        for (int j = 0; j < IDENTITY_COUNT; j++) {
            String email = emails.get(j);
            SaltEntry salt = emailToSaltMap.get(email);

            // Prepare salt state booleans
            boolean missingSalt = salt.currentSalt() == null;
            boolean missingKey = salt.currentKeySalt() == null || salt.currentKeySalt().key() == null || salt.currentKeySalt().salt() == null;
            boolean missingPrevSalt = salt.previousSalt() == null;
            boolean missingPrevKey = salt.previousKeySalt() == null || salt.previousKeySalt().key() == null || salt.previousKeySalt().salt() == null;

            // Prepare current UID booleans
            JsonNode mapping = emailMappings.get(j);
            String uid = mapping.at("/u").asText(null);
            byte[] uidBytes = uid == null ? null : Base64.getDecoder().decode(uid);
            boolean isV4Uid = uidBytes != null && uidBytes.length == 33;
            boolean isV2Uid = uidBytes != null && uidBytes.length == 32;

            Map<String, String> seenSalts = emailToUidMapping.get(email).get(salt.id());
            String usedSalt = missingSalt ? (missingKey ? null : salt.currentKeySalt().key()) : salt.currentSalt();
            boolean rotated = iteration > 0 && !seenSalts.containsKey(usedSalt);

            // Assert salt state + additional assertions on freshly rotated salts with enabledV4Uid flag
            if (!assertSaltState(missingSalt, missingKey, missingPrevSalt, missingPrevKey, rotated, enabledV4Uid)) {
                invalidSaltCount.put(salt.id(), invalidSaltCount.getOrDefault(salt, 0) + 1);
                skippedUidCount++;
                continue;
            }
            validSaltCount.put(salt.id(), validSaltCount.getOrDefault(salt, 0) + 1);

            // Assert UID consistency
            if (!assertUidConsistency(uid, email, salt.id(), usedSalt, emailToUidMapping)) {
                inconsistentUidCount++;
            }

            // Assert that current UID is valid based on salt state
            boolean validCurrentUid = assertCurrentUid(uidBytes, isV4Uid, isV2Uid, missingKey, missingSalt, email, salt, snapshot);
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

            boolean validPrevUid = assertPrevUid(
                    prevUidBytes, isPrevV4Uid, isPrevV2Uid, missingPrevKey, missingPrevSalt,
                    email, salt, snapshot);
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

    private RotatingSaltProvider.SaltSnapshot rotateSalts(RoutingContext rc, double fraction, TargetDate targetDate, int iterations) throws Exception {
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
        RotatingSaltProvider.SaltSnapshot snapshot = result.getSnapshot();
        storageManager.upload(snapshot, iterations);
        return snapshot;
    }

    private boolean assertSaltState(
            boolean missingSalt, boolean missingKey, boolean missingPrevSalt, boolean missingPrevKey,
            boolean rotated, boolean enabledV4Uid) {
        if (missingSalt && missingKey) {
            LOGGER.error("Invalid salt state - salt and key are both missing");
            return false;
        } else if (!missingSalt && !missingKey) {
            LOGGER.error("Invalid salt state - salt and key are both present");
            return false;
        } else if (!missingPrevSalt && !missingPrevKey) {
            LOGGER.error("Invalid salt state - previous salt and previous key are both present");
            return false;
        } else if (rotated && enabledV4Uid && missingKey) {
            LOGGER.error("Invalid salt state - V4 UID enabled but missing key on rotated salt");
            return false;
        } else if (rotated && !enabledV4Uid && missingSalt) {
            LOGGER.error("Invalid salt state - V4 UID disabled but missing salt on rotated salt");
            return false;
        }

        return true;
    }

    private boolean assertUidConsistency(
            String uid, String email, long saltId, String salt,
            Map<String, Map<Long, Map<String, String>>> emailToUidMapping
    ) {
        String seenUid = emailToUidMapping.get(email).get(saltId).get(salt);
        if (seenUid != null) {
            if (!seenUid.equals(uid)) {
                LOGGER.error("Invalid UID state - Inconsistent UID");
                return false;
            }
        } else {
            emailToUidMapping.get(email).get(saltId).put(salt, uid);
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
            String email, SaltEntry salt, RotatingSaltProvider.SaltSnapshot snapshot
    ) {
        if (prevUidBytes != null) {
            if (isPrevV4Uid) {
                if (assertV4Uid(prevUidBytes, email, salt.previousKeySalt(), snapshot)) {
                    if (!missingPrevKey) {
                        return true;
                    } else {
                        LOGGER.error("Invalid previous UID state - v4 UID generated with null key");
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
            Instant now = Instant.now();
            long age = (now.toEpochMilli() - salt.lastUpdated()) / Duration.ofDays(1).toMillis();

            if (age < 90 && (!missingPrevKey || !missingPrevSalt)) {
                LOGGER.error("Invalid previous UID state - previous salt or previous key found");
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
        int extractedKeyId = ((keyIdBytes[0] & 0xFF) << 16) | ((keyIdBytes[1] & 0xFF) << 8) | (keyIdBytes[2] & 0xFF);
        if (extractedKeyId != key.id()) {
            LOGGER.error("Invalid key ID");
            return false;
        }

        byte[] firstLevelHash = getFirstLevelHash(identityString, snapshot);
        byte[] firstLevelHashLast16Bytes = Arrays.copyOfRange(firstLevelHash, firstLevelHash.length - 16, firstLevelHash.length);
        byte[] iv;
        try {
            iv = generateIV(key.salt(), firstLevelHashLast16Bytes, metadata, key.id());
            byte[] extractedIV = Arrays.copyOfRange(uid, 4, 16);
            if (!Arrays.equals(iv, extractedIV)) {
                LOGGER.error("Invalid IV");
                return false;
            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
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

    private byte[] generateIV(String salt, byte[] firstLevelHashLast16Bytes, byte metadata, int keyId) throws Exception {
        ByteArrayOutputStream ivBase = new ByteArrayOutputStream();
        ivBase.write(salt.getBytes());
        ivBase.write(firstLevelHashLast16Bytes);
        ivBase.write(metadata);
        ivBase.write(getKeyIdBytes(keyId));
        return Arrays.copyOfRange(getSha256Bytes(ivBase.toByteArray(), null), 0, IV_LENGTH);
    }

    private byte[] getKeyIdBytes(int keyId) {
        return new byte[] {
                (byte) ((keyId >> 16) & 0xFF),   // MSB
                (byte) ((keyId >> 8) & 0xFF),    // Middle
                (byte) (keyId & 0xFF),           // LSB
        };
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

    private byte[] getSha256Bytes(byte[] input, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(input);
            if (salt != null) {
                md.update(salt.getBytes());
            }
            return md.digest();
        } catch (Exception e) {
            throw new RuntimeException("Trouble Generating SHA256", e);
        }
    }

    private byte[] getSha256Bytes(String input, String salt) {
        return getSha256Bytes(input.getBytes(), salt);
    }

    private String toBase64String(byte[] b) {
        return Base64.getEncoder().encodeToString(b);
    }

    private void v2LoadSalts() throws Exception {
        HTTP_CLIENT.post(String.format("%s/v2/salts/load", OPERATOR_URL), "", OPERATOR_HEADERS);
    }

    private JsonNode v3IdentityMap(List<String> emails, String baseUrl, String key, String secret) throws Exception {
        StringBuilder reqBody = new StringBuilder("{ \"email\": [");
        for (String email : emails) {
            reqBody.append(String.format("\"%s\"", email));
            if (!emails.getLast().equals(email)) {
                reqBody.append(",");
            }
        }
        reqBody.append("] }");

        V2Envelope envelope = v2CreateEnvelope(reqBody.toString(), secret);
        Map<String, String> headers = Map.of("Authorization", String.format("Bearer %s", key));
        HttpResponse<String> response = HTTP_CLIENT.post(String.format("%s/v3/identity/map", baseUrl), envelope.envelope(), headers);
        return v2DecryptEncryptedResponse(response.body(), envelope.nonce(), secret);
    }

    private JsonNode v3IdentityMap(List<String> emails) throws Exception {
        return v3IdentityMap(emails, OPERATOR_URL, CLIENT_API_KEY, CLIENT_API_SECRET);
    }

    private JsonNode v2TokenGenerate(String email) throws Exception {
        String reqBody = String.format("{ \"email\": \"%s\", \"optout_check\": 1}", email);

        V2Envelope envelope = v2CreateEnvelope(reqBody, CLIENT_API_SECRET);
        HttpResponse<String> response = HTTP_CLIENT.post(String.format("%s/v2/token/generate", OPERATOR_URL), envelope.envelope(), OPERATOR_HEADERS);
        return v2DecryptEncryptedResponse(response.body(), envelope.nonce(), CLIENT_API_SECRET);
    }

    private JsonNode v2TokenRefresh(String refreshToken, String refreshResponseKey) throws Exception {
        HttpResponse<String> response = HTTP_CLIENT.post(String.format("%s/v2/token/refresh", OPERATOR_URL), refreshToken, OPERATOR_HEADERS);
        return v2DecryptRefreshResponse(response.body(), refreshResponseKey);
    }

    private String randomEmail() {
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

    private JsonNode v2DecryptRefreshResponse(String response, String refreshResponseKey) throws Exception {
        if (response.contains("client_error")) {
            return null;
        }

        byte[] encryptedResponseBytes = base64ToByteArray(response);
        byte[] refreshResponseKeyBytes = base64ToByteArray(refreshResponseKey);
        byte[] payload = decryptGCM(encryptedResponseBytes, refreshResponseKeyBytes);
        return OBJECT_MAPPER.readTree(new String(payload, StandardCharsets.UTF_8));
    }

    private byte[] encryptGDM(byte[] b, byte[] secretBytes) throws Exception {
        Class<?> clazz = Class.forName("com.uid2.client.Uid2Encryption");
        Method encryptGDMMethod = clazz.getDeclaredMethod("encryptGCM", byte[].class, byte[].class, byte[].class);
        encryptGDMMethod.setAccessible(true);
        return (byte[]) encryptGDMMethod.invoke(clazz, b, null, secretBytes);
    }

    private byte[] decryptGCM(byte[] b, byte[] secretBytes) throws Exception {
        Class<?> clazz = Class.forName("com.uid2.client.Uid2Encryption");
        Method decryptGCMMethod = clazz.getDeclaredMethod("decryptGCM", byte[].class, int.class, byte[].class);
        decryptGCMMethod.setAccessible(true);
        return (byte[]) decryptGCMMethod.invoke(clazz, b, 0, secretBytes);
    }

    private byte[] base64ToByteArray(String str) {
        return Base64.getDecoder().decode(str);
    }

    private String byteArrayToBase64(byte[] b) {
        return Base64.getEncoder().encodeToString(b);
    }

    private record V2Envelope(String envelope, byte[] nonce) {
    }
}
