package com.uid2.admin.vertx;

import com.uid2.admin.auth.AdminUser;
import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.service.SearchService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.auth.ClientKey;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.auth.Role;
import com.uid2.shared.secret.KeyHashResult;
import com.uid2.shared.secret.KeyHasher;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.VertxTestContext;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.instancio.Select.field;
import static org.junit.jupiter.api.Assertions.*;

public class SearchServiceTest extends ServiceTestBase {
    private final static String searchUrl = "api/search";

    @Override
    protected IService createService() {
        return new SearchService(auth, clientKeyProvider, operatorKeyProvider, adminUserProvider);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMINISTRATOR"}, mode = EnumSource.Mode.EXCLUDE)
    void searchAsNonAdminFails(Role role, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(role);
        post(vertx, searchUrl, "1234567", expectHttpError(testContext, 401));
    }

    @Test
    void searchAsAdminPasses(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);
        post(vertx, searchUrl, "123456", response -> {
            assertAll(
                    "searchAsAdminPasses",
                    () -> assertTrue(response.succeeded()),
                    () -> assertEquals(200, response.result().statusCode())
            );
            testContext.completeNow();
        });
    }

    @Test
    void searchWithoutRoleFails(Vertx vertx, VertxTestContext testContext) {
        post(vertx, searchUrl, "1234567", expectHttpError(testContext, 401));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "aa", "aaa", "aaaa", "aaaaa"})
    void searchWithShortQueryStringReturns400Error(String parameter, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);
        post(vertx, "api/search", parameter, response -> {
            try {
                assertAll(
                        "searchWithShortQueryStringReturns400Error",
                        () -> assertTrue(response.succeeded()),
                        () -> assertEquals(400, response.result().statusCode()),
                        () -> assertEquals("{\"message\":\"Parameter too short. Must be 6 or more characters.\",\"status\":\"error\"}", response.result().bodyAsString())
                );
                testContext.completeNow();
            } catch (Throwable t) {
                testContext.failNow(t);
            }
        });
    }

    @ParameterizedTest
    @MethodSource("searchByClientKeyNotFound")
    void searchByClientKeyNotFound(ClientKey[] clientKeys, OperatorKey[] operatorKeys, AdminUser[] adminUsers, String searchString, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setClientKeys(clientKeys);
        setOperatorKeys(operatorKeys);
        setAdminUsers(adminUsers);

        post(vertx, searchUrl, searchString, response -> {
            try {

                HttpResponse<Buffer> httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();
                JsonArray foundClientKeys = result.getJsonArray("ClientKeys");
                JsonArray foundOperatorKeys = result.getJsonArray("OperatorKeys");
                JsonArray foundAdminKeys = result.getJsonArray("AdministratorKeys");

                assertAll(
                        "searchByClientKeyNotFound",
                        () -> assertTrue(response.succeeded()),
                        () -> assertEquals(0, foundClientKeys.size()),
                        () -> assertEquals(0, foundOperatorKeys.size()),
                        () -> assertEquals(0, foundAdminKeys.size())
                );

                testContext.completeNow();
            } catch (Throwable ex) {
                testContext.failNow(ex);
            }
        });
    }

    @Test
    void searchClientKeyFindsKey(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);
        ClientKey[] clientKeys = getClientKeys();

        setClientKeys(clientKeys);
        post(vertx, searchUrl, "UID2-C-L-999-fCXrMM.fsR3mDqAXELtWWMS+xG1s7RdgRTMqdOH2qaAo=", response -> {
            try {
                HttpResponse<Buffer> httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();
                JsonArray foundKeys = result.getJsonArray("ClientKeys");
                JsonObject client = foundKeys.getJsonObject(0);

                assertAll(
                        "searchClientKeyFindsKey",
                        () -> assertTrue(response.succeeded()),
                        () -> assertEquals(1, foundKeys.size()),
                        () -> assertClientKey(clientKeys[0], client)
                );
                testContext.completeNow();
            } catch (Throwable t) {
                testContext.failNow(t);
            }
        });
    }

    @Test
    void searchClientKeyByHashFindsKey(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);
        ClientKey[] clientKeys = getClientKeys();

        setClientKeys(clientKeys);
        post(vertx, searchUrl, clientKeys[0].getKeyHash(), response -> {
            try {
                HttpResponse<Buffer> httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();
                JsonArray foundKeys = result.getJsonArray("ClientKeys");
                JsonObject client = foundKeys.getJsonObject(0);

                assertAll(
                        "searchClientKeyByHashFindsKey",
                        () -> assertTrue(response.succeeded()),
                        () -> assertEquals(1, foundKeys.size()),
                        () -> assertClientKey(clientKeys[0], client)
                );
                testContext.completeNow();
            } catch (Throwable t) {
                testContext.failNow(t);
            }
        });
    }

    @Test
    void searchOperatorKeyFindsKey(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);
        OperatorKey[] operatorKeys = getOperatorKeys();

        setOperatorKeys(operatorKeys);
        post(vertx, searchUrl, "UID2-O-L-999-dp9Dt0.JVoGpynN4J8nMA7FxmzsavxJa8B9H74y9xdEE=", response -> {
            try {
                HttpResponse<Buffer> httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();
                JsonArray foundKeys = result.getJsonArray("OperatorKeys");
                JsonObject operatorKey = foundKeys.getJsonObject(0);

                assertAll(
                        "searchOperatorKeyFindsKey",
                        () -> assertTrue(response.succeeded()),
                        () -> assertEquals(1, foundKeys.size()),
                        () -> assertOperatorKey(operatorKeys[0], operatorKey)
                );
                testContext.completeNow();
            } catch (Throwable t) {
                testContext.failNow(t);
            }
        });
    }

    @Test
    void searchOperatorKeyByHashFindsKey(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);
        OperatorKey[] operatorKeys = getOperatorKeys();

        setOperatorKeys(operatorKeys);
        post(vertx, searchUrl, operatorKeys[0].getKeyHash(), response -> {
            try {
                HttpResponse<Buffer> httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();
                JsonArray foundKeys = result.getJsonArray("OperatorKeys");
                JsonObject operatorKey = foundKeys.getJsonObject(0);

                assertAll(
                        "searchOperatorKeyByHashFindsKey",
                        () -> assertTrue(response.succeeded()),
                        () -> assertEquals(1, foundKeys.size()),
                        () -> assertOperatorKey(operatorKeys[0], operatorKey)
                );
                testContext.completeNow();
            } catch (Throwable t) {
                testContext.failNow(t);
            }
        });
    }

    @Test
    void searchAdminUserFindsKey(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);
        AdminUser[] adminUsers = getAdminUsers();

        setAdminUsers(adminUsers);
        post(vertx, searchUrl, "UID2-A-L-WYHV5i.Se6uQDk/N1KsKk4T8CWAFSU5oyObkCes9yFG8=", response -> {
            try {
                HttpResponse<Buffer> httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();
                JsonArray foundKeys = result.getJsonArray("AdministratorKeys");
                JsonObject adminUser = foundKeys.getJsonObject(0);

                assertAll(
                        "searchAdminUserFindsKey",
                        () -> assertTrue(response.succeeded()),
                        () -> assertEquals(1, foundKeys.size()),
                        () -> assertAdminUser(adminUsers[0], adminUser)
                );
                testContext.completeNow();
            } catch (Throwable t) {
                testContext.failNow(t);
            }
        });
    }

    @Test
    void searchAdminUserByHashFindsKey(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);
        AdminUser[] adminUsers = getAdminUsers();

        setAdminUsers(adminUsers);
        post(vertx, searchUrl, adminUsers[0].getKeyHash(), response -> {
            try {
                HttpResponse<Buffer> httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();
                JsonArray foundKeys = result.getJsonArray("AdministratorKeys");
                JsonObject adminUser = foundKeys.getJsonObject(0);

                assertAll(
                        "searchAdminUserByHashFindsKey",
                        () -> assertTrue(response.succeeded()),
                        () -> assertEquals(1, foundKeys.size()),
                        () -> assertAdminUser(adminUsers[0], adminUser)
                );
                testContext.completeNow();
            } catch (Throwable t) {
                testContext.failNow(t);
            }
        });
    }

    private static Stream<Arguments> searchByClientKeyNotFound() {
        ClientKey[] clientKeys = getClientKeys();
        OperatorKey[] operatorKeys = getOperatorKeys();
        AdminUser[] adminUsers = getAdminUsers();
        String key = clientKeys[1].getKey();
        return Stream.of(
                Arguments.of(clientKeys, operatorKeys, adminUsers, key.toLowerCase()),
                Arguments.of(clientKeys, operatorKeys, adminUsers, key.toUpperCase())
        );
    }

    @ParameterizedTest
    @MethodSource("searchByClientSecretSuccess")
    void searchByClientSecretSuccess(ClientKey[] clientKeys, OperatorKey[] operatorKeys, AdminUser[] adminUsers, String searchString, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setClientKeys(clientKeys);
        setOperatorKeys(operatorKeys);
        setAdminUsers(adminUsers);

        post(vertx, searchUrl, searchString, response -> {
            try {
                HttpResponse<Buffer> httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();
                JsonArray foundKeys = result.getJsonArray("ClientKeys");
                JsonObject client = foundKeys.getJsonObject(0);

                assertAll(
                        "searchByClientSecretSuccess",
                        () -> assertTrue(response.succeeded()),
                        () -> assertEquals(1, foundKeys.size()),
                        () -> assertClientKey(clientKeys[1], client)
                );
                testContext.completeNow();
            } catch (Throwable ex) {
                testContext.failNow(ex);
            }
        });
    }

    private static Stream<Arguments> searchByClientSecretSuccess() {
        ClientKey[] clientKeys = getClientKeys();
        OperatorKey[] operatorKeys = getOperatorKeys();
        AdminUser[] adminUsers = getAdminUsers();

        String secret = clientKeys[1].getSecret();
        return Stream.of(
                Arguments.of(clientKeys, operatorKeys, adminUsers, secret.substring(0, 8)),
                Arguments.of(clientKeys, operatorKeys, adminUsers, secret.substring(secret.length() - 8)),
                Arguments.of(clientKeys, operatorKeys, adminUsers, secret.substring(10, 20)),
                Arguments.of(clientKeys, operatorKeys, adminUsers, secret)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"UID2-O-L-999-dp9", "dp9Dt0", "9Dt0.JVoG", "xJa8B9H74y9", "74y9xdEE="})
    void searchBySecrectSpecialCharactersSuccess(String searchString, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        ClientKey[] clientKeys = Instancio.ofList(ClientKey.class)
                .size(1)
                .set(field(ClientKey::getSecret), "UID2-O-L-999-dp9Dt0.JVoGpynN4J8nMA7FxmzsavxJa8B9H74y9xdEE=")
                .create().toArray(new ClientKey[0]);
        setClientKeys(clientKeys);

        post(vertx, searchUrl, searchString, response -> {
            try {
                HttpResponse<Buffer> httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();
                JsonArray foundKeys = result.getJsonArray("ClientKeys");
                JsonObject clientKey = foundKeys.getJsonObject(0);

                assertAll(
                        "",
                        () -> assertTrue(response.succeeded()),
                        () -> assertEquals(1, foundKeys.size()),
                        () -> assertClientKey(clientKeys[0], clientKey)
                );
                testContext.completeNow();
            } catch (Throwable ex) {
                testContext.failNow(ex);
            }
        });

    }

    private static KeyHashResult hashKeys(String key) {
        KeyHasher keyHasher = new KeyHasher();
        return keyHasher.hashKey(key);
    }

    private static ClientKey[] getClientKeys() {
        ClientKey[] clientKeys = {
                createClientKey("UID2-C-L-999-fCXrMM.fsR3mDqAXELtWWMS+xG1s7RdgRTMqdOH2qaAo=", "DzBzbjTJcYL0swDtFs2krRNu+g1Eokm2tBU4dEuD0Wk="),
                createClientKey("LOCALbGlvbnVuZGVybGluZXdpbmRzY2FyZWRzb2Z0ZGVzZXI=", "c3RlZXBzcGVuZHNsb3BlZnJlcXVlbnRseWRvd2lkZWM="),
                createClientKey("UID2-C-L-123-t32pCM.5NCX1E94UgOd2f8zhsKmxzCoyhXohHYSSWR8U=", "FsD4bvtjMkeTonx6HvQp6u0EiI1ApGH4pIZzZ5P7UcQ=")};
        return clientKeys;
    }

    private static ClientKey createClientKey(String key, String secret) {
        KeyHashResult keyHashResult = hashKeys(key);
        return new ClientKey(key, keyHashResult.getHash(), keyHashResult.getSalt(), secret, key, Instant.now(), Set.of(), 3);
    }

    private static OperatorKey[] getOperatorKeys() {
        OperatorKey[] operatorKeys = {
                createOperatorKey("UID2-O-L-999-dp9Dt0.JVoGpynN4J8nMA7FxmzsavxJa8B9H74y9xdEE="),
                createOperatorKey("OPLCLAjLRWcVlCDl9+BbwR38gzxYdiWFa751ynWLuI7JU4iA="),
                createOperatorKey("UID2-O-L-123-Xt/ght.6tODU8mmodEtI3J67LW3vcX50LOsQR4oqMMFk=")
        };
        return operatorKeys;
    }

    private static OperatorKey createOperatorKey(String key) {
        KeyHashResult keyHashResult = hashKeys(key);
        return new OperatorKey(key, keyHashResult.getHash(), keyHashResult.getSalt(), "name", "contact", "protocol", Instant.now().toEpochMilli(), false);
    }

    private static AdminUser[] getAdminUsers() {
        AdminUser[] adminUsers = {
                createAdminUser("UID2-A-L-WYHV5i.Se6uQDk/N1KsKk4T8CWAFSU5oyObkCes9yFG8="),
                createAdminUser("ADLCLWYHV5iSe6uQDk/N1KsKk4T8CWAFSU5oyObkCes9yFG8=")
        };
        return adminUsers;
    }

    private static AdminUser createAdminUser(String key) {
        KeyHashResult keyHashResult = hashKeys(key);
        return new AdminUser(key, keyHashResult.getHash(), keyHashResult.getSalt(), "name", "contact", Instant.now().toEpochMilli(), Set.of(), false);
    }

    private static void assertClientKey(ClientKey expected, JsonObject actual) {
        assertEquals(expected.getKey(), actual.getString("key"));
        assertEquals(expected.getSecret(), actual.getString("secret"));
        assertEquals(expected.getName(), actual.getString("name"));
        assertEquals(expected.getContact(), actual.getString("contact"));
        assertEquals(expected.getSiteId(), actual.getInteger("site_id"));
        assertEquals(expected.getKeyHash(), actual.getString("key_hash"));
        assertEquals(expected.getKeySalt(), actual.getString("key_salt"));

        assertRoles(expected.getRoles(), actual.getJsonArray("roles"));
    }

    private static void assertOperatorKey(OperatorKey expected, JsonObject actual) {
        assertEquals(expected.getKey(), actual.getString("key"));
        assertEquals(expected.getName(), actual.getString("name"));
        assertEquals(expected.getContact(), actual.getString("contact"));
        assertEquals(expected.getProtocol(), actual.getString("protocol"));
        assertEquals(expected.getSiteId(), actual.getInteger("site_id"));
        assertEquals(expected.getOperatorType().toString(), actual.getString("operator_type"));
        assertEquals(expected.getKeyHash(), actual.getString("key_hash"));
        assertEquals(expected.getKeySalt(), actual.getString("key_salt"));

        assertRoles(expected.getRoles(), actual.getJsonArray("roles"));
    }

    private static void assertAdminUser(AdminUser expected, JsonObject actual) {
        assertEquals(expected.getKey(), actual.getString("key"));
        assertEquals(expected.getName(), actual.getString("name"));
        assertEquals(expected.getContact(), actual.getString("contact"));
        assertEquals(expected.getSiteId(), actual.getInteger("site_id"));
        assertEquals(expected.getKeyHash(), actual.getString("key_hash"));
        assertEquals(expected.getKeySalt(), actual.getString("key_salt"));

        assertRoles(expected.getRoles(), actual.getJsonArray("roles"));
    }

    private static void assertRoles(Set<Role> expectedRoles, JsonArray actualRoles) {
        List<Role> actualRolesList = actualRoles.stream()
                .map(r -> Role.valueOf((String) r))
                .collect(Collectors.toList());
        assertEquals(expectedRoles.size(), actualRolesList.size());
        for (Role role : expectedRoles) {
            assertTrue(actualRolesList.contains(role));
        }
    }
}
