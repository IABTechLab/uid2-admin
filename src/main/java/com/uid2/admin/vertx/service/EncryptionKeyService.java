package com.uid2.admin.vertx.service;

import com.uid2.admin.secret.IEncryptionKeyManager;
import com.uid2.admin.secret.IKeyGenerator;
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
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.maxBy;

public class EncryptionKeyService implements IService, IEncryptionKeyManager {
    private static class RotationResult {
        public Set<Integer> siteIds = null;
        public List<EncryptionKey> rotatedKeys = new ArrayList<>();
    }

    private static final String MASTER_KEY_ACTIVATES_IN_SECONDS = "master_key_activates_in_seconds";
    private static final String MASTER_KEY_EXPIRES_AFTER_SECONDS = "master_key_expires_after_seconds";
    private static final String SITE_KEY_ACTIVATES_IN_SECONDS = "site_key_activates_in_seconds";
    private static final String SITE_KEY_EXPIRES_AFTER_SECONDS = "site_key_expires_after_seconds";

    private final AuthMiddleware auth;
    private final WriteLock writeLock;
    private final EncryptionKeyStoreWriter storeWriter;
    private final RotatingKeyStore keyProvider;
    private final IKeyGenerator keyGenerator;

    private final Duration masterKeyActivatesIn;
    private final Duration masterKeyExpiresAfter;
    private final Duration siteKeyActivatesIn;
    private final Duration siteKeyExpiresAfter;

    public EncryptionKeyService(
            JsonObject config,
            AuthMiddleware auth,
            WriteLock writeLock,
            EncryptionKeyStoreWriter storeWriter,
            RotatingKeyStore keyProvider,
            IKeyGenerator keyGenerator) {
        this.auth = auth;
        this.writeLock = writeLock;
        this.storeWriter = storeWriter;
        this.keyProvider = keyProvider;
        this.keyGenerator = keyGenerator;

        masterKeyActivatesIn = Duration.ofSeconds(config.getInteger(MASTER_KEY_ACTIVATES_IN_SECONDS));
        masterKeyExpiresAfter = Duration.ofSeconds(config.getInteger(MASTER_KEY_EXPIRES_AFTER_SECONDS));
        siteKeyActivatesIn = Duration.ofSeconds(config.getInteger(SITE_KEY_ACTIVATES_IN_SECONDS));
        siteKeyExpiresAfter = Duration.ofSeconds(config.getInteger(SITE_KEY_EXPIRES_AFTER_SECONDS));

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

        router.post("/api/key/rotate_master").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleRotateMasterKey(ctx);
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

    @Override
    public EncryptionKey addSiteKey(int siteId) throws Exception {
        // force refresh manually
        this.keyProvider.loadContent();

        return addSiteKeys(Arrays.asList(siteId), siteKeyActivatesIn, siteKeyExpiresAfter).get(0);
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
            final RotationResult result = rotateKeys(rc, masterKeyActivatesIn, masterKeyExpiresAfter,
                    s -> s == Const.Data.MasterKeySiteId || s == Const.Data.RefreshKeySiteId);

            final JsonArray ja = new JsonArray();
            result.rotatedKeys.stream().forEachOrdered(k -> ja.add(toJson(k)));
            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(ja.encode());
        } catch (Exception e) {
            rc.fail(500, e);
        }
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

            final RotationResult result = rotateKeys(rc, siteKeyActivatesIn, siteKeyExpiresAfter, s -> s == siteId);
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
            final RotationResult result = rotateKeys(rc, siteKeyActivatesIn, siteKeyExpiresAfter, s -> SiteUtil.isValidSiteId(s) || s == Const.Data.AdvertisingTokenSiteId);
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

    private RotationResult rotateKeys(RoutingContext rc, Duration activatesIn, Duration expiresAfter, Predicate<Integer> siteSelector)
            throws Exception {
        final Duration minAge = RequestUtil.getDuration(rc, "min_age_seconds");
        if (minAge == null) return null;
        final Optional<Boolean> force = RequestUtil.getBoolean(rc, "force", false);
        if (!force.isPresent()) return null;

        return rotateKeys(siteSelector, minAge, activatesIn, expiresAfter, force.get());
    }

    private RotationResult rotateKeys(Predicate<Integer> siteSelector, Duration minAge, Duration activatesIn, Duration expiresAfter, boolean force)
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

        final Instant now = Instant.now();
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

        result.rotatedKeys = addSiteKeys(siteIds, activatesIn, expiresAfter);

        return result;
    }

    private List<EncryptionKey> addSiteKeys(Iterable<Integer> siteIds, Duration activatesIn, Duration expiresAfter)
            throws Exception {
        final List<EncryptionKey> keys = this.keyProvider.getSnapshot().getActiveKeySet().stream()
                .sorted(Comparator.comparingInt(EncryptionKey::getId))
                .collect(Collectors.toList());

        int maxKeyId = MaxKeyUtil.getMaxKeyId(this.keyProvider.getSnapshot().getActiveKeySet(),
                this.keyProvider.getMetadata().getInteger("max_key_id"));

        final List<EncryptionKey> addedKeys = new ArrayList<>();
        final Instant now = Instant.now();

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
