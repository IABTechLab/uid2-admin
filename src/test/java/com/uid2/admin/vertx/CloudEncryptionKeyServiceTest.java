package com.uid2.admin.vertx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uid2.admin.model.CloudEncryptionKeyListResponse;
import com.uid2.admin.model.CloudEncryptionKeySummary;
import com.uid2.admin.vertx.service.CloudEncryptionKeyService;
import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.CloudEncryptionKey;
import com.uid2.shared.util.Mapper;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CloudEncryptionKeyServiceTest extends ServiceTestBase {
    private static final ObjectMapper OBJECT_MAPPER = Mapper.getInstance();

    @Override
    protected IService createService() {
        return new CloudEncryptionKeyService(auth, cloudEncryptionKeyProvider);
    }

    @Test
    public void testList_noKeys(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);
        var expected = new CloudEncryptionKeyListResponse(List.of());

        get(vertx, testContext, Endpoints.CLOUD_ENCRYPTION_KEY_LIST, response -> {
            assertEquals(200, response.statusCode());

            CloudEncryptionKeyListResponse actual = OBJECT_MAPPER.readValue(response.bodyAsString(), new TypeReference<>() {});
            assertEquals(expected, actual);

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

        CloudEncryptionKey key1 = new CloudEncryptionKey(1, 2, 100, 100, "secret 1");
        CloudEncryptionKey key2 = new CloudEncryptionKey(2, 2, 200, 100, "secret 2");

        setCloudEncryptionKeys(key1, key2);

        var expected = new CloudEncryptionKeyListResponse(List.of(
                new CloudEncryptionKeySummary(1, 2, 100, 100),
                new CloudEncryptionKeySummary(2, 2, 200, 100)
        ));

        get(vertx, testContext, Endpoints.CLOUD_ENCRYPTION_KEY_LIST, response -> {
            assertEquals(200, response.statusCode());

            CloudEncryptionKeyListResponse actual = OBJECT_MAPPER.readValue(response.bodyAsString(), new TypeReference<>() {});
            assertEquals(expected, actual);

            testContext.completeNow();
        });
    }
}
