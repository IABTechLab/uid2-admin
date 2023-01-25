package com.uid2.admin.vertx.service;

import com.uid2.admin.secret.IEncryptionKeyManager;
import com.uid2.admin.secret.IKeyGenerator;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.writer.EncryptionKeyStoreWriter;
import com.uid2.admin.util.MaxKeyUtil;
import com.uid2.admin.vertx.RequestUtil;
import com.uid2.admin.vertx.ResponseUtil;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.Const;
import com.uid2.shared.auth.Role;
import com.uid2.shared.middleware.AuthMiddleware;
import com.uid2.shared.model.EncryptionKey;
import com.uid2.shared.model.SiteUtil;
import com.uid2.shared.store.reader.RotatingKeyStore;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.*;

public class EncryptionKeyService implements IService, IEncryptionKeyManager {
    private static class RotationResult {
        public Set<Integer> siteIds = null;
        public List<EncryptionKey> rotatedKeys = new ArrayList<>();
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(EncryptionKeyService.class);
    private static final String MASTER_KEY_ACTIVATES_IN_SECONDS = "master_key_activates_in_seconds";
    private static final String MASTER_KEY_EXPIRES_AFTER_SECONDS = "master_key_expires_after_seconds";
    private static final String MASTER_KEY_ROTATION_CUT_OFF_DAYS = "master_key_rotation_cut_off_days";
    private static final String SITE_KEY_ACTIVATES_IN_SECONDS = "site_key_activates_in_seconds";
    private static final String SITE_KEY_EXPIRES_AFTER_SECONDS = "site_key_expires_after_seconds";
    private static final String SITE_KEY_ROTATION_CUT_OFF_DAYS = "site_key_rotation_cut_off_days";
    private static final String REFRESH_KEY_ROTATION_CUT_OFF_DAYS = "refresh_key_rotation_cut_off_days";

    private final AuthMiddleware auth;
    private final Clock clock;
    private final WriteLock writeLock;
    private final EncryptionKeyStoreWriter storeWriter;
    private final RotatingKeyStore keyProvider;
    private final IKeyGenerator keyGenerator;

    private final Duration masterKeyActivatesIn;
    private final Duration masterKeyExpiresAfter;
    private final Duration masterKeyRotationCutoffTime;
    private final Duration siteKeyActivatesIn;
    private final Duration siteKeyExpiresAfter;
    private final Duration siteKeyRotationCutOffTime;
    private final Duration refreshKeyRotationCutOffTime;

    public EncryptionKeyService(JsonObject config,
                                AuthMiddleware auth,
                                WriteLock writeLock,
                                EncryptionKeyStoreWriter storeWriter,
                                RotatingKeyStore keyProvider,
                                IKeyGenerator keyGenerator,
                                Clock clock) {
        this.auth = auth;
        this.writeLock = writeLock;
        this.storeWriter = storeWriter;
        this.keyProvider = keyProvider;
        this.keyGenerator = keyGenerator;
        this.clock = clock;

        masterKeyActivatesIn = Duration.ofSeconds(config.getInteger(MASTER_KEY_ACTIVATES_IN_SECONDS));
        masterKeyExpiresAfter = Duration.ofSeconds(config.getInteger(MASTER_KEY_EXPIRES_AFTER_SECONDS));
        masterKeyRotationCutoffTime = Duration.ofDays(config.getInteger(MASTER_KEY_ROTATION_CUT_OFF_DAYS, 15));
        siteKeyActivatesIn = Duration.ofSeconds(config.getInteger(SITE_KEY_ACTIVATES_IN_SECONDS));
        siteKeyExpiresAfter = Duration.ofSeconds(config.getInteger(SITE_KEY_EXPIRES_AFTER_SECONDS));
        siteKeyRotationCutOffTime = Duration.ofDays(config.getInteger(SITE_KEY_ROTATION_CUT_OFF_DAYS, 110));
        refreshKeyRotationCutOffTime = Duration.ofDays(config.getInteger(REFRESH_KEY_ROTATION_CUT_OFF_DAYS, 40));

        if (masterKeyActivatesIn.compareTo(masterKeyExpiresAfter) >= 0) {
            throw new IllegalStateException(MASTER_KEY_ACTIVATES_IN_SECONDS + " must be greater than " + MASTER_KEY_EXPIRES_AFTER_SECONDS);
        }
        if (siteKeyActivatesIn.compareTo(siteKeyExpiresAfter) >= 0) {
            throw new IllegalStateException(SITE_KEY_ACTIVATES_IN_SECONDS + " must be greater than " + SITE_KEY_EXPIRES_AFTER_SECONDS);
        }
    }

