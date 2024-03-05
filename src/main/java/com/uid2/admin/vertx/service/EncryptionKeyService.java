package com.uid2.admin.vertx.service;

import com.uid2.admin.auth.AdminAuthMiddleware;
import com.uid2.admin.auth.AdminKeyset;
import com.uid2.admin.secret.IEncryptionKeyManager;
import com.uid2.admin.secret.IKeysetKeyManager;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.reader.RotatingAdminKeysetStore;
import com.uid2.admin.store.writer.AdminKeysetWriter;
import com.uid2.admin.store.writer.EncryptionKeyStoreWriter;
import com.uid2.admin.store.writer.KeysetKeyStoreWriter;
import com.uid2.admin.util.MaxKeyUtil;
import com.uid2.admin.vertx.RequestUtil;
import com.uid2.admin.vertx.ResponseUtil;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.Const;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.EncryptionKey;
import com.uid2.shared.model.KeysetKey;
import com.uid2.shared.model.SiteUtil;
import com.uid2.shared.secret.IKeyGenerator;
import com.uid2.shared.store.reader.RotatingKeyStore;
import com.uid2.shared.store.reader.RotatingKeysetKeyStore;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.uid2.admin.AdminConst.enableKeysetConfigProp;
import static com.uid2.admin.managers.KeysetManager.*;
import static java.util.stream.Collectors.*;

public class EncryptionKeyService implements IService, IEncryptionKeyManager, IKeysetKeyManager {
    private static class RotationResult<T> {
        public Set<Integer> rotatedIds = null;
        public List<T> rotatedKeys = new ArrayList<>();
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(EncryptionKeyService.class);
    private static final String MASTER_KEY_ACTIVATES_IN_SECONDS = "master_key_activates_in_seconds";
    private static final String MASTER_KEY_EXPIRES_AFTER_SECONDS = "master_key_expires_after_seconds";
    private static final String MASTER_KEY_ROTATION_CUT_OFF_DAYS = "master_key_rotation_cut_off_days";
    private static final String SITE_KEY_ACTIVATES_IN_SECONDS = "site_key_activates_in_seconds";
    private static final String SITE_KEY_EXPIRES_AFTER_SECONDS = "site_key_expires_after_seconds";
    private static final String SITE_KEY_ROTATION_CUT_OFF_DAYS = "site_key_rotation_cut_off_days";
    private static final String REFRESH_KEY_ROTATION_CUT_OFF_DAYS = "refresh_key_rotation_cut_off_days";
    private static final String FILTER_KEY_OVER_CUT_OFF_DAYS = "filter_key_over_cut_off_days";

    private final AdminAuthMiddleware auth;
    private final Clock clock;
    private final WriteLock writeLock;
    private final EncryptionKeyStoreWriter storeWriter;
    private final KeysetKeyStoreWriter keysetKeyStoreWriter;
    private final RotatingKeyStore keyProvider;
    private final RotatingKeysetKeyStore keysetKeyProvider;

    private final RotatingAdminKeysetStore keysetProvider;
    private final AdminKeysetWriter keysetStoreWriter;
    private final IKeyGenerator keyGenerator;

    private final Duration masterKeyActivatesIn;
    private final Duration masterKeyExpiresAfter;
    private final Duration masterKeyRotationCutoffTime;
    private final Duration siteKeyActivatesIn;
    private final Duration siteKeyExpiresAfter;
    private final Duration siteKeyRotationCutOffTime;
    private final Duration refreshKeyRotationCutOffTime;
    private final boolean filterKeyOverCutOffTime;

    private final boolean enableKeysets;

