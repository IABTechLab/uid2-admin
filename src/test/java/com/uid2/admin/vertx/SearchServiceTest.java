package com.uid2.admin.vertx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uid2.admin.auth.AdminUser;
import com.uid2.admin.legacy.LegacyClientKey;
import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.service.SearchService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.auth.ClientKey;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.auth.Role;
import com.uid2.shared.secret.KeyHashResult;
import com.uid2.shared.secret.KeyHasher;
import com.uid2.shared.util.Mapper;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class SearchServiceTest extends ServiceTestBase {
    private static final ObjectMapper OBJECT_MAPPER = Mapper.getInstance();

    private final String searchUrl = "api/search";

    @Override
    protected IService createService() {
        return new SearchService(auth, clientKeyProvider, operatorKeyProvider, adminUserProvider);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMINISTRATOR"}, mode = EnumSource.Mode.EXCLUDE)
    void searchAsNonAdminFails(Role role, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(role);
        post(vertx, testContext, searchUrl, "1234567", expectHttpStatus(testContext, 401));
    }

    @Test
    void searchAsAdminPasses(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);
        post(vertx, testContext, searchUrl, "123456", response -> {
            assertEquals(200, response.statusCode());
            testContext.completeNow();
        });
    }

    @Test
    void searchWithoutRoleFails(Vertx vertx, VertxTestContext testContext) {
        post(vertx, testContext, searchUrl, "1234567", expectHttpStatus(testContext, 401));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "aa", "aaa", "aaaa", "aaaaa"})
    void searchWithShortQueryStringReturns400Error(String parameter, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);
        post(vertx, testContext, "api/search", parameter, response -> {
            assertAll(
                    "searchWithShortQueryStringReturns400Error",
                    () -> assertEquals(400, response.statusCode()),
                    () -> assertEquals("{\"message\":\"Parameter too short. Must be 6 or more characters.\",\"status\":\"error\"}", response.bodyAsString())
            );
            testContext.completeNow();
        });
    }

    @ParameterizedTest
    @MethodSource("searchByClientKeyNotFound")
    void searchByClientKeyNotFound(Map<String, LegacyClientKey> clientKeys, Map<String, OperatorKey> operatorKeys, AdminUser[] adminUsers, String searchString, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setClientKeys(clientKeys);
        setOperatorKeys(operatorKeys);
        setAdminUsers(adminUsers);

        post(vertx, testContext, searchUrl, searchString, response -> {
            JsonObject result = response.bodyAsJsonObject();
            JsonArray foundClientKeys = result.getJsonArray("ClientKeys");
            JsonArray foundOperatorKeys = result.getJsonArray("OperatorKeys");
            JsonArray foundAdminKeys = result.getJsonArray("AdministratorKeys");

            assertAll(
                    "searchByClientKeyNotFound",
                    () -> assertEquals(0, foundClientKeys.size()),
                    () -> assertEquals(0, foundOperatorKeys.size()),
                    () -> assertEquals(0, foundAdminKeys.size())
            );

            testContext.completeNow();
        });
    }

    private static Stream<Arguments> searchByClientKeyNotFound() {
        Map<String, LegacyClientKey> clientKeys = getClientKeys();
        Map<String, OperatorKey> operatorKeys = getOperatorKeys();
        AdminUser[] adminUsers = getAdminUsers();

        String key = new ArrayList<>(clientKeys.keySet()).get(1);
        return Stream.of(
                Arguments.of(clientKeys, operatorKeys, adminUsers, key.toLowerCase()),
                Arguments.of(clientKeys, operatorKeys, adminUsers, key.toUpperCase())
        );
    }

    @Test
    void searchClientKeyFindsKey(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);
        Map<String, LegacyClientKey> clientKeys = getClientKeys();

        setClientKeys(clientKeys);
        String expectedPlaintextClientKey = "UID2-C-L-999-fCXrMM.fsR3mDqAXELtWWMS+xG1s7RdgRTMqdOH2qaAo=";
        ClientKey expectedClientKey = clientKeys.get(expectedPlaintextClientKey).toClientKey();
        post(vertx, testContext, searchUrl, expectedPlaintextClientKey, response -> {
            JsonObject result = response.bodyAsJsonObject();
            JsonArray foundKeys = result.getJsonArray("ClientKeys");
            ClientKey clientKey = OBJECT_MAPPER.readValue(foundKeys.getJsonObject(0).toString(), ClientKey.class);

            assertAll(
                    "searchClientKeyFindsKey",
                    () -> assertEquals(1, foundKeys.size()),
                    () -> assertEquals(expectedClientKey, clientKey)
            );
            testContext.completeNow();
        });
    }

    @Test
    void searchClientKeyByHashFindsKey(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);
        Map<String, LegacyClientKey> clientKeys = getClientKeys();

        setClientKeys(clientKeys);
        String expectedPlaintextClientKey = "UID2-C-L-999-fCXrMM.fsR3mDqAXELtWWMS+xG1s7RdgRTMqdOH2qaAo=";
        ClientKey expectedClientKey = clientKeys.get(expectedPlaintextClientKey).toClientKey();
        post(vertx, testContext, searchUrl, expectedClientKey.getKeyHash(), response -> {
            JsonObject result = response.bodyAsJsonObject();
            JsonArray foundKeys = result.getJsonArray("ClientKeys");
            ClientKey clientKey = OBJECT_MAPPER.readValue(foundKeys.getJsonObject(0).toString(), ClientKey.class);

            assertAll(
                    "searchClientKeyByHashFindsKey",
                    () -> assertEquals(1, foundKeys.size()),
                    () -> assertEquals(expectedClientKey, clientKey)
            );
            testContext.completeNow();
        });
    }

    @Test
    void searchOperatorKeyFindsKey(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);
        Map<String, OperatorKey> operatorKeys = getOperatorKeys();

        setOperatorKeys(operatorKeys);
        String expectedPlaintextOperatorKey = "UID2-O-L-999-dp9Dt0.JVoGpynN4J8nMA7FxmzsavxJa8B9H74y9xdEE=";
        OperatorKey expectedOperatorKey = operatorKeys.get(expectedPlaintextOperatorKey);
        post(vertx, testContext, searchUrl, expectedPlaintextOperatorKey, response -> {
            JsonObject result = response.bodyAsJsonObject();
            JsonArray foundKeys = result.getJsonArray("OperatorKeys");
            OperatorKey operatorKey = OBJECT_MAPPER.readValue(foundKeys.getJsonObject(0).toString(), OperatorKey.class);

            assertAll(
                    "searchOperatorKeyFindsKey",
                    () -> assertEquals(1, foundKeys.size()),
                    () -> assertEquals(expectedOperatorKey, operatorKey)
            );
            testContext.completeNow();
        });
    }

    @Test
    void searchOperatorKeyByHashFindsKey(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);
        Map<String, OperatorKey> operatorKeys = getOperatorKeys();

        setOperatorKeys(operatorKeys);
        String expectedPlaintextOperatorKey = "UID2-O-L-999-dp9Dt0.JVoGpynN4J8nMA7FxmzsavxJa8B9H74y9xdEE=";
        OperatorKey expectedOperatorKey = operatorKeys.get(expectedPlaintextOperatorKey);
        post(vertx, testContext, searchUrl, expectedOperatorKey.getKeyHash(), response -> {
            JsonObject result = response.bodyAsJsonObject();
            JsonArray foundKeys = result.getJsonArray("OperatorKeys");
            OperatorKey operatorKey = OBJECT_MAPPER.readValue(foundKeys.getJsonObject(0).toString(), OperatorKey.class);

            assertAll(
                    "searchOperatorKeyByHashFindsKey",
                    () -> assertEquals(1, foundKeys.size()),
                    () -> assertEquals(expectedOperatorKey, operatorKey)
            );
            testContext.completeNow();
        });
    }

    @Test
    void searchAdminUserFindsKey(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);
        AdminUser[] adminUsers = getAdminUsers();

        setAdminUsers(adminUsers);
        post(vertx, testContext, searchUrl, "UID2-A-L-WYHV5i.Se6uQDk/N1KsKk4T8CWAFSU5oyObkCes9yFG8=", response -> {
            JsonObject result = response.bodyAsJsonObject();
            JsonArray foundKeys = result.getJsonArray("AdministratorKeys");
            JsonObject adminUser = foundKeys.getJsonObject(0);

            assertAll(
                    "searchAdminUserFindsKey",
                    () -> assertEquals(1, foundKeys.size()),
                    () -> assertAdminUser(adminUsers[0], adminUser)
            );
            testContext.completeNow();
        });
    }

    @Test
    void searchAdminUserByHashFindsKey(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);
        AdminUser[] adminUsers = getAdminUsers();

        setAdminUsers(adminUsers);
        post(vertx, testContext, searchUrl, adminUsers[0].getKeyHash(), response -> {
            JsonObject result = response.bodyAsJsonObject();
            JsonArray foundKeys = result.getJsonArray("AdministratorKeys");
            JsonObject adminUser = foundKeys.getJsonObject(0);

            assertAll(
                    "searchAdminUserByHashFindsKey",
                    () -> assertEquals(1, foundKeys.size()),
                    () -> assertAdminUser(adminUsers[0], adminUser)
            );
            testContext.completeNow();
        });
    }

    @ParameterizedTest
    @MethodSource("searchByClientSecretSuccess")
    void searchByClientSecretSuccess(Map<String, LegacyClientKey> clientKeys, Map<String, OperatorKey> operatorKeys, AdminUser[] adminUsers, String searchString, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setClientKeys(clientKeys);
        setOperatorKeys(operatorKeys);
        setAdminUsers(adminUsers);

        String expectedSecret = "FsD4bvtjMkeTonx6HvQp6u0EiI1ApGH4pIZzZ5P7UcQ=";
        ClientKey expectedClientKey = clientKeys.values().stream()
                .filter(c -> expectedSecret.equals(c.getSecret()))
                .collect(Collectors.toList())
                .get(0)
                .toClientKey();
        post(vertx, testContext, searchUrl, searchString, response -> {
            JsonObject result = response.bodyAsJsonObject();
            JsonArray foundKeys = result.getJsonArray("ClientKeys");
            ClientKey clientKey = OBJECT_MAPPER.readValue(foundKeys.getJsonObject(0).toString(), ClientKey.class);

            assertAll(
                    "searchByClientSecretSuccess",
                    () -> assertEquals(1, foundKeys.size()),
                    () -> assertEquals(expectedClientKey, clientKey)
            );
            testContext.completeNow();
        });
    }

    private static Stream<Arguments> searchByClientSecretSuccess() {
        Map<String, LegacyClientKey> clientKeys = getClientKeys();
        Map<String, OperatorKey> operatorKeys = getOperatorKeys();
        AdminUser[] adminUsers = getAdminUsers();

        String secret = new ArrayList<>(clientKeys.values()).get(1).getSecret();
        return Stream.of(
                Arguments.of(clientKeys, operatorKeys, adminUsers, secret.substring(0, 8)),
                Arguments.of(clientKeys, operatorKeys, adminUsers, secret.substring(secret.length() - 8)),
                Arguments.of(clientKeys, operatorKeys, adminUsers, secret.substring(10, 20)),
                Arguments.of(clientKeys, operatorKeys, adminUsers, secret)
        );
    }

    private static KeyHashResult hashKeys(String key) {
        KeyHasher keyHasher = new KeyHasher();
        return keyHasher.hashKey(key);
    }

    private static Map<String, LegacyClientKey> getClientKeys() {
        Map<String, Map.Entry<String, String>> plaintextClientKeyAndSecretMap = Map.of(
                "UID2-C-L-999-fCXrMM.fsR3mDqAXELtWWMS+xG1s7RdgRTMqdOH2qaAo=", new AbstractMap.SimpleEntry("DzBzbjTJcYL0swDtFs2krRNu+g1Eokm2tBU4dEuD0Wk=", "UID2-C-L-999-fCXrM"),
                "LOCALbGlvbnVuZGVybGluZXdpbmRzY2FyZWRzb2Z0ZGVzZXI=", new AbstractMap.SimpleEntry("c3RlZXBzcGVuZHNsb3BlZnJlcXVlbnRseWRvd2lkZWM=", "LOCALbGlvb"),
                "UID2-C-L-123-t32pCM.5NCX1E94UgOd2f8zhsKmxzCoyhXohHYSSWR8U=", new AbstractMap.SimpleEntry("FsD4bvtjMkeTonx6HvQp6u0EiI1ApGH4pIZzZ5P7UcQ=", "UID2-C-L-123-t32pC")
        );
        return plaintextClientKeyAndSecretMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> createClientKey(entry.getKey(), entry.getValue().getKey(), entry.getValue().getValue())
                ));
    }

    private static LegacyClientKey createClientKey(String key, String secret, String keyId) {
        KeyHashResult keyHashResult = hashKeys(key);
        return new LegacyClientKey(key, keyHashResult.getHash(), keyHashResult.getSalt(), secret, key, Instant.now(), Set.of(), 3, keyId);
    }

    private static Map<String, OperatorKey> getOperatorKeys() {
        Map<String, String> plaintextOperatorKeys = Map.of(
                "UID2-O-L-999-dp9Dt0.JVoGpynN4J8nMA7FxmzsavxJa8B9H74y9xdEE=", "UID2-O-L-999-dp9Dt",
                "OPLCLAjLRWcVlCDl9+BbwR38gzxYdiWFa751ynWLuI7JU4iA=", "OPLCLAjLRWc",
                "UID2-O-L-123-Xt/ght.6tODU8mmodEtI3J67LW3vcX50LOsQR4oqMMFk=", "UID2-O-L-123-Xt/gh"
        );
        return plaintextOperatorKeys.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> createOperatorKey(entry.getKey(), entry.getValue())
                ));
    }

    private static OperatorKey createOperatorKey(String key, String keyId) {
        KeyHashResult keyHashResult = hashKeys(key);
        return new OperatorKey(keyHashResult.getHash(), keyHashResult.getSalt(), "name", "contact", "protocol", Instant.now().getEpochSecond(), false, keyId);
    }

    private static AdminUser[] getAdminUsers() {
        AdminUser[] adminUsers = {
                createAdminUser("UID2-A-L-WYHV5i.Se6uQDk/N1KsKk4T8CWAFSU5oyObkCes9yFG8=", "UID2-A-L-WYHV5"),
                createAdminUser("ADLCLWYHV5iSe6uQDk/N1KsKk4T8CWAFSU5oyObkCes9yFG8=", "ADLCLWYHV5")
        };
        return adminUsers;
    }

    private static AdminUser createAdminUser(String key, String keyId) {
        KeyHashResult keyHashResult = hashKeys(key);
        return new AdminUser(key, keyHashResult.getHash(), keyHashResult.getSalt(), "name", "contact", Instant.now().toEpochMilli(), Set.of(), false, keyId);
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