    @Override
    public void setupRoutes(Router router) {
        router.get("/api/key/list").handler(
                auth.handle(this::handleKeyList, Role.SECRET_MANAGER));

        router.post("/api/key/rewrite_metadata").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleRewriteMetadata(ctx);
            }
        }, Role.SECRET_MANAGER));

        router.post("/api/key/rotate_master").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleRotateMasterKey(ctx);
            }
        }, Role.SECRET_MANAGER));

        router.post("/api/key/add").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleAddSiteKey(ctx);
            }
        }, Role.SECRET_MANAGER));

        router.post("/api/key/rotate_site").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleRotateSiteKey(ctx);
            }
        }, Role.SECRET_MANAGER));
        router.post("/api/key/rotate_all_sites").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleRotateAllSiteKeys(ctx);
            }
        }, Role.SECRET_MANAGER));
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

    @Override
    public EncryptionKey addSiteKey(int siteId) throws Exception {
        return addSiteKey(siteId, siteKeyActivatesIn);
    }

    private EncryptionKey addSiteKey(int siteId, Duration activatesIn) throws Exception {
        // force refresh manually
        this.keyProvider.loadContent();

        // Set cutoffTime to be maximum here as we don't want to filter out the keys that have passed the cutoff time
        return addSiteKeys(Arrays.asList(siteId), activatesIn, siteKeyExpiresAfter, Duration.ofNanos(Instant.MAX.getNano())).get(0);
    }

    private void handleKeyList(RoutingContext rc) {
        try {
            final JsonArray ja = new JsonArray();
            this.keyProvider.getSnapshot().getActiveKeySet().stream()
                    .sorted(Comparator.comparingInt(EncryptionKey::getSiteId).thenComparing(EncryptionKey::getActivates))
                    .forEachOrdered(k -> ja.add(toJson(k)));

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(ja.encode());
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleRotateMasterKey(RoutingContext rc) {
        try {
            final RotationResult masterKeyResult = rotateKeys(rc, masterKeyActivatesIn, masterKeyExpiresAfter, masterKeyRotationCutoffTime,
                s -> s == Const.Data.MasterKeySiteId);
            final RotationResult refreshKeyResult = rotateKeys(rc, masterKeyActivatesIn, masterKeyExpiresAfter, refreshKeyRotationCutOffTime,
                    s -> s == Const.Data.RefreshKeySiteId);

            final JsonArray ja = new JsonArray();
            masterKeyResult.rotatedKeys.stream().forEachOrdered(k -> ja.add(toJson(k)));
            refreshKeyResult.rotatedKeys.stream().forEachOrdered(k -> ja.add(toJson(k)));
            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(ja.encode());
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleAddSiteKey(RoutingContext rc) {
        final Optional<Integer> siteIdOpt = RequestUtil.getSiteId(rc, "site_id");
        if (!siteIdOpt.isPresent()) {
            return;
        }

        final int siteId = siteIdOpt.get();

        if (!SiteUtil.isValidSiteId(siteId)) {
            ResponseUtil.error(rc, 400, "must specify a valid site id");
            return;
        }

        final boolean siteKeyExists = this.keyProvider.getSnapshot()
                .getActiveKeySet()
                .stream()
                .anyMatch(key -> key.getSiteId() == siteId);

        if (siteKeyExists) {
            ResponseUtil.error(rc, 400, "Key already exists for specified site id: " + siteId);
            return;
        }

        final Optional<String> activatesQueryParam = rc.queryParam("activates_in_seconds")
                .stream()
                .findFirst();

        Optional<Duration> activatesInSeconds = Optional.empty();

        if (activatesQueryParam.isPresent() && !activatesQueryParam.get().isEmpty()) {
            final long seconds;
            try {
                seconds = Long.parseUnsignedLong(activatesQueryParam.get());
            } catch (NumberFormatException e) {
                ResponseUtil.error(rc, 400, "activates_in_seconds must be a non-negative number of seconds");
                return;
            }

            activatesInSeconds = Optional.of(Duration.ofSeconds(seconds));
        }

        final EncryptionKey siteKey;
        try {
            siteKey = addSiteKey(siteId, activatesInSeconds.orElse(siteKeyActivatesIn));
        } catch (Exception e) {
            rc.fail(500, e);
            return;
        }

        final JsonObject json = toJson(siteKey);
        rc.response()
                .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end(json.encode());
    }

    private void handleRotateSiteKey(RoutingContext rc) {
        try {
            final Optional<Integer> siteIdOpt = RequestUtil.getSiteId(rc, "site_id");
            if (!siteIdOpt.isPresent()) return;
            final int siteId = siteIdOpt.get();

            if (siteId != Const.Data.AdvertisingTokenSiteId && !SiteUtil.isValidSiteId(siteId)) {
                ResponseUtil.error(rc, 400, "must specify a valid site id");
                return;
            }

            final RotationResult result = rotateKeys(rc, siteKeyActivatesIn, siteKeyExpiresAfter, siteKeyRotationCutOffTime, s -> s == siteId);
            if (result == null) {
                return;
            } else if (!result.siteIds.contains(siteId)) {
                ResponseUtil.error(rc, 404, "No keys found for the specified site id: " + siteId);
                return;
            }

            final JsonArray ja = new JsonArray();
            result.rotatedKeys.stream().forEachOrdered(k -> ja.add(toJson(k)));
            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(ja.encode());
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleRotateAllSiteKeys(RoutingContext rc) {
        try {
            final RotationResult result = rotateKeys(rc, siteKeyActivatesIn, siteKeyExpiresAfter, siteKeyRotationCutOffTime, s -> SiteUtil.isValidSiteId(s) || s == Const.Data.AdvertisingTokenSiteId);
            if (result == null) {
                return;
            }

            final JsonArray ja = new JsonArray();
            result.rotatedKeys.stream().forEachOrdered(k -> ja.add(toJson(k)));
            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(ja.encode());
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private RotationResult rotateKeys(RoutingContext rc, Duration activatesIn, Duration expiresAfter, Duration cutOffTime, Predicate<Integer> siteSelector)
            throws Exception {
        final Duration minAge = RequestUtil.getDuration(rc, "min_age_seconds");
        if (minAge == null) return null;
        final Optional<Boolean> force = RequestUtil.getBoolean(rc, "force", false);
        if (!force.isPresent()) return null;

        return rotateKeys(siteSelector, minAge, activatesIn, expiresAfter, cutOffTime, force.get());
    }

    private RotationResult rotateKeys(Predicate<Integer> siteSelector, Duration minAge, Duration activatesIn, Duration expiresAfter, Duration cutoffTime, boolean force)
            throws Exception {
        RotationResult result = new RotationResult();

        // force refresh manually
        this.keyProvider.loadContent();

        final List<EncryptionKey> allKeys = this.keyProvider.getSnapshot().getActiveKeySet();

        // report back which sites were considered
        result.siteIds = allKeys.stream()
                .map(k -> k.getSiteId())
                .filter(s -> siteSelector.test(s))
                .collect(Collectors.toSet());

        final Instant now = clock.now();
        final Instant activatesThreshold = now.minusSeconds(minAge.getSeconds());

        // within the selected sites, find keys with max activation time
        // and then select those which are old enough to be rotated
        // from then on we only care about sites
        List<Integer> siteIds = allKeys.stream()
                .filter(k -> siteSelector.test(k.getSiteId()))
                .collect(groupingBy(EncryptionKey::getSiteId, maxBy(Comparator.comparing(EncryptionKey::getActivates))))
                .values().stream()
                .filter(k -> k.isPresent())
                .map(k -> k.get())
                .filter(k -> force || k.getActivates().isBefore(activatesThreshold))
                .map(k -> k.getSiteId())
                .collect(Collectors.toList());

        if (siteIds.isEmpty()) {
            return result;
        }

        result.rotatedKeys = addSiteKeys(siteIds, activatesIn, expiresAfter, cutoffTime);

        return result;
    }

    private List<EncryptionKey> addSiteKeys(Iterable<Integer> siteIds, Duration activatesIn, Duration expiresAfter, Duration cutoffTime)
            throws Exception {
        final Instant now = clock.now();

        final List<EncryptionKey> keys = this.keyProvider.getSnapshot().getActiveKeySet().stream()
                .sorted(Comparator.comparingInt(EncryptionKey::getId))
                .filter(k -> now.compareTo(k.getExpires().plus(cutoffTime.toDays(), ChronoUnit.DAYS)) < 0)
                .collect(Collectors.toList());

        int maxKeyId = MaxKeyUtil.getMaxKeyId(this.keyProvider.getSnapshot().getActiveKeySet(),
                this.keyProvider.getMetadata().getInteger("max_key_id"));

        final List<EncryptionKey> addedKeys = new ArrayList<>();

        for (Integer siteId : siteIds) {
            ++maxKeyId;
            final byte[] secret = keyGenerator.generateRandomKey(32);
            final Instant created = now;
            final Instant activates = created.plusSeconds(activatesIn.getSeconds());
            final Instant expires = activates.plusSeconds(expiresAfter.getSeconds());
            final EncryptionKey key = new EncryptionKey(maxKeyId, secret, created, activates, expires, siteId);
            keys.add(key);
            addedKeys.add(key);
        }
        storeWriter.upload(keys, maxKeyId);

        return addedKeys;
    }

    private JsonObject toJson(EncryptionKey key) {
        JsonObject jo = new JsonObject();
        jo.put("id", key.getId());
        jo.put("site_id", key.getSiteId());
        jo.put("created", key.getCreated().toEpochMilli());
        jo.put("activates", key.getActivates().toEpochMilli());
        jo.put("expires", key.getExpires().toEpochMilli());
        return jo;
    }
}
