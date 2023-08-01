package com.uid2.admin.vertx;

import com.uid2.admin.auth.AdminUser;
import com.uid2.admin.vertx.service.AdminKeyService;
import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.KeyGenerationResult;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.VertxTestContext;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.instancio.Select.field;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class AdminKeyServiceTest extends ServiceTestBase {
    final String adminKeyPrefix = "UID2-A-L-";
    final String expectedAdminKey = adminKeyPrefix + "abcdef.abcdefabcdefabcdef";

    @Override
    protected IService createService() {
        this.config.put("admin_key_prefix", adminKeyPrefix);
        return new AdminKeyService(config, auth, writeLock, adminUserStoreWriter, adminUserProvider, keyGenerator, clientKeyStoreWriter, encryptionKeyStoreWriter, keyAclStoreWriter);
    }

    @Test
    void addAdminUsesKeyPrefixAndFormattedKeyString(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.ADMINISTRATOR);
        when(this.keyGenerator.generateFormattedKeyStringAndKeyHash(anyString(), anyInt())).thenReturn(new KeyGenerationResult(expectedAdminKey, ""));

        AdminUser expectedAdminUser = this.getAdminUser(expectedAdminKey);

        post(vertx, String.format("api/admin/add?name=%s&roles=administrator", expectedAdminUser.getName()), "", response -> {
            try {
                HttpResponse<Buffer> httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();

                assertAll(
                        () -> assertTrue(response.succeeded()),
                        () -> assertNotNull(result),
                        () -> assertEquals(expectedAdminKey, result.getString("key"))
                );
                testContext.completeNow();
            } catch (Throwable t) {
                testContext.failNow(t);
            }
        });
    }

    private AdminUser getAdminUser(String key) {
        return Instancio.of(AdminUser.class)
                .set(field(AdminUser::getKey), key)
                .set(field(AdminUser::getRoles), new HashSet<>(List.of(Role.ADMINISTRATOR)))
                .create();
    }
}
