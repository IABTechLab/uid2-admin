package com.uid2.admin.vertx;

import com.uid2.admin.auth.AdminKeyset;
import com.uid2.admin.store.Clock;
import com.uid2.admin.vertx.service.EncryptionKeyService;
import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.Const;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.EncryptionKey;
import com.uid2.shared.model.KeysetKey;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class EncryptionKeyServiceTest extends ServiceTestBase {
    private static final int MASTER_KEY_ACTIVATES_IN_SECONDS = 3600;
    private static final int MASTER_KEY_EXPIRES_AFTER_SECONDS = 7200;
    private static final int MASTER_KEY_ROTATION_CUT_OFF_DAYS = 30;
    private static final int SITE_KEY_ACTIVATES_IN_SECONDS = 36000;
    private static final int SITE_KEY_EXPIRES_AFTER_SECONDS = 72000;
    private static final int SITE_KEY_ROTATION_CUT_OFF_DAYS = 30;
    private static final int REFRESH_KEY_ROTATION_CUT_OFF_DAYS = 30;
    private static final long KEY_CREATE_TIME_IN_MILLI = 100010011L;
    private static final long KEY_ACTIVATE_TIME_IN_MILLI = 100020011L;
    private static final long KEY_EXPIRE_TIME_IN_MILLI = 100030011L;
    private static final long TEN_DAYS_IN_MILLI = 864000000L;
    private static final long A_HUNDRED_DAYS_IN_MILLI = 86400000000L;
    private static final long A_HUNDRED_DAYS_IN_SECONDS = 8640000L;
    private static final int MAX_KEY_ID = 777;
    private static final boolean FILTER_KEY_OVER_CUT_OFF_DAYS = true;
    private Clock clock = mock(Clock.class);
    private EncryptionKeyService keyService = null;

    @BeforeEach
    void setUp() {
        // seconds since epoch, DO NOT set this to small number as it will make the time before 1970-00-00 and overflow.
        // set it to be key creation timestamp + 10days because we need to filter out expired keys [UID2-599]
        when(clock.now()).thenReturn(Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI + TEN_DAYS_IN_MILLI));
    }

    @Override
    protected IService createService() {
        this.config.put("master_key_activates_in_seconds", MASTER_KEY_ACTIVATES_IN_SECONDS);
        this.config.put("master_key_expires_after_seconds", MASTER_KEY_EXPIRES_AFTER_SECONDS);
        this.config.put("master_key_rotation_cut_off_days", MASTER_KEY_ROTATION_CUT_OFF_DAYS);
        this.config.put("site_key_activates_in_seconds", SITE_KEY_ACTIVATES_IN_SECONDS);
        this.config.put("site_key_expires_after_seconds", SITE_KEY_EXPIRES_AFTER_SECONDS);
        this.config.put("site_key_rotation_cut_off_days", SITE_KEY_ROTATION_CUT_OFF_DAYS);
        this.config.put("refresh_key_rotation_cut_off_days", REFRESH_KEY_ROTATION_CUT_OFF_DAYS);
        this.config.put("filter_key_over_cut_off_days", FILTER_KEY_OVER_CUT_OFF_DAYS);
        this.config.put("enable_keysets", true);

        keyService = new EncryptionKeyService(config, auth, writeLock, encryptionKeyStoreWriter, keysetKeyStoreWriter,
                keyProvider, keysetKeyProvider, adminKeysetProvider, adminKeysetWriter, keyGenerator, clock);
        return keyService;
    }

    private void assertKeyActivation(Instant generatedTime, int activatesIn, int expiresAfter,
                                     Instant actualCreated, Instant actualActivates, Instant actualExpires) {
        assertTrue(generatedTime.plusSeconds(-5).isBefore(actualCreated));
        assertTrue(generatedTime.plusSeconds(5).isAfter(actualCreated));
        assertEquals(actualCreated.plusSeconds(activatesIn), actualActivates);
        assertEquals(actualActivates.plusSeconds(expiresAfter), actualExpires);
    }

    private void assertSiteKeyActivation(EncryptionKey key, Instant generatedTime) {
        assertKeyActivation(generatedTime, SITE_KEY_ACTIVATES_IN_SECONDS, SITE_KEY_EXPIRES_AFTER_SECONDS,
                key.getCreated(), key.getActivates(), key.getExpires());
    }

    private void assertSiteKeyActivation(KeysetKey key, Instant generatedTime) {
        assertKeyActivation(generatedTime, SITE_KEY_ACTIVATES_IN_SECONDS, SITE_KEY_EXPIRES_AFTER_SECONDS,
                key.getCreated(), key.getActivates(), key.getExpires());
    }

    private void checkEncryptionKeyResponse(EncryptionKey[] expectedKeys, Object[] actualKeys) {
        assertEquals(expectedKeys.length, actualKeys.length);
        for (int i = 0; i < expectedKeys.length; ++i) {
            final EncryptionKey expectedKey = expectedKeys[i];
            final JsonObject actualKey = (JsonObject) actualKeys[i];
            assertEquals(expectedKey.getId(), actualKey.getInteger("id"));
            assertEquals(expectedKey.getCreated(), Instant.ofEpochMilli(actualKey.getLong("created")));
            assertEquals(expectedKey.getActivates(), Instant.ofEpochMilli(actualKey.getLong("activates")));
            assertEquals(expectedKey.getExpires(), Instant.ofEpochMilli(actualKey.getLong("expires")));
            assertEquals(expectedKey.getSiteId(), actualKey.getInteger("site_id"));
            assertFalse(actualKey.containsKey("secret"));
        }
    }

    private void checkRotatedKeyResponse(int startingKeyId, int[] expectedSiteIds, int activatesIn, int expiresAfter, Object[] actualKeys) {
        assertEquals(expectedSiteIds.length, actualKeys.length);
        final Set<Integer> actualSiteIds = new HashSet<>();
        for (int i = 0; i < actualKeys.length; ++i) {
            final int expectedKeyId = startingKeyId + i;
            final JsonObject actualKey = (JsonObject) actualKeys[i];
            assertEquals(expectedKeyId, actualKey.getInteger("id"));
            assertKeyActivation(clock.now(), activatesIn, expiresAfter,
                    Instant.ofEpochMilli(actualKey.getLong("created")),
                    Instant.ofEpochMilli(actualKey.getLong("activates")),
                    Instant.ofEpochMilli(actualKey.getLong("expires")));
            actualSiteIds.add(actualKey.getInteger("site_id"));
            assertFalse(actualKey.containsKey("secret"));
        }
        for (int expectedSiteId : expectedSiteIds) {
            assertTrue(actualSiteIds.contains(expectedSiteId));
        }
    }

    private void checkRotatedKeysetKeyResponse(int startingKeyId, int[] expectedKeysetIds, int activatesIn, int expiresAfter, Object[] actualKeys) {
        assertEquals(expectedKeysetIds.length, actualKeys.length);
        final Set<Integer> actualKeysetIds = new HashSet<>();
        for (int i = 0; i < actualKeys.length; ++i) {
            final int expectedKeyId = startingKeyId + i;
            final JsonObject actualKey = (JsonObject) actualKeys[i];
            assertEquals(expectedKeyId, actualKey.getInteger("id"));
            assertKeyActivation(clock.now(), activatesIn, expiresAfter,
                    Instant.ofEpochMilli(actualKey.getLong("created")),
                    Instant.ofEpochMilli(actualKey.getLong("activates")),
                    Instant.ofEpochMilli(actualKey.getLong("expires")));
            actualKeysetIds.add(actualKey.getInteger("keyset_id"));
            assertFalse(actualKey.containsKey("secret"));
        }
        for (int expectedKeysetId : expectedKeysetIds) {
            assertTrue(actualKeysetIds.contains(expectedKeysetId));
        }
    }

    @Test
    void addSiteKey() throws Exception {
        setEncryptionKeys(123);
        final EncryptionKey key = keyService.addSiteKey(5);
        verify(encryptionKeyStoreWriter).upload(collectionOfSize(1), eq(124));
        assertSiteKeyActivation(key, clock.now());
    }

    @Test
    void addSiteKeyAddsKeysetAndKey() throws Exception {
        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 2, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};
        setAdminKeysets(keysets);
        setEncryptionKeys(123);
        setKeysetKeys(123);
        final EncryptionKey key = keyService.addSiteKey(5);

        AdminKeyset expected = new AdminKeyset(4, 5, "", null, Instant.now().getEpochSecond(), true, true, new HashSet<>());
        assertNotNull(keysets.get(4));
        assertTrue(keysets.get(4).equals(expected));
        verify(keysetKeyStoreWriter).upload(collectionOfSize(1), eq(124));
    }

    @Test
    void addSiteKeyUsesKeysetAndAddsKey() throws Exception {
        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};
        setAdminKeysets(keysets);
        setEncryptionKeys(123);
        setKeysetKeys(123);
        final EncryptionKey key = keyService.addSiteKey(5);

        assertNotNull(keysets.get(1));
        assertTrue(keysets.get(1).equals(keysets.get(1)));
        verify(keysetKeyStoreWriter).upload(collectionOfSize(1), eq(124));
    }

    @Test
    void addKeysetKey() throws Exception {
        setKeysetKeys(123);
        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};
        setAdminKeysets(keysets);
        final KeysetKey key = keyService.addKeysetKey(1);
        verify(keysetKeyStoreWriter).upload(collectionOfSize(1), eq(124));
        assertSiteKeyActivation(key, clock.now());
    }

    @Test
    void addKeysetKeyAddsSiteKey() throws Exception {
        setKeysetKeys(123);
        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};
        setAdminKeysets(keysets);
        final KeysetKey key = keyService.addKeysetKey(1);
        verify(encryptionKeyStoreWriter).upload(collectionOfSize(1), eq(124));
    }

    @Test
    void listKeysNoKeys(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SECRET_MANAGER);

        get(vertx, "api/key/list", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            assertEquals(0, response.bodyAsJsonArray().size());
            testContext.completeNow();
        });
    }

    @Test
    void listKeysWithKeys(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.SECRET_MANAGER);

        final EncryptionKey[] keys = {
                new EncryptionKey(11, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), 5),
                new EncryptionKey(12, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+1), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI+1), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI+1), 5),
                new EncryptionKey(13, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+2), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI+2), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI+2), 6),
                new EncryptionKey(14, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+3), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI+3), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI+3), 7),
        };
        setEncryptionKeys(MAX_KEY_ID, keys);

        get(vertx, "api/key/list", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkEncryptionKeyResponse(keys, response.bodyAsJsonArray().stream().toArray());
            testContext.completeNow();
        });
    }

    @Test
    void rotateMasterKey(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.SECRET_MANAGER);

        final EncryptionKey[] keys = {
                new EncryptionKey(11, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), -1),
                new EncryptionKey(12, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+1), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI+1), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI+1), 5),
        };
        setEncryptionKeys(MAX_KEY_ID, keys);

        setKeysetKeys(MAX_KEY_ID);

        post(vertx, "api/key/rotate_master?min_age_seconds=100", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkRotatedKeyResponse(MAX_KEY_ID+1, new int[] { -1 },
                    MASTER_KEY_ACTIVATES_IN_SECONDS, MASTER_KEY_EXPIRES_AFTER_SECONDS,
                    response.bodyAsJsonArray().stream().toArray());
            try {
                verify(encryptionKeyStoreWriter, times(1)).upload(collectionOfSize(3), eq(MAX_KEY_ID+1));
                verify(keysetKeyStoreWriter, times(1)).upload(collectionOfSize(1), eq(MAX_KEY_ID+1));
            } catch (Exception ex) {
                fail(ex);
            }
            testContext.completeNow();
        });
    }

    @Test
    void rotateRefreshKey(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.SECRET_MANAGER);

        final EncryptionKey[] keys = {
                new EncryptionKey(12, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+1), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI+1), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI+1), 5),
                new EncryptionKey(13, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+2), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI+1), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI+1), -2),
        };
        setEncryptionKeys(MAX_KEY_ID, keys);

        post(vertx, "api/key/rotate_master?min_age_seconds=100", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkRotatedKeyResponse(MAX_KEY_ID+1, new int[] { -2 },
                    MASTER_KEY_ACTIVATES_IN_SECONDS, MASTER_KEY_EXPIRES_AFTER_SECONDS,
                    response.bodyAsJsonArray().stream().toArray());
            try {
                verify(encryptionKeyStoreWriter, times(1)).upload(collectionOfSize(3), eq(MAX_KEY_ID+1));
                verify(keysetKeyStoreWriter, times(1)).upload(collectionOfSize(1), eq(MAX_KEY_ID+1));
            } catch (Exception ex) {
                fail(ex);
            }
            testContext.completeNow();
        });
    }

    @Test
    void filterOutMasterKeysOverCutoffTime(Vertx vertx, VertxTestContext testContext) throws Exception {
        // set it to be key creation timestamp + 100days so that we can create expired keys [UID2-599]
        when(clock.now()).thenReturn(Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI + A_HUNDRED_DAYS_IN_MILLI));
        fakeAuth(Role.SECRET_MANAGER);

        final EncryptionKey[] keys = {
                new EncryptionKey(11, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), -1),
        };

        setEncryptionKeys(MAX_KEY_ID, keys);

        post(vertx, "api/key/rotate_master?min_age_seconds=100", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkRotatedKeyResponse(MAX_KEY_ID+1, new int[] { -1, },
                    MASTER_KEY_ACTIVATES_IN_SECONDS, MASTER_KEY_EXPIRES_AFTER_SECONDS,
                    response.bodyAsJsonArray().stream().toArray());
            try {
                verify(encryptionKeyStoreWriter, times(1)).upload(collectionOfSize(1), eq(MAX_KEY_ID+1));
            } catch (Exception ex) {
                fail(ex);
            }
            testContext.completeNow();
        });
    }

    @Test
    void filterOutRefreshKeysOverCutoffTime(Vertx vertx, VertxTestContext testContext) throws Exception {
        // set it to be key creation timestamp + 100days so that we can create expired keys [UID2-599]
        when(clock.now()).thenReturn(Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI + A_HUNDRED_DAYS_IN_MILLI));
        fakeAuth(Role.SECRET_MANAGER);

        final EncryptionKey[] keys = {
                new EncryptionKey(11, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), -2),
        };

        setEncryptionKeys(MAX_KEY_ID, keys);

        post(vertx, "api/key/rotate_master?min_age_seconds=100", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkRotatedKeyResponse(MAX_KEY_ID+1, new int[] { -2 },
                    MASTER_KEY_ACTIVATES_IN_SECONDS, MASTER_KEY_EXPIRES_AFTER_SECONDS,
                    response.bodyAsJsonArray().stream().toArray());
            try {
                verify(encryptionKeyStoreWriter, times(1)).upload(collectionOfSize(1), eq(MAX_KEY_ID+1));
            } catch (Exception ex) {
                fail(ex);
            }
            testContext.completeNow();
        });
    }

    @Test
    void rotateMasterKeyNewEnough(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.SECRET_MANAGER);

        final EncryptionKey[] keys = {
                new EncryptionKey(11, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), -1),
                new EncryptionKey(12, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+1), clock.now().plusSeconds(MASTER_KEY_ACTIVATES_IN_SECONDS + A_HUNDRED_DAYS_IN_SECONDS), clock.now().plusSeconds(MASTER_KEY_EXPIRES_AFTER_SECONDS + A_HUNDRED_DAYS_IN_SECONDS), -1),
                new EncryptionKey(13, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), -2),
                new EncryptionKey(14, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+1), clock.now().plusSeconds(MASTER_KEY_ACTIVATES_IN_SECONDS + A_HUNDRED_DAYS_IN_SECONDS), clock.now().plusSeconds(MASTER_KEY_EXPIRES_AFTER_SECONDS + A_HUNDRED_DAYS_IN_SECONDS), -2),
        };
        setEncryptionKeys(MAX_KEY_ID, keys);

        post(vertx, "api/key/rotate_master?min_age_seconds=100", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            assertEquals(0, response.bodyAsJsonArray().size());
            try {
                verify(encryptionKeyStoreWriter, times(0)).upload(any(), anyInt());
            } catch (Exception ex) {
                fail(ex);
            }
            testContext.completeNow();
        });
    }

    @Test
    void rotateMasterKeyNewEnoughWithForce(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.SECRET_MANAGER);

        final EncryptionKey[] keys = {
                new EncryptionKey(11, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), -1),
                new EncryptionKey(12, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+1), clock.now().plusSeconds(MASTER_KEY_ACTIVATES_IN_SECONDS + A_HUNDRED_DAYS_IN_SECONDS), clock.now().plusSeconds(MASTER_KEY_EXPIRES_AFTER_SECONDS + A_HUNDRED_DAYS_IN_SECONDS), -1),
        };
        setEncryptionKeys(MAX_KEY_ID, keys);

        post(vertx, "api/key/rotate_master?min_age_seconds=100&force=true", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkRotatedKeyResponse(MAX_KEY_ID+1, new int[] { -1, },
                    MASTER_KEY_ACTIVATES_IN_SECONDS, MASTER_KEY_EXPIRES_AFTER_SECONDS,
                    response.bodyAsJsonArray().stream().toArray());
            try {
                verify(encryptionKeyStoreWriter, times(1)).upload(collectionOfSize(3), eq(MAX_KEY_ID+1));
            } catch (Exception ex) {
                fail(ex);
            }
            testContext.completeNow();
        });
    }

    @Test
    void rotateRefreshKeyNewEnoughWithForce(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.SECRET_MANAGER);

        final EncryptionKey[] keys = {
                new EncryptionKey(13, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), -2),
                new EncryptionKey(14, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+1), clock.now().plusSeconds(MASTER_KEY_ACTIVATES_IN_SECONDS + A_HUNDRED_DAYS_IN_SECONDS), clock.now().plusSeconds(MASTER_KEY_EXPIRES_AFTER_SECONDS + A_HUNDRED_DAYS_IN_SECONDS), -2),
        };
        setEncryptionKeys(MAX_KEY_ID, keys);

        post(vertx, "api/key/rotate_master?min_age_seconds=100&force=true", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkRotatedKeyResponse(MAX_KEY_ID+1, new int[] { -2 },
                    MASTER_KEY_ACTIVATES_IN_SECONDS, MASTER_KEY_EXPIRES_AFTER_SECONDS,
                    response.bodyAsJsonArray().stream().toArray());
            try {
                verify(encryptionKeyStoreWriter, times(1)).upload(collectionOfSize(3), eq(MAX_KEY_ID+1));
            } catch (Exception ex) {
                fail(ex);
            }
            testContext.completeNow();
        });
    }

    @Test
    void rotateSiteKey(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.SECRET_MANAGER);

        final EncryptionKey[] keys = {
                new EncryptionKey(11, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), -1),
                new EncryptionKey(12, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+1), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI+1), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI+1), 5),
        };
        setEncryptionKeys(MAX_KEY_ID, keys);

        post(vertx, "api/key/rotate_site?site_id=5&min_age_seconds=100", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkRotatedKeyResponse(MAX_KEY_ID+1, new int[] { 5 },
                    SITE_KEY_ACTIVATES_IN_SECONDS, SITE_KEY_EXPIRES_AFTER_SECONDS,
                    response.bodyAsJsonArray().stream().toArray());
            try {
                verify(encryptionKeyStoreWriter).upload(collectionOfSize(keys.length+1), eq(MAX_KEY_ID+1));
            } catch (Exception ex) {
                fail(ex);
            }
            testContext.completeNow();
        });
    }

    @Test
    void rotateKeysetKey(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.SECRET_MANAGER);

        final KeysetKey[] keys = {
                new KeysetKey(11, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), 4),
                new KeysetKey(12, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+1), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI+1), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI+1), 5)
        };
        setKeysetKeys(MAX_KEY_ID, keys);
        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(4, new AdminKeyset(4, 2, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(5, new AdminKeyset(5, 3, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};
        setAdminKeysets(keysets);

        post(vertx, "api/key/rotate_keyset_key?keyset_id=5&min_age_seconds=100", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkRotatedKeysetKeyResponse(MAX_KEY_ID+1, new int[] { 5 },
                    SITE_KEY_ACTIVATES_IN_SECONDS, SITE_KEY_EXPIRES_AFTER_SECONDS,
                    response.bodyAsJsonArray().stream().toArray());

            try {
                verify(keysetKeyStoreWriter).upload(collectionOfSize(keys.length+1), eq(MAX_KEY_ID+1));
                verify(encryptionKeyStoreWriter).upload(collectionOfSize(1), eq(MAX_KEY_ID+1));
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
    }

    @Test
    void rotateSiteKeyNewEnough(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.SECRET_MANAGER);

        final EncryptionKey[] keys = {
                new EncryptionKey(11, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), 5),
                new EncryptionKey(12, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+1), clock.now().plusSeconds(SITE_KEY_ACTIVATES_IN_SECONDS + 100), clock.now().plusSeconds(MASTER_KEY_EXPIRES_AFTER_SECONDS + A_HUNDRED_DAYS_IN_SECONDS), 5),
        };
        setEncryptionKeys(MAX_KEY_ID, keys);

        post(vertx, "api/key/rotate_site?site_id=5&min_age_seconds=100", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            assertEquals(0, response.bodyAsJsonArray().size());
            try {
                verify(encryptionKeyStoreWriter, times(0)).upload(any(), anyInt());
            } catch (Exception ex) {
                fail(ex);
            }
            testContext.completeNow();
        });
    }

    @Test
    void rotateSiteKeyNewEnoughWithForce(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.SECRET_MANAGER);

        final EncryptionKey[] keys = {
                new EncryptionKey(11, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), 5),
                new EncryptionKey(12, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+1), clock.now().plusSeconds(SITE_KEY_ACTIVATES_IN_SECONDS + 100), clock.now().plusSeconds(MASTER_KEY_EXPIRES_AFTER_SECONDS + A_HUNDRED_DAYS_IN_SECONDS), 5),
        };
        setEncryptionKeys(MAX_KEY_ID, keys);

        post(vertx, "api/key/rotate_site?site_id=5&min_age_seconds=100&force=true", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkRotatedKeyResponse(MAX_KEY_ID+1, new int[] { 5 },
                    SITE_KEY_ACTIVATES_IN_SECONDS, SITE_KEY_EXPIRES_AFTER_SECONDS,
                    response.bodyAsJsonArray().stream().toArray());
            try {
                verify(encryptionKeyStoreWriter).upload(collectionOfSize(keys.length+1), eq(MAX_KEY_ID+1));
            } catch (Exception ex) {
                fail(ex);
            }
            testContext.completeNow();
        });
    }

    @Test
    void rotateSiteKeyNoSiteKey(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.SECRET_MANAGER);

        setEncryptionKeys(MAX_KEY_ID);

        post(vertx, "api/key/rotate_site?site_id=5&min_age_seconds=100", "", expectHttpError(testContext, 404));
        verify(encryptionKeyStoreWriter, times(0)).upload(any(), anyInt());
    }

    @Test
    void rotateSiteKeyMasterSite(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.SECRET_MANAGER);

        final EncryptionKey[] keys = {
                new EncryptionKey(11, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), -1),
                new EncryptionKey(12, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+1), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI+1), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI+1), 5),
        };
        setEncryptionKeys(MAX_KEY_ID, keys);

        post(vertx, "api/key/rotate_site?site_id=-1&min_age_seconds=100", "", expectHttpError(testContext, 400));
        verify(encryptionKeyStoreWriter, times(0)).upload(any(), anyInt());
    }

    @Test
    void rotateSiteKeySpecialSite1(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.SECRET_MANAGER);

        final EncryptionKey[] keys = {
                new EncryptionKey(11, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), 1),
        };
        setEncryptionKeys(MAX_KEY_ID, keys);

        post(vertx, "api/key/rotate_site?site_id=-1&min_age_seconds=100", "", expectHttpError(testContext, 400));
        verify(encryptionKeyStoreWriter, times(0)).upload(any(), anyInt());
    }

    @Test
    void rotateSiteKeySpecialSite2(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.SECRET_MANAGER);

        final EncryptionKey[] keys = {
                new EncryptionKey(11, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), 2),
        };
        setEncryptionKeys(MAX_KEY_ID, keys);

        post(vertx, "api/key/rotate_site?site_id=2&min_age_seconds=100", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkRotatedKeyResponse(MAX_KEY_ID+1, new int[] { 2 },
                    SITE_KEY_ACTIVATES_IN_SECONDS, SITE_KEY_EXPIRES_AFTER_SECONDS,
                    response.bodyAsJsonArray().stream().toArray());
            try {
                verify(encryptionKeyStoreWriter).upload(collectionOfSize(keys.length+1), eq(MAX_KEY_ID+1));
            } catch (Exception ex) {
                fail(ex);
            }
            testContext.completeNow();
        });
    }

    @Test
    void filterOutSiteKeysOverCutoffTime(Vertx vertx, VertxTestContext testContext) throws Exception {
        // set it to be key creation timestamp + 100days so that we can create expired keys [UID2-599]
        when(clock.now()).thenReturn(Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI + A_HUNDRED_DAYS_IN_MILLI));
        fakeAuth(Role.SECRET_MANAGER);

        final EncryptionKey[] keys = {
                new EncryptionKey(11, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), 2),
        };
        setEncryptionKeys(MAX_KEY_ID, keys);

        post(vertx, "api/key/rotate_site?site_id=2&min_age_seconds=100", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkRotatedKeyResponse(MAX_KEY_ID+1, new int[] { 2 },
                    SITE_KEY_ACTIVATES_IN_SECONDS, SITE_KEY_EXPIRES_AFTER_SECONDS,
                    response.bodyAsJsonArray().stream().toArray());
            try {
                verify(encryptionKeyStoreWriter).upload(collectionOfSize(1), eq(MAX_KEY_ID+1));
            } catch (Exception ex) {
                fail(ex);
            }
            testContext.completeNow();
        });
    }

    @Test
    void rotateAllSiteKeys(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.SECRET_MANAGER);

        final EncryptionKey[] keys = {
                new EncryptionKey(11, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), 5),
                new EncryptionKey(12, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+1), clock.now().plusSeconds(MASTER_KEY_ACTIVATES_IN_SECONDS + A_HUNDRED_DAYS_IN_SECONDS), clock.now().plusSeconds(MASTER_KEY_EXPIRES_AFTER_SECONDS + A_HUNDRED_DAYS_IN_SECONDS), 5),
                new EncryptionKey(13, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+2), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI+2), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI+2), 6),
                new EncryptionKey(14, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+3), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI+3), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI+3), 7),
                new EncryptionKey(15, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+4), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI+4), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI+4), -1),

        };
        setEncryptionKeys(MAX_KEY_ID, keys);

        post(vertx, "api/key/rotate_all_sites?site_id=5&min_age_seconds=100", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkRotatedKeyResponse(MAX_KEY_ID+1, new int[] { 6, 7 },
                    SITE_KEY_ACTIVATES_IN_SECONDS, SITE_KEY_EXPIRES_AFTER_SECONDS,
                    response.bodyAsJsonArray().stream().toArray());
            try {
                verify(encryptionKeyStoreWriter).upload(collectionOfSize(keys.length+2), eq(MAX_KEY_ID+2));
                verify(keysetKeyStoreWriter).upload(collectionOfSize(2), eq(MAX_KEY_ID+2));
            } catch (Exception ex) {
                fail(ex);
            }
            testContext.completeNow();
        });
    }

    @Test
    void rotateAllSiteKeysWithKeysetkeys(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.SECRET_MANAGER);

        final EncryptionKey[] keys = {
                new EncryptionKey(11, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), 5),
                new EncryptionKey(12, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+1), clock.now().plusSeconds(MASTER_KEY_ACTIVATES_IN_SECONDS + A_HUNDRED_DAYS_IN_SECONDS), clock.now().plusSeconds(MASTER_KEY_EXPIRES_AFTER_SECONDS + A_HUNDRED_DAYS_IN_SECONDS), 5),
                new EncryptionKey(13, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+2), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI+2), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI+2), 6),
                new EncryptionKey(14, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+3), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI+3), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI+3), 7),
                new EncryptionKey(15, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+4), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI+4), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI+4), -1),

        };
        setEncryptionKeys(MAX_KEY_ID, keys);

        final KeysetKey[] keysetKeys = {
                new KeysetKey(11, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), 5),
                new KeysetKey(12, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+1), clock.now().plusSeconds(MASTER_KEY_ACTIVATES_IN_SECONDS + A_HUNDRED_DAYS_IN_SECONDS), clock.now().plusSeconds(MASTER_KEY_EXPIRES_AFTER_SECONDS + A_HUNDRED_DAYS_IN_SECONDS), 5),
                new KeysetKey(13, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+2), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI+2), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI+2), 6),
                new KeysetKey(14, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+3), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI+3), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI+3), 7),
        };
        setKeysetKeys(MAX_KEY_ID, keysetKeys);

        post(vertx, "api/key/rotate_all_sites?site_id=5&min_age_seconds=100", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkRotatedKeyResponse(MAX_KEY_ID+1, new int[] { 6, 7 },
                    SITE_KEY_ACTIVATES_IN_SECONDS, SITE_KEY_EXPIRES_AFTER_SECONDS,
                    response.bodyAsJsonArray().stream().toArray());
            try {
                verify(encryptionKeyStoreWriter).upload(collectionOfSize(keys.length+2), eq(MAX_KEY_ID+2));
                verify(keysetKeyStoreWriter).upload(collectionOfSize(keysetKeys.length+2), eq(MAX_KEY_ID+2));
            } catch (Exception ex) {
                fail(ex);
            }
            testContext.completeNow();
        });
    }

    @Test
    void rotateAllSiteKeysWithForce(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.SECRET_MANAGER);

        final EncryptionKey[] keys = {
                new EncryptionKey(11, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), 5),
                new EncryptionKey(12, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+1), clock.now().plusSeconds(MASTER_KEY_ACTIVATES_IN_SECONDS + A_HUNDRED_DAYS_IN_SECONDS), clock.now().plusSeconds(MASTER_KEY_EXPIRES_AFTER_SECONDS + A_HUNDRED_DAYS_IN_SECONDS), 5),
                new EncryptionKey(13, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+2), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI+2), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI+2), 6),
                new EncryptionKey(14, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+3), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI+3), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI+3), 7),
                new EncryptionKey(15, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+4), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI+4), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI+4), 2),
                new EncryptionKey(16, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+5), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI+5), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI+5), -1),
        };
        setEncryptionKeys(MAX_KEY_ID, keys);

        post(vertx, "api/key/rotate_all_sites?site_id=5&min_age_seconds=100&force=true", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkRotatedKeyResponse(MAX_KEY_ID+1, new int[] { 2, 5, 6, 7 },
                    SITE_KEY_ACTIVATES_IN_SECONDS, SITE_KEY_EXPIRES_AFTER_SECONDS,
                    response.bodyAsJsonArray().stream().toArray());
            try {
                verify(encryptionKeyStoreWriter).upload(collectionOfSize(keys.length+4), eq(MAX_KEY_ID+4));
            } catch (Exception ex) {
                fail(ex);
            }
            testContext.completeNow();
        });
    }

    @Test
    void rotateAllSiteKeysAllUpToDate(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.SECRET_MANAGER);

        final EncryptionKey[] keys = {
                new EncryptionKey(11, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), 5),
                new EncryptionKey(12, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+1), clock.now().plusSeconds(MASTER_KEY_ACTIVATES_IN_SECONDS + A_HUNDRED_DAYS_IN_SECONDS), clock.now().plusSeconds(MASTER_KEY_EXPIRES_AFTER_SECONDS + A_HUNDRED_DAYS_IN_SECONDS), 5),
        };
        setEncryptionKeys(MAX_KEY_ID, keys);

        post(vertx, "api/key/rotate_all_sites?site_id=5&min_age_seconds=100", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            assertEquals(0, response.bodyAsJsonArray().size());
            try {
                verify(encryptionKeyStoreWriter, times(0)).upload(any(), anyInt());
            } catch (Exception ex) {
                fail(ex);
            }
            testContext.completeNow();
        });
    }

    @Test
    void rotateAllSiteKeysNoSiteKeys(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.SECRET_MANAGER);

        setEncryptionKeys(MAX_KEY_ID);

        post(vertx, "api/key/rotate_all_sites?site_id=5&min_age_seconds=100", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            assertEquals(0, response.bodyAsJsonArray().size());
            try {
                verify(encryptionKeyStoreWriter, times(0)).upload(any(), anyInt());
            } catch (Exception ex) {
                fail(ex);
            }
            testContext.completeNow();
        });
    }

    @Test
    void createKeysetKeysFirstRun() throws Exception {
        final EncryptionKey[] keys = {
                new EncryptionKey(11, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), 5),
                new EncryptionKey(12, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+1), clock.now().plusSeconds(MASTER_KEY_ACTIVATES_IN_SECONDS + A_HUNDRED_DAYS_IN_SECONDS), clock.now().plusSeconds(MASTER_KEY_EXPIRES_AFTER_SECONDS + A_HUNDRED_DAYS_IN_SECONDS), 5),
                new EncryptionKey(13, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), 6),
                new EncryptionKey(14, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), 7),
                new EncryptionKey(15, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), -1),
                new EncryptionKey(16, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), -2),
                new EncryptionKey(17, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), 2),
        };
        setEncryptionKeys(MAX_KEY_ID, keys);
        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
        }};
        setAdminKeysets(keysets);
        final KeysetKey[] keysetKeys = {};
        setKeysetKeys(0, keysetKeys);
        keyService.createKeysetKeys();
        // 7 keys should be added
        verify(keysetKeyStoreWriter).upload(collectionOfSize(7), eq(777));
        // 6 keysets should be created
        assertEquals(6, keysets.keySet().size());
        //Special Keysets are set correctly
        // Master key site id -1 : keyset id 1
        assertEquals(-1, keysets.get(-1).getSiteId());
        // Master key site id -2 : keyset id 2
        assertEquals(-2, keysets.get(-2).getSiteId());
        // Master key site id 2 : keyset id 3
        assertEquals(2, keysets.get(2).getSiteId());
        assertEquals(Const.Data.MasterKeySiteId, keysets.get(Const.Data.MasterKeysetId).getSiteId());
        assertEquals(Const.Data.RefreshKeySiteId, keysets.get(Const.Data.RefreshKeysetId).getSiteId());
        assertEquals(Const.Data.AdvertisingTokenSiteId, keysets.get(Const.Data.FallbackPublisherKeysetId).getSiteId());
    }

    @Test
    void createKeysetKeysNoKeysNeed() throws Exception {
        final EncryptionKey[] keys = {
                new EncryptionKey(11, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), 5),
                new EncryptionKey(12, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+1), clock.now().plusSeconds(MASTER_KEY_ACTIVATES_IN_SECONDS + A_HUNDRED_DAYS_IN_SECONDS), clock.now().plusSeconds(MASTER_KEY_EXPIRES_AFTER_SECONDS + A_HUNDRED_DAYS_IN_SECONDS), 5),
                new EncryptionKey(13, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), 6),
                new EncryptionKey(14, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), 7),
        };
        setEncryptionKeys(MAX_KEY_ID, keys);
        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
        }};
        setAdminKeysets(keysets);
        final KeysetKey[] keysetKeys = {
                new KeysetKey(11, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), 1),
                new KeysetKey(12, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), 2),
                new KeysetKey(13, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), 3),
                new KeysetKey(14, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), 4),
        };
        setKeysetKeys(0, keysetKeys);
        keyService.createKeysetKeys();
        // No new keys should be uploaded and no keyset created
        verify(keysetKeyStoreWriter).upload(collectionOfSize(4), eq(777));
        assertEquals(0, keysets.keySet().size());
    }

    @Test
    void createKeysetKeysMissingKey() throws Exception {
        final EncryptionKey[] keys = {
                new EncryptionKey(11, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), 5),
                new EncryptionKey(12, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+1), clock.now().plusSeconds(MASTER_KEY_ACTIVATES_IN_SECONDS + A_HUNDRED_DAYS_IN_SECONDS), clock.now().plusSeconds(MASTER_KEY_EXPIRES_AFTER_SECONDS + A_HUNDRED_DAYS_IN_SECONDS), 5),
                new EncryptionKey(13, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), 6),
                new EncryptionKey(14, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), 7),
                new EncryptionKey(15, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), 7),
                new EncryptionKey(16, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), 8),
        };
        setEncryptionKeys(MAX_KEY_ID, keys);
        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 7, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};
        setAdminKeysets(keysets);
        // Missing 2 keys, 1 without a keyset
        final KeysetKey[] keysetKeys = {
                new KeysetKey(11, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), 1),
                new KeysetKey(12, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), 2),
                new KeysetKey(13, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), 3),
                new KeysetKey(14, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), 4),
        };
        setKeysetKeys(0, keysetKeys);
        keyService.createKeysetKeys();
        // 6 keys should be added
        verify(keysetKeyStoreWriter).upload(collectionOfSize(6), eq(777));
        // One keyset created
        assertEquals(2, keysets.keySet().size());
    }
}
