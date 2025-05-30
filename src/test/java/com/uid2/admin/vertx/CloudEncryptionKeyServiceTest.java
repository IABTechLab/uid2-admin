package com.uid2.admin.vertx;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uid2.admin.cloudencryption.CloudEncryptionKeyManager;
import com.uid2.admin.cloudencryption.CloudKeyStatePlanner;
import com.uid2.admin.cloudencryption.ExpiredKeyCountRetentionStrategy;
import com.uid2.admin.job.JobDispatcher;
import com.uid2.admin.model.CloudEncryptionKeyListResponse;
import com.uid2.admin.model.CloudEncryptionKeySummary;
import com.uid2.admin.store.InstantClock;
import com.uid2.admin.vertx.service.CloudEncryptionKeyService;
import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.CloudEncryptionKey;
import com.uid2.shared.util.Mapper;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CloudEncryptionKeyServiceTest extends ServiceTestBase {
    private static final ObjectMapper OBJECT_MAPPER = Mapper.getInstance();
    private final CloudEncryptionKeyListResponse noKeys = new CloudEncryptionKeyListResponse(Set.of());
    private final long one_hour = 60 * 60;
    private final long longAgo = 0L;
    private final long before = 100L;
    private final long now = 200L;
    private final int siteId1 = 1;
    private final int keyId1 = 1;
    private final int keyId2 = 2;
    private final int keyId3 = 3;
    private final int keyId4 = 4;
    private final OperatorKey operator1 = testOperatorKey(siteId1, "one");
    private final OperatorKey operator2 = testOperatorKey(siteId1, "two");
    private final String secret1 = "secret 1";
    private final String secret2 = "secret 2";
    private final String secret3 = "secret 3";
    private final String secret4 = "secret 4";

    @Override
    protected IService createService() {
        var retentionStrategy = new ExpiredKeyCountRetentionStrategy(2);
        var rotationStrategy = new CloudKeyStatePlanner(cloudSecretGenerator, clock, retentionStrategy);
        var manager = new CloudEncryptionKeyManager(
                cloudEncryptionKeyProvider,
                cloudEncryptionKeyStoreWriter,
                operatorKeyProvider,
                rotationStrategy
        );

        var dispatcher = new JobDispatcher("test-job-dispatcher", 10, 5, new InstantClock());

        return new CloudEncryptionKeyService(auth, manager, dispatcher);
    }

    @Test
    public void testList_noKeys(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        get(vertx, testContext, Endpoints.CLOUD_ENCRYPTION_KEY_LIST, response -> {
            assertEquals(200, response.statusCode());
            assertEquals(noKeys, parseKeyListResponse(response));

            testContext.completeNow();
        });
    }

    @Test
    public void testList_noAccess(Vertx vertx, VertxTestContext testContext) {
        get(vertx, testContext, Endpoints.CLOUD_ENCRYPTION_KEY_LIST, response -> {
            assertEquals(401, response.statusCode());

            testContext.completeNow();
        });
    }

    @Test
    public void testList_withKeys(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        var date1EpochSeconds = 100;
        var date2EpochSeconds = 200;
        var date1Iso = "1970-01-01T00:01:40Z";
        var date2Iso = "1970-01-01T00:03:20Z";

        CloudEncryptionKey key1 = new CloudEncryptionKey(1, 2, date1EpochSeconds, date1EpochSeconds, "secret 1");
        CloudEncryptionKey key2 = new CloudEncryptionKey(2, 2, date2EpochSeconds, date1EpochSeconds, "secret 2");

        setCloudEncryptionKeys(key1, key2);

        var expected = new CloudEncryptionKeyListResponse(Set.of(
                new CloudEncryptionKeySummary(1, 2, date1Iso, date1Iso),
                new CloudEncryptionKeySummary(2, 2, date2Iso, date1Iso)
        ));

        get(vertx, testContext, Endpoints.CLOUD_ENCRYPTION_KEY_LIST, response -> {
            assertEquals(200, response.statusCode());
            var actual = parseKeyListResponse(response);
            assertEquals(expected, actual);

            testContext.completeNow();
        });
    }

    @Test
    public void testRotate_noAccess(Vertx vertx, VertxTestContext testContext) {
        post(vertx, testContext, Endpoints.CLOUD_ENCRYPTION_KEY_ROTATE, null, response -> {
            assertEquals(401, response.statusCode());

            testContext.completeNow();
        });
    }

    @Test
    public void testRotate_fail_on_request(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);
        post(vertx, testContext, Endpoints.CLOUD_ENCRYPTION_KEY_ROTATE + "?fail=true", null, response -> {
            assertEquals(500, response.statusCode());

            testContext.completeNow();
        });
    }

    @Test
    public void testRotate_canBeRotatedBySecretRotationJob(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SECRET_ROTATION);
        post(vertx, testContext, Endpoints.CLOUD_ENCRYPTION_KEY_ROTATE, null, response -> {
            assertEquals(200, response.statusCode());

            testContext.completeNow();
        });
    }

    @Test
    public void testRotate_noOperatorsNoKeys(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        setCloudEncryptionKeys();
        setOperatorKeys();

        post(vertx, testContext, Endpoints.CLOUD_ENCRYPTION_KEY_ROTATE, null, rotateResponse -> {
            assertEquals(200, rotateResponse.statusCode());

            verify(cloudEncryptionKeyStoreWriter).upload(Map.of(), null);

            testContext.completeNow();
        });
    }

    @Test
    public void testRotate_forOperatorSiteWithNoKeysCreatesKey(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        setCloudEncryptionKeys();
        setOperatorKeys(operator1);
        when(cloudSecretGenerator.generate()).thenReturn(secret1);
        when(clock.getEpochSecond()).thenReturn(now);

        var expected = Map.of(
                siteId1, new CloudEncryptionKey(keyId1, siteId1, now + one_hour, now, secret1)
        );

        post(vertx, testContext, Endpoints.CLOUD_ENCRYPTION_KEY_ROTATE, null, rotateResponse -> {
            assertEquals(200, rotateResponse.statusCode());
            verify(cloudEncryptionKeyStoreWriter).upload(expected, null);
            testContext.completeNow();
        });
    }

    @Test
    public void testRotate_CreatesOneKeyWhenThereAreMultipleOperatorsPerSite(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        setCloudEncryptionKeys();
        setOperatorKeys(operator1, operator2);

        post(vertx, testContext, Endpoints.CLOUD_ENCRYPTION_KEY_ROTATE, null, rotateResponse -> {
            assertEquals(200, rotateResponse.statusCode());

            get(vertx, testContext, Endpoints.CLOUD_ENCRYPTION_KEY_LIST, listResponse -> {
                assertEquals(200, listResponse.statusCode());
                assertEquals(noKeys, parseKeyListResponse(listResponse));

                testContext.completeNow();
            });
        });
    }

    @Test
    public void testRotate_forOperatorSiteWithKeyCreatesNewActiveKey(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        var existingKey1 = new CloudEncryptionKey(keyId1, siteId1, before, before, secret1);
        var existingKey2 = new CloudEncryptionKey(keyId2, siteId1, longAgo, longAgo, secret2);
        var existingKey3 = new CloudEncryptionKey(keyId3, siteId1, before, before, secret3);

        var expected = Map.of(
                keyId1, existingKey1,
                // We allow 2 expired keys, but have 4. Key 2 is removed as oldest expired key.
                keyId3, existingKey3,
                keyId4, new CloudEncryptionKey(4, siteId1, now + one_hour, now, secret4)
        );

        setCloudEncryptionKeys(existingKey1, existingKey2, existingKey3);
        setOperatorKeys(operator1);
        when(cloudSecretGenerator.generate()).thenReturn(secret4);
        when(clock.getEpochSecond()).thenReturn(now);

        post(vertx, testContext, Endpoints.CLOUD_ENCRYPTION_KEY_ROTATE, null, rotateResponse -> {
            assertEquals(200, rotateResponse.statusCode());
            verify(cloudEncryptionKeyStoreWriter).upload(expected, null);
            testContext.completeNow();
        });
    }

    @Test
    public void testRotate_removesExcessiveExpiredKeys(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        var existingKey = new CloudEncryptionKey(siteId1, siteId1, before, before, secret1);
        var key2Id = keyId1 + 1;

        var expected = Map.of(
                keyId1, existingKey,
                key2Id, new CloudEncryptionKey(key2Id, siteId1, now + one_hour, now, secret2)
        );

        setCloudEncryptionKeys(existingKey);
        setOperatorKeys(operator1);
        when(cloudSecretGenerator.generate()).thenReturn(secret2);
        when(clock.getEpochSecond()).thenReturn(now);

        post(vertx, testContext, Endpoints.CLOUD_ENCRYPTION_KEY_ROTATE, null, rotateResponse -> {
            assertEquals(200, rotateResponse.statusCode());
            verify(cloudEncryptionKeyStoreWriter).upload(expected, null);
            testContext.completeNow();
        });
    }

    @Test
    public void testRotate_handleExceptions(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);
        setOperatorKeys(operator1);
        when(clock.getEpochSecond()).thenThrow(new RuntimeException("oops"));

        post(vertx, testContext, Endpoints.CLOUD_ENCRYPTION_KEY_ROTATE, null, rotateResponse -> {
            assertEquals(500, rotateResponse.statusCode());
            testContext.completeNow();
        });
    }

    private static CloudEncryptionKeyListResponse parseKeyListResponse(HttpResponse<Buffer> response) throws JsonProcessingException {
        return OBJECT_MAPPER.readValue(response.bodyAsString(), new TypeReference<>() {
        });
    }

    private static OperatorKey testOperatorKey(int siteId, String keyId) {
        return new OperatorKey(
                "hash " + keyId,
                "key salt " + keyId,
                "name " + keyId,
                "contact " + keyId,
                "protocol " + keyId,
                0,
                false,
                siteId,
                keyId
        );
    }
}