    public EncryptionKeyService(JsonObject config,
                                AdminAuthMiddleware auth,
                                WriteLock writeLock,
                                EncryptionKeyStoreWriter storeWriter,
                                KeysetKeyStoreWriter keysetKeyStoreWriter,
                                RotatingKeyStore keyProvider,
                                RotatingKeysetKeyStore keysetKeyProvider,
                                RotatingAdminKeysetStore keysetProvider,
                                AdminKeysetWriter keysetStoreWriter,
                                IKeyGenerator keyGenerator,
                                Clock clock) {
        this.auth = auth;
        this.writeLock = writeLock;
        this.storeWriter = storeWriter;
        this.keysetKeyStoreWriter = keysetKeyStoreWriter;
        this.keyProvider = keyProvider;
        this.keysetKeyProvider = keysetKeyProvider;
        this.keysetStoreWriter = keysetStoreWriter;
        this.keysetProvider = keysetProvider;
        this.keyGenerator = keyGenerator;
        this.clock = clock;

        masterKeyActivatesIn = Duration.ofSeconds(config.getInteger(MASTER_KEY_ACTIVATES_IN_SECONDS));
        masterKeyExpiresAfter = Duration.ofSeconds(config.getInteger(MASTER_KEY_EXPIRES_AFTER_SECONDS));
        masterKeyRotationCutoffTime = Duration.ofDays(config.getInteger(MASTER_KEY_ROTATION_CUT_OFF_DAYS, 37));
        siteKeyActivatesIn = Duration.ofSeconds(config.getInteger(SITE_KEY_ACTIVATES_IN_SECONDS));
        siteKeyExpiresAfter = Duration.ofSeconds(config.getInteger(SITE_KEY_EXPIRES_AFTER_SECONDS));
        siteKeyRotationCutOffTime = Duration.ofDays(config.getInteger(SITE_KEY_ROTATION_CUT_OFF_DAYS, 37));
        refreshKeyRotationCutOffTime = Duration.ofDays(config.getInteger(REFRESH_KEY_ROTATION_CUT_OFF_DAYS, 37));
        filterKeyOverCutOffTime = config.getBoolean(FILTER_KEY_OVER_CUT_OFF_DAYS, false);

        if (masterKeyActivatesIn.compareTo(masterKeyExpiresAfter) >= 0) {
            throw new IllegalStateException(MASTER_KEY_ACTIVATES_IN_SECONDS + " must be greater than " + MASTER_KEY_EXPIRES_AFTER_SECONDS);
        }
        if (siteKeyActivatesIn.compareTo(siteKeyExpiresAfter) >= 0) {
            throw new IllegalStateException(SITE_KEY_ACTIVATES_IN_SECONDS + " must be greater than " + SITE_KEY_EXPIRES_AFTER_SECONDS);
        }

        enableKeysets = config.getBoolean(enableKeysetConfigProp);
    }

