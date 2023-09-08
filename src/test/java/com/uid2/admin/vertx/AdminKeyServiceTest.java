package com.uid2.admin.vertx;

import com.uid2.admin.auth.AdminUser;
import com.uid2.admin.vertx.service.AdminKeyService;
import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.auth.Role;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.VertxTestContext;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.instancio.Select.*;
import static org.junit.jupiter.api.Assertions.*;

public class AdminKeyServiceTest extends ServiceTestBase {
    private static final String KEY_PREFIX = "UID2-A-L-";
    private static final String EXPECTED_ADMIN_KEY = KEY_PREFIX + "abcdef.abcdefabcdefabcdef";
    private static final String EXPECTED_ADMIN_KEY_HASH = "abcdefabcdefabcdefabcdef";
    private static final String EXPECTED_ADMIN_KEY_SALT = "ghijklghijklghijklghijkl";

    @Override
    protected IService createService() {
        this.config.put("admin_key_prefix", KEY_PREFIX);
        return new AdminKeyService(config, auth, writeLock, adminUserStoreWriter, adminUserProvider, keyGenerator, keyHasher, clientKeyStoreWriter, encryptionKeyStoreWriter, keyAclStoreWriter);
    }

    private void checkAdminUserJson(AdminUser expectedAdmin, JsonObject actualAdmin) {
        List<Role> actualRoles = actualAdmin.getJsonArray("roles").stream()
                .map(r -> Role.valueOf((String) r))
                .collect(Collectors.toList());

        assertAll(
                "checkAdminUserJson",
                () -> assertEquals(expectedAdmin.getKey(), actualAdmin.getString("key")),
                () -> assertEquals(expectedAdmin.getKeyHash(), actualAdmin.getString("key_hash")),
                () -> assertEquals(expectedAdmin.getKeySalt(), actualAdmin.getString("key_salt")),
                () -> assertEquals(expectedAdmin.getName(), actualAdmin.getString("name")),
                () -> assertEquals(expectedAdmin.getContact(), actualAdmin.getString("contact")),
                () -> assertEquals(expectedAdmin.getRoles().size(), actualRoles.size()),
                () -> assertTrue(actualRoles.containsAll(expectedAdmin.getRoles())),
                () -> assertEquals(expectedAdmin.isDisabled(), actualAdmin.getBoolean("disabled")));
    }

    @Test
    void addAdminUsesKeyPrefixAndFormattedKeyString(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        AdminUser expectedAdminUser = this.expectedAdminUser();

        post(vertx, String.format("api/admin/add?name=%s&roles=administrator", expectedAdminUser.getName()), "", response -> {
            try {
                HttpResponse<Buffer> httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();

                assertAll(
                        "addAdminUsesKeyPrefixAndFormattedKeyString",
                        () -> assertTrue(response.succeeded()),
                        () -> assertNotNull(result),
                        () -> checkAdminUserJson(expectedAdminUser, result));

                testContext.completeNow();
            } catch (Throwable t) {
                testContext.failNow(t);
            }
        });
    }

    private AdminUser expectedAdminUser() {
        return Instancio.of(AdminUser.class)
                .set(field(AdminUser::getKey), EXPECTED_ADMIN_KEY)
                .set(field(AdminUser::getKeyHash), EXPECTED_ADMIN_KEY_HASH)
                .set(field(AdminUser::getKeySalt), EXPECTED_ADMIN_KEY_SALT)
                .set(field(AdminUser::getName), "test")
                .set(field(AdminUser::getContact), "test")
                .set(field(AdminUser::getRoles), new HashSet<>(List.of(Role.ADMINISTRATOR)))
                .set(field(AdminUser::isDisabled), false)
                .create();
    }
}
