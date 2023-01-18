package com.uid2.admin.vertx;

import com.uid2.admin.store.Clock;
import com.uid2.admin.vertx.service.EncryptionKeyService;
import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.EncryptionKey;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
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
    private static final long KEY_CREATE_TIME_IN_MILLI = 100010011L;
    private static final long KEY_ACTIVATE_TIME_IN_MILLI = 100020011L;
    private static final long KEY_EXPIRE_TIME_IN_MILLI = 100030011L;
    private static final long TEN_DAYS_IN_MILLI = 864000000L;
    private static final int MAX_KEY_ID = 777;
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

        keyService = new EncryptionKeyService(config, auth, writeLock, encryptionKeyStoreWriter, keyProvider, keyGenerator, clock);
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

    @Test
    void addSiteKey() throws Exception {
        setEncryptionKeys(123);
        final EncryptionKey key = keyService.addSiteKey(5);
        verify(encryptionKeyStoreWriter).upload(collectionOfSize(1), eq(124));
        assertSiteKeyActivation(key, clock.now());
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
                new EncryptionKey(13, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+2), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI+1), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI+1), -2),
        };
        setEncryptionKeys(MAX_KEY_ID, keys);

        post(vertx, "api/key/rotate_master?min_age_seconds=100", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkRotatedKeyResponse(MAX_KEY_ID+1, new int[] { -1, -2 },
                    MASTER_KEY_ACTIVATES_IN_SECONDS, MASTER_KEY_EXPIRES_AFTER_SECONDS,
                    response.bodyAsJsonArray().stream().toArray());
            try {
                verify(encryptionKeyStoreWriter).upload(collectionOfSize(keys.length+2), eq(MAX_KEY_ID+2));
            } catch (Exception ex) {
                fail(ex);
            }
            testContext.completeNow();
        });
    }

    @Test
    void filterOutMasterKeysOverCutoffTime(Vertx vertx, VertxTestContext testContext) throws Exception {
        // set it to be key creation timestamp + 100days so that we can create expired keys [UID2-599]
        when(clock.now()).thenReturn(Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI + TEN_DAYS_IN_MILLI * 10));
        fakeAuth(Role.SECRET_MANAGER);

        final EncryptionKey[] keys = {
                new EncryptionKey(11, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), -1),
                new EncryptionKey(12, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), -2),
        };

        setEncryptionKeys(777, keys);

        post(vertx, "api/key/rotate_master?min_age_seconds=100", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkRotatedKeyResponse(777+1, new int[] { -1, -2 },
                    MASTER_KEY_ACTIVATES_IN_SECONDS, MASTER_KEY_EXPIRES_AFTER_SECONDS,
                    response.bodyAsJsonArray().stream().toArray());
            try {
                verify(encryptionKeyStoreWriter).upload(collectionOfSize(2), eq(777+2));
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
                new EncryptionKey(12, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+1), clock.now().plusSeconds(MASTER_KEY_ACTIVATES_IN_SECONDS + 1000), Instant.MAX, -1),
                new EncryptionKey(13, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), -2),
                new EncryptionKey(14, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+1), clock.now().plusSeconds(MASTER_KEY_ACTIVATES_IN_SECONDS + 1000), Instant.MAX, -2),
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
                new EncryptionKey(12, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+1), clock.now().plusSeconds(MASTER_KEY_ACTIVATES_IN_SECONDS + 1000), Instant.MAX, -1),
                new EncryptionKey(13, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), -2),
                new EncryptionKey(14, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+1), clock.now().plusSeconds(MASTER_KEY_ACTIVATES_IN_SECONDS + 1000), Instant.MAX, -2),
        };
        setEncryptionKeys(MAX_KEY_ID, keys);

        post(vertx, "api/key/rotate_master?min_age_seconds=100&force=true", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkRotatedKeyResponse(MAX_KEY_ID+1, new int[] { -1, -2 },
                    MASTER_KEY_ACTIVATES_IN_SECONDS, MASTER_KEY_EXPIRES_AFTER_SECONDS,
                    response.bodyAsJsonArray().stream().toArray());
            try {
                verify(encryptionKeyStoreWriter).upload(collectionOfSize(keys.length+2), eq(MAX_KEY_ID+2));
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
    void rotateSiteKeyNewEnough(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.SECRET_MANAGER);

        final EncryptionKey[] keys = {
                new EncryptionKey(11, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), 5),
                new EncryptionKey(12, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+1), clock.now().plusSeconds(SITE_KEY_ACTIVATES_IN_SECONDS + 100), Instant.MAX, 5),
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
                new EncryptionKey(12, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+1), clock.now().plusSeconds(SITE_KEY_ACTIVATES_IN_SECONDS + 100), Instant.MAX, 5),
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
        when(clock.now()).thenReturn(Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI + TEN_DAYS_IN_MILLI * 10));
        fakeAuth(Role.SECRET_MANAGER);

        final EncryptionKey[] keys = {
                new EncryptionKey(11, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), 2),
        };
        setEncryptionKeys(777, keys);

        post(vertx, "api/key/rotate_site?site_id=2&min_age_seconds=100", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkRotatedKeyResponse(777+1, new int[] { 2 },
                    SITE_KEY_ACTIVATES_IN_SECONDS, SITE_KEY_EXPIRES_AFTER_SECONDS,
                    response.bodyAsJsonArray().stream().toArray());
            try {
                verify(encryptionKeyStoreWriter).upload(collectionOfSize(1), eq(777+1));
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
                new EncryptionKey(10, null, Instant.ofEpochMilli(10010), Instant.ofEpochMilli(20010), Instant.ofEpochMilli(30010), -1),
                new EncryptionKey(11, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI), 5),
                new EncryptionKey(12, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+1), Instant.MAX, Instant.MAX, 5),
                new EncryptionKey(13, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+2), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI+2), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI+2), 6),
                new EncryptionKey(14, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+3), Instant.ofEpochMilli(KEY_ACTIVATE_TIME_IN_MILLI+3), Instant.ofEpochMilli(KEY_EXPIRE_TIME_IN_MILLI+3), 7),
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
                new EncryptionKey(12, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+1), Instant.MAX, Instant.MAX, 5),
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
                new EncryptionKey(12, null, Instant.ofEpochMilli(KEY_CREATE_TIME_IN_MILLI+1), Instant.MAX, Instant.MAX, 5),
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
}