    @Override
    public void setupRoutes(Router router) {
        router.get("/api/key/list").handler(
            auth.handle(this::handleKeyList, Role.DEFAULT));

        if(enableKeysets) {
            router.get("/api/key/list_keyset_keys").handler(
                auth.handle(this::handleKeysetKeyList, Role.DEFAULT));
        }

        router.post("/api/key/rewrite_metadata").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleRewriteMetadata(ctx);
            }
        }, Role.PRIVILEGED));

        router.post("/api/key/rotate_master").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleRotateMasterKey(ctx);
            }
        }, Role.DEFAULT, Role.SECRET_ROTATION));

        router.post("/api/key/add").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleAddSiteKey(ctx);
            }
        }, Role.DEFAULT));

        router.post("/api/key/rotate_site").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleRotateSiteKey(ctx);
            }
        }, Role.DEFAULT));

        if(enableKeysets) {
            router.post("/api/key/rotate_keyset_key").blockingHandler(auth.handle((ctx) -> {
                synchronized (writeLock) {
                    this.handleRotateKeysetKey(ctx);
                }
            }, Role.DEFAULT));
        }

        router.post("/api/key/rotate_all_sites").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleRotateAllSiteKeys(ctx);
            }
        }, Role.DEFAULT, Role.SECRET_ROTATION));
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

        return addSiteKeys(Arrays.asList(siteId), activatesIn, siteKeyExpiresAfter, false).get(0);
    }

    @Override
    public KeysetKey addKeysetKey(int keysetId) throws Exception {
        loadKeysetKeys();
        return addKeysetKeys(Arrays.asList(keysetId), siteKeyActivatesIn, siteKeyExpiresAfter, false).get(0);
    }

    public void createKeysetKeys() throws Exception {
        loadAllContent();

        final List<EncryptionKey> encryptionKeys = this.keyProvider.getSnapshot().getActiveKeySet();
        List<EncryptionKey> addKeys = new ArrayList<>();

        for (EncryptionKey key: encryptionKeys) {
            KeysetKey keysetKey = this.keysetKeyProvider.getSnapshot().getKey(key.getId());
            if(keysetKey == null) {
                addKeys.add(key);
            }
        }
        catchUpKeysetKeys(addKeys, false, this.keyProvider.getMetadata().getInteger("max_key_id"));
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

    private void handleKeysetKeyList(RoutingContext rc) {
        try {
            final JsonArray ja = new JsonArray();
            this.keysetKeyProvider.getSnapshot().getAllKeysetKeys().stream()
                    .sorted(Comparator.comparingInt(KeysetKey::getKeysetId).thenComparing(KeysetKey::getActivates))
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
            final RotationResult<EncryptionKey> masterKeyResult = rotateKeys(rc, masterKeyActivatesIn, masterKeyExpiresAfter,
                s -> s == Const.Data.MasterKeySiteId || s == Const.Data.RefreshKeySiteId);

            final JsonArray ja = new JsonArray();
            masterKeyResult.rotatedKeys.stream().forEachOrdered(k -> ja.add(toJson(k)));
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

            final RotationResult<EncryptionKey> result = rotateKeys(rc, siteKeyActivatesIn, siteKeyExpiresAfter, s -> s == siteId);
            if (result == null) {
                return;
            } else if (!result.rotatedIds.contains(siteId)) {
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

    private void handleRotateKeysetKey(RoutingContext rc) {
        try {
            final Optional<Integer>  keysetIdOpt = RequestUtil.getKeysetId(rc, "keyset_id");
            if(!keysetIdOpt.isPresent()) return;
            final int keysetId = keysetIdOpt.get();

            //TODO how to check for valid keyset id
            final RotationResult<KeysetKey> result = rotateKeysetKeys(rc, siteKeyActivatesIn, siteKeyExpiresAfter, s -> s == keysetId);
            if (result == null) {
                return;
            } else if (!result.rotatedIds.contains(keysetId)) {
                ResponseUtil.error(rc, 404, "No keys found for the keyset id: " + keysetId);
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
            final RotationResult<EncryptionKey> result = rotateKeys(rc, siteKeyActivatesIn, siteKeyExpiresAfter, s -> SiteUtil.isValidSiteId(s) || s == Const.Data.AdvertisingTokenSiteId);
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

    private RotationResult<EncryptionKey> rotateKeys(RoutingContext rc, Duration activatesIn, Duration expiresAfter, Predicate<Integer> siteSelector)
            throws Exception {
        final Duration minAge = RequestUtil.getDuration(rc, "min_age_seconds");
        if (minAge == null) return null;
        final Optional<Boolean> force = RequestUtil.getBoolean(rc, "force", false);
        if (!force.isPresent()) return null;

        return rotateKeys(siteSelector, minAge, activatesIn, expiresAfter, force.get());
    }

    private RotationResult<KeysetKey> rotateKeysetKeys(RoutingContext rc, Duration activatesIn, Duration expiresAfter, Predicate<Integer> siteSelector)
            throws Exception {
        final Duration minAge = RequestUtil.getDuration(rc, "min_age_seconds");
        if (minAge == null) return null;
        final Optional<Boolean> force = RequestUtil.getBoolean(rc, "force", false);
        if (!force.isPresent()) return null;

        return rotateKeysetKeys(siteSelector, minAge, activatesIn, expiresAfter, force.get());
    }

    private RotationResult<EncryptionKey> rotateKeys(Predicate<Integer> siteSelector, Duration minAge, Duration activatesIn, Duration expiresAfter, boolean force)
            throws Exception {
        RotationResult<EncryptionKey> result = new RotationResult();

        // force refresh manually
        loadAllContent();

        final List<EncryptionKey> allKeys = this.keyProvider.getSnapshot().getActiveKeySet();

        // report back which sites were considered
        result.rotatedIds = allKeys.stream()
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

        result.rotatedKeys = addSiteKeys(siteIds, activatesIn, expiresAfter, true);

        return result;
    }

    private RotationResult<KeysetKey> rotateKeysetKeys(Predicate<Integer> siteSelector, Duration minAge, Duration activatesIn, Duration expiresAfter, boolean force)
            throws Exception {
        RotationResult<KeysetKey> result = new RotationResult();

        loadAllContent();

        final List<KeysetKey> keysetKeys = this.keysetKeyProvider.getSnapshot().getAllKeysetKeys();

        result.rotatedIds = keysetKeys.stream()
                .map(KeysetKey::getKeysetId)
                .filter(siteSelector::test)
                .collect(toSet());

        final Instant now = clock.now();
        final Instant activatesThreshold = now.minusSeconds(minAge.getSeconds());

        List<Integer> keysetIds = keysetKeys.stream()
                .filter(k -> siteSelector.test(k.getKeysetId()))
                .collect(groupingBy(KeysetKey::getKeysetId, maxBy(Comparator.comparing(KeysetKey::getActivates))))
                .values().stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(k -> force || k.getActivates().isBefore(activatesThreshold))
                .map(KeysetKey::getKeysetId)
                .collect(toList());

        if (keysetIds.isEmpty()) {
            return result;
        }

        result.rotatedKeys = addKeysetKeys(keysetIds, activatesIn, expiresAfter, true);

        return result;
    }

    private List<EncryptionKey> addSiteKeys(Iterable<Integer> siteIds, Duration activatesIn, Duration expiresAfter, boolean isDuringRotation)
            throws Exception {
        final Instant now = clock.now();

        final List<EncryptionKey> keys = this.keyProvider.getSnapshot().getActiveKeySet().stream()
                .sorted(Comparator.comparingInt(EncryptionKey::getId))
                .filter(k -> isWithinCutOffTime(k, now, isDuringRotation))
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
        catchUpKeysetKeys(addedKeys, isDuringRotation, maxKeyId);

        return addedKeys;
    }

    private void catchUpKeys(Iterable<KeysetKey> missingKeys, boolean isDuringRotation, int maxKeyId)
            throws Exception {
        final Instant now = clock.now();

        final List<EncryptionKey> keys = this.keyProvider.getSnapshot().getActiveKeySet().stream()
                .sorted(Comparator.comparingInt(EncryptionKey::getId))
                .filter(k -> isWithinCutOffTime(k, now, isDuringRotation))
                .collect(Collectors.toList());

        final List<EncryptionKey> addedKeys = new ArrayList<>();

        for (KeysetKey key : missingKeys) {
            final int siteId = getSiteId(key.getKeysetId());
            final EncryptionKey newKey = new EncryptionKey(key.getId(), key.getKeyBytes(), key.getCreated(), key.getActivates(), key.getExpires(), siteId);
            keys.add(newKey);
            addedKeys.add(newKey);
        }
        storeWriter.upload(keys, maxKeyId);
    }

    private int getOrCreateKeysetId(int siteId)
        throws Exception {
        Map<Integer, AdminKeyset> currentKeysets = keysetProvider.getSnapshot().getAllKeysets();
        AdminKeyset keyset = lookUpKeyset(siteId, currentKeysets);
        if(keyset == null) {
            int newKeysetId = getMaxKeyset(currentKeysets)+1;
            if(siteId == Const.Data.MasterKeySiteId) {
                newKeysetId = Const.Data.MasterKeysetId;
            }
            else if(siteId == Const.Data.RefreshKeySiteId) {
                newKeysetId = Const.Data.RefreshKeysetId;
            }
            else if(siteId == Const.Data.AdvertisingTokenSiteId) {
                newKeysetId = Const.Data.FallbackPublisherKeysetId;
            }
            keyset = createDefaultKeyset(siteId, newKeysetId);
            currentKeysets.put(newKeysetId, keyset);
            keysetStoreWriter.upload(currentKeysets, null);
        }

        return keyset.getKeysetId();
    }

    private int getSiteId(int keysetId) throws Exception {
        Map<Integer, AdminKeyset> currentKeysets = keysetProvider.getSnapshot().getAllKeysets();
        return currentKeysets.get(keysetId).getSiteId();
    }

    private List<KeysetKey> addKeysetKeys(Iterable<Integer> keysetIds, Duration activatesIn, Duration expiresAfter, boolean isDuringRotation)
        throws Exception {
        final Instant now = clock.now();

        final List<KeysetKey> keys = this.keysetKeyProvider.getSnapshot().getAllKeysetKeys().stream()
                .sorted(Comparator.comparingInt(KeysetKey::getId))
                .filter(k -> isWithinCutOffTime(k, now, isDuringRotation))
                .collect(Collectors.toList());


        int maxKeyId = MaxKeyUtil.getMaxKeysetKeyId(this.keysetKeyProvider.getSnapshot().getAllKeysetKeys(),
                this.keysetKeyProvider.getMetadata().getInteger("max_key_id"));

        final List<KeysetKey> addedKeys = new ArrayList<>();

        for (Integer keysetId : keysetIds) {
            ++maxKeyId;
            final byte[] secret = keyGenerator.generateRandomKey(32);
            final Instant created = now;
            final Instant activates = created.plusSeconds(activatesIn.getSeconds());
            final Instant expires = activates.plusSeconds(expiresAfter.getSeconds());
            final KeysetKey key = new KeysetKey(maxKeyId, secret, created, activates, expires, keysetId);
            keys.add(key);
            addedKeys.add(key);
        }
        keysetKeyStoreWriter.upload(keys, maxKeyId);
        catchUpKeys(addedKeys, isDuringRotation, maxKeyId);

        return addedKeys;
    }

    private void catchUpKeysetKeys(Iterable<EncryptionKey> missingKeys, boolean isDuringRotation, int maxKeyId)
        throws Exception {
        if(!enableKeysets) return;
        final Instant now = clock.now();

        final List<KeysetKey> keys = this.keysetKeyProvider.getSnapshot().getAllKeysetKeys().stream()
                .sorted(Comparator.comparingInt(KeysetKey::getId))
                .filter(k -> isWithinCutOffTime(k, now, isDuringRotation))
                .collect(Collectors.toList());


        final List<KeysetKey> addedKeys = new ArrayList<>();

        for (EncryptionKey key : missingKeys) {
            final int keysetId = getOrCreateKeysetId(key.getSiteId());
            final KeysetKey newKey = new KeysetKey(key.getId(), key.getKeyBytes(), key.getCreated(), key.getActivates(), key.getExpires(), keysetId);
            keys.add(newKey);
            addedKeys.add(newKey);
        }
        keysetKeyStoreWriter.upload(keys, maxKeyId);
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

    private JsonObject toJson(KeysetKey key) {
        JsonObject jo = new JsonObject();
        jo.put("id", key.getId());
        jo.put("keyset_id", key.getKeysetId());
        jo.put("created", key.getCreated().toEpochMilli());
        jo.put("activates", key.getActivates().toEpochMilli());
        jo.put("expires", key.getExpires().toEpochMilli());
        return jo;
    }

    private boolean isWithinCutOffTime(EncryptionKey key, Instant now, boolean duringRotation) {
        if (!(filterKeyOverCutOffTime && duringRotation)) {
            return true;
        }
        int siteId = key.getSiteId();
        Duration cutoffTime;
        switch (siteId) {
            case Const.Data.MasterKeySiteId:
                cutoffTime = masterKeyRotationCutoffTime;
                break;
            case Const.Data.RefreshKeySiteId:
                cutoffTime = refreshKeyRotationCutOffTime;
                break;
            default:
                cutoffTime = siteKeyRotationCutOffTime;
                break;
        }
        return now.compareTo(key.getExpires().plus(cutoffTime.toDays(), ChronoUnit.DAYS)) < 0;
    }
    private boolean isWithinCutOffTime(KeysetKey key, Instant now, boolean duringRotation) {
        if (!(filterKeyOverCutOffTime && duringRotation)) {
            return true;
        }
        Duration cutoffTime = siteKeyRotationCutOffTime;
        return now.compareTo(key.getExpires().plus(cutoffTime.toDays(), ChronoUnit.DAYS)) < 0;
    }

    private void loadAllContent() throws Exception {
        this.keyProvider.loadContent();
        loadKeysets();
        loadKeysetKeys();
    }

    private void loadKeysetKeys() throws Exception {
        if(enableKeysets){
            this.keysetKeyProvider.loadContent();
        }
    }

    private void loadKeysets() throws Exception {
        if(enableKeysets) {
            this.keysetProvider.loadContent();
        }
    }
}
