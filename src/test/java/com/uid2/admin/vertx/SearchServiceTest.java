package com.uid2.admin.vertx;

import com.uid2.admin.auth.AdminUser;
import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.service.SearchService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.auth.ClientKey;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.auth.Role;
import io.vertx.core.Vertx;
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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.instancio.Select.field;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SearchServiceTest extends ServiceTestBase {
    private final static String searchUrl = "api/search?keyOrSecret=";

    @Override
    protected IService createService() {
        return new SearchService(auth, clientKeyProvider, operatorKeyProvider, adminUserProvider);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMINISTRATOR"}, mode = EnumSource.Mode.EXCLUDE)
    void searchAsNonAdminFails(Role role, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(role);
        get(vertx, searchUrl, expectHttpError(testContext, 401));
    }

    @Test
    void searchAsAdminPasses(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);
        get(vertx, searchUrl + "123456", response -> {
            assertTrue(response.succeeded());
            HttpResponse httpResponse = response.result();
            assertEquals(200, httpResponse.statusCode());
            testContext.completeNow();
        });
    }

    @Test
    void searchWithoutRoleFails(Vertx vertx, VertxTestContext testContext) {
        get(vertx, searchUrl, expectHttpError(testContext, 401));
    }

    @Test
    void searchWithoutQueryStringReturns400Error(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);
        get(vertx, "api/search?invalid=123", response -> {
            assertTrue(response.succeeded());
            HttpResponse httpResponse = response.result();
            assertEquals(400, httpResponse.statusCode());
            String body = httpResponse.bodyAsString();
            assertEquals("{\"message\":\"Invalid parameters\",\"status\":\"error\"}", body);
            testContext.completeNow();
        });
    }

    @ParameterizedTest
    @MethodSource("shortParameterValues")
    void searchWithShortQueryStringReturns400Error(String parameter, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);
        get(vertx, String.format("api/search?keyOrSecret=$s", parameter), response -> {
            assertTrue(response.succeeded());
            HttpResponse httpResponse = response.result();
            assertEquals(400, httpResponse.statusCode());
            String body = httpResponse.bodyAsString();
            assertEquals("{\"message\":\"Parameter too short\",\"status\":\"error\"}", body);
            testContext.completeNow();
        });
    }

    private static Stream<Arguments> shortParameterValues() {
        return Stream.of(
                Arguments.of("a"),
                Arguments.of("aa"),
                Arguments.of("aaa"),
                Arguments.of("aaaa"),
                Arguments.of("aaaaa"),
                Arguments.of("aaaaaa")
        );
    }

    @Test
    void searchClientKeyFindsKey(Vertx vertx, VertxTestContext testContext) throws InterruptedException {
        fakeAuth(Role.ADMINISTRATOR);
        ClientKey[] clientKeys = new ClientKey[3];
        clientKeys[0] = new ClientKey("LOCALfCXrMMfsR3mDqAXELtWWMS+xG1s7RdgRTMqdOH2qaAo=", "DzBzbjTJcYL0swDtFs2krRNu+g1Eokm2tBU4dEuD0Wk=")
                .withNameAndContact("MegaTest Client")
                .withRoles(Role.OPERATOR)
                .withSiteId(5);
        clientKeys[1] = new ClientKey("LOCALt32pCM5NCX1E94UgOd2f8zhsKmxzCoyhXohHYSSWR8U=", "FsD4bvtjMkeTonx6HvQp6u0EiI1ApGH4pIZzZ5P7UcQ=")
                .withNameAndContact("MegaTest Client 2")
                .withRoles(Role.MAPPER)
                .withSiteId(5);
        clientKeys[2] = new ClientKey("LOCALH8VwqXl2G4TCuUWYAqdqkeG/UqtFoPEoXirKn4kHWxc=", "NcMgi6Y8C80SlxvV7pYlfcvEIo+2b0508tYQ3pKK8HM=")
                .withNameAndContact("TestCorp Client")
                .withRoles(Role.OPERATOR)
                .withSiteId(5);

        setClientKeys(clientKeys);
        get(vertx, searchUrl + "fCXrMMfs", response -> {
            try {
                assertTrue(response.succeeded());
                HttpResponse httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();
                JsonArray foundKeys = result.getJsonArray("ClientKeys");
                assertEquals(1, foundKeys.size());
                JsonObject client = foundKeys.getJsonObject(0);
                assertClientKey(clientKeys[0], client);
                testContext.completeNow();
            } catch (Throwable t) {
                testContext.failNow(t);
            }
        });
    }

    @ParameterizedTest
    @MethodSource("searchByClientKey")
    void searchByClientKeySuccess(ClientKey[] clientKeys, OperatorKey[] operatorKeys, AdminUser[] adminUsers, String searchString, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setClientKeys(clientKeys);
        setOperatorKeys(operatorKeys);
        setAdminUsers(adminUsers);

        get(vertx, searchUrl + searchString, response -> {
            try {
                assertTrue(response.succeeded());
                HttpResponse httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();
                JsonArray foundKeys = result.getJsonArray("ClientKeys");
                assertEquals(1, foundKeys.size());
                JsonObject client = foundKeys.getJsonObject(0);
                assertClientKey(clientKeys[1], client);
                testContext.completeNow();
            } catch (Throwable ex) {
                testContext.failNow(ex);
            }
        });
    }


    private static Stream<Arguments> searchByClientKey() {
        ClientKey[] clientKeys = getClientKeys();
        OperatorKey[] operatorKeys = getOperatorKeys();
        AdminUser[] adminUsers = getAdminUsers();

        String key = clientKeys[1].getKey();
        return Stream.of(
                Arguments.of(clientKeys, operatorKeys, adminUsers, key.substring(0, 8)),
                Arguments.of(clientKeys, operatorKeys, adminUsers, key.substring(key.length() - 8, key.length())),
                Arguments.of(clientKeys, operatorKeys, adminUsers, key.substring(10, 20)),
                Arguments.of(clientKeys, operatorKeys, adminUsers, key)
        );
    }

    @ParameterizedTest
    @MethodSource("searchByClientKeyNotFound")
    void searchByClientKeyNotFound(ClientKey[] clientKeys, OperatorKey[] operatorKeys, AdminUser[] adminUsers, String searchString, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setClientKeys(clientKeys);
        setOperatorKeys(operatorKeys);
        setAdminUsers(adminUsers);

        get(vertx, searchUrl + searchString, response -> {
            try {
                assertTrue(response.succeeded());
                HttpResponse httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();
                JsonArray foundClientKeys = result.getJsonArray("ClientKeys");
                JsonArray foundOperatorKeys = result.getJsonArray("OperatorKeys");
                JsonArray foundAdminKeys = result.getJsonArray("AdministratorKeys");

                assertEquals(0, foundClientKeys.size());
                assertEquals(0, foundOperatorKeys.size());
                assertEquals(0, foundAdminKeys.size());

                testContext.completeNow();
            } catch (Throwable ex) {
                testContext.failNow(ex);
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

        get(vertx, searchUrl + searchString, response -> {
            try {
                assertTrue(response.succeeded());
                HttpResponse httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();
                JsonArray foundKeys = result.getJsonArray("ClientKeys");
                assertEquals(1, foundKeys.size());
                JsonObject client = foundKeys.getJsonObject(0);
                assertClientKey(clientKeys[1], client);
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
                Arguments.of(clientKeys, operatorKeys, adminUsers, secret.substring(secret.length() - 8, secret.length())),
                Arguments.of(clientKeys, operatorKeys, adminUsers, secret.substring(10, 20)),
                Arguments.of(clientKeys, operatorKeys, adminUsers, secret)
        );
    }

    @ParameterizedTest
    @MethodSource("searchByOperatorKeySuccess")
    void searchByOperatorKeySuccess(ClientKey[] clientKeys, OperatorKey[] operatorKeys, AdminUser[] adminUsers, String searchString, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setClientKeys(clientKeys);
        setOperatorKeys(operatorKeys);
        setAdminUsers(adminUsers);

        get(vertx, searchUrl + searchString, response -> {
            try {
                assertTrue(response.succeeded());
                HttpResponse httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();
                JsonArray foundKeys = result.getJsonArray("OperatorKeys");
                assertEquals(1, foundKeys.size());
                JsonObject operator = foundKeys.getJsonObject(0);
                assertOperatorKey(operatorKeys[1], operator);
                testContext.completeNow();
            } catch (Throwable ex) {
                testContext.failNow(ex);
            }
        });
    }

    private static Stream<Arguments> searchByOperatorKeySuccess() {
        ClientKey[] clientKeys = getClientKeys();
        OperatorKey[] operatorKeys = getOperatorKeys();
        AdminUser[] adminUsers = getAdminUsers();

        String key = operatorKeys[1].getKey();
        return Stream.of(
                Arguments.of(clientKeys, operatorKeys, adminUsers, key.substring(0, 8)),
                Arguments.of(clientKeys, operatorKeys, adminUsers, key.substring(key.length() - 8, key.length())),
                Arguments.of(clientKeys, operatorKeys, adminUsers, key.substring(10, 20)),
                Arguments.of(clientKeys, operatorKeys, adminUsers, key)
        );
    }

    @ParameterizedTest
    @MethodSource("searchByAdminKeySuccess")
    void searchByAdminKeySuccess(ClientKey[] clientKeys, OperatorKey[] operatorKeys, AdminUser[] adminUsers, String searchString, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setClientKeys(clientKeys);
        setOperatorKeys(operatorKeys);
        setAdminUsers(adminUsers);

        get(vertx, searchUrl + searchString, response -> {
            try {
                assertTrue(response.succeeded());
                HttpResponse httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();
                JsonArray foundKeys = result.getJsonArray("AdministratorKeys");
                assertEquals(1, foundKeys.size());
                JsonObject operator = foundKeys.getJsonObject(0);
                assertAdminUser(adminUsers[1], operator);
                testContext.completeNow();
            } catch (Throwable ex) {
                testContext.failNow(ex);
            }
        });
    }

    private static Stream<Arguments> searchByAdminKeySuccess() {
        ClientKey[] clientKeys = getClientKeys();
        OperatorKey[] operatorKeys = getOperatorKeys();
        AdminUser[] adminUsers = getAdminUsers();

        String key = adminUsers[1].getKey();
        return Stream.of(
                Arguments.of(clientKeys, operatorKeys, adminUsers, key.substring(0, 8)),
                Arguments.of(clientKeys, operatorKeys, adminUsers, key.substring(key.length() - 8, key.length())),
                Arguments.of(clientKeys, operatorKeys, adminUsers, key.substring(10, 20)),
                Arguments.of(clientKeys, operatorKeys, adminUsers, key)
        );
    }

    @ParameterizedTest
    @MethodSource("searchByAllKeySuccess")
    void searchByAllKeySuccess(ClientKey[] clientKeys, OperatorKey[] operatorKeys, AdminUser[] adminUsers, String searchString, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setClientKeys(clientKeys);
        setOperatorKeys(operatorKeys);
        setAdminUsers(adminUsers);

        get(vertx, searchUrl + searchString, response -> {
            try {
                assertTrue(response.succeeded());
                HttpResponse httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();
                JsonArray foundClientKeys = result.getJsonArray("ClientKeys");
                JsonArray foundOperatorKeys = result.getJsonArray("OperatorKeys");
                JsonArray foundAdminKeys = result.getJsonArray("AdministratorKeys");

                assertEquals(3, foundClientKeys.size());
                assertEquals(3, foundOperatorKeys.size());
                assertEquals(3, foundAdminKeys.size());
                for (int i = 0; i < 3; i++) {
                    JsonObject clientKey = foundClientKeys.getJsonObject(i);
                    assertClientKey(clientKeys[i], clientKey);
                    JsonObject operatorKey = foundOperatorKeys.getJsonObject(i);
                    assertOperatorKey(operatorKeys[i], operatorKey);
                    JsonObject adminKey = foundAdminKeys.getJsonObject(i);
                    assertAdminUser(adminUsers[i], adminKey);
                }
                testContext.completeNow();
            } catch (Throwable ex) {
                testContext.failNow(ex);
            }
        });
    }

    private static Stream<Arguments> searchByAllKeySuccess() {
        ClientKey[] clientKeys = getClientKeys("SHAREDVALUE", "");
        OperatorKey[] operatorKeys = getOperatorKeys("SHAREDVALUE");
        AdminUser[] adminUsers = getAdminUsers("SHAREDVALUE");

        return Stream.of(
                Arguments.of(clientKeys, operatorKeys, adminUsers, "SHAREDVALUE")
        );
    }

    @ParameterizedTest
    @MethodSource("searchByAllClientKeySuccess")
    void searchByAllClientKeySuccess(ClientKey[] clientKeys, OperatorKey[] operatorKeys, AdminUser[] adminUsers, String searchString, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setClientKeys(clientKeys);
        setOperatorKeys(operatorKeys);
        setAdminUsers(adminUsers);

        get(vertx, searchUrl + searchString, response -> {
            try {
                assertTrue(response.succeeded());
                HttpResponse httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();
                JsonArray foundKeys = result.getJsonArray("ClientKeys");

                assertEquals(3, foundKeys.size());
                for (int i = 0; i < 3; i++) {
                    JsonObject clientKey = foundKeys.getJsonObject(i);
                    assertClientKey(clientKeys[i], clientKey);
                }
                testContext.completeNow();
            } catch (Throwable ex) {
                testContext.failNow(ex);
            }
        });
    }

    private static Stream<Arguments> searchByAllClientKeySuccess() {
        ClientKey[] clientKeys = getClientKeys("SHAREDVALUE", "");
        OperatorKey[] operatorKeys = getOperatorKeys();
        AdminUser[] adminUsers = getAdminUsers();

        return Stream.of(
                Arguments.of(clientKeys, operatorKeys, adminUsers, "SHAREDVALUE")
        );
    }

    @ParameterizedTest
    @MethodSource("searchByAllOperatorKeySuccess")
    void searchByAllOperatorKeySuccess(ClientKey[] clientKeys, OperatorKey[] operatorKeys, AdminUser[] adminUsers, String searchString, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setClientKeys(clientKeys);
        setOperatorKeys(operatorKeys);
        setAdminUsers(adminUsers);

        get(vertx, searchUrl + searchString, response -> {
            try {
                assertTrue(response.succeeded());
                HttpResponse httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();
                JsonArray foundKeys = result.getJsonArray("OperatorKeys");

                assertEquals(3, foundKeys.size());
                for (int i = 0; i < 3; i++) {
                    JsonObject operatorKey = foundKeys.getJsonObject(i);
                    assertOperatorKey(operatorKeys[i], operatorKey);
                }
                testContext.completeNow();
            } catch (Throwable ex) {
                testContext.failNow(ex);
            }
        });
    }

    private static Stream<Arguments> searchByAllOperatorKeySuccess() {
        ClientKey[] clientKeys = getClientKeys();
        OperatorKey[] operatorKeys = getOperatorKeys("SHAREDVALUE");
        AdminUser[] adminUsers = getAdminUsers();

        return Stream.of(
                Arguments.of(clientKeys, operatorKeys, adminUsers, "SHAREDVALUE")
        );
    }

    @ParameterizedTest
    @MethodSource("searchByAllAdminKeySuccess")
    void searchByAllAdminKeySuccess(ClientKey[] clientKeys, OperatorKey[] operatorKeys, AdminUser[] adminUsers, String searchString, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setClientKeys(clientKeys);
        setOperatorKeys(operatorKeys);
        setAdminUsers(adminUsers);

        get(vertx, searchUrl + searchString, response -> {
            try {
                assertTrue(response.succeeded());
                HttpResponse httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();
                JsonArray foundKeys = result.getJsonArray("AdministratorKeys");

                assertEquals(3, foundKeys.size());
                for (int i = 0; i < 3; i++) {
                    JsonObject adminKey = foundKeys.getJsonObject(i);
                    assertAdminUser(adminUsers[i], adminKey);
                }
                testContext.completeNow();
            } catch (Throwable ex) {
                testContext.failNow(ex);
            }
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPLCLXt/gh", "/ght6tOD", "t/ght6tOD+8", "+8mmodEtI3J6", "oqMMFk=", "t/ght6tOD%2B8", "%2B8mmodEtI3J6"})
    void searchBySpecialCharactersSuccess(String searchString, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        ClientKey[] clientKeys = Instancio.ofList(ClientKey.class)
                .size(1)
                .set(field(ClientKey::getSecret), "OPLCLXt/ght6tOD+8mmodEtI3J67LW3vcX50LOsQR4oqMMFk=")
                .create().toArray(new ClientKey[0]);
        setClientKeys(clientKeys);

        get(vertx, searchUrl + searchString, response -> {
            try {
                assertTrue(response.succeeded());
                HttpResponse httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();
                JsonArray foundKeys = result.getJsonArray("ClientKeys");

                assertEquals(1, foundKeys.size());
                JsonObject clientKey = foundKeys.getJsonObject(0);
                assertClientKey(clientKeys[0], clientKey);
                testContext.completeNow();
            } catch (Throwable ex) {
                testContext.failNow(ex);
            }
        });

    }

    private static Stream<Arguments> searchByAllAdminKeySuccess() {
        ClientKey[] clientKeys = getClientKeys();
        OperatorKey[] operatorKeys = getOperatorKeys();
        AdminUser[] adminUsers = getAdminUsers("SHAREDVALUE");

        return Stream.of(
                Arguments.of(clientKeys, operatorKeys, adminUsers, "SHAREDVALUE")
        );
    }

    private static ClientKey[] getClientKeys() {
        return getClientKeys("", "");
    }

    private static ClientKey[] getClientKeys(String keySuffix, String secretSuffix) {
        ClientKey[] clientKeys = Instancio.ofList(ClientKey.class)
                .size(3)
                .generate(field(ClientKey::getKey), gen -> gen.string().suffix(keySuffix).minLength(44).mixedCase())
                .generate(field(ClientKey::getSecret), gen -> gen.string().suffix(secretSuffix).minLength(44).mixedCase())
                .create().toArray(new ClientKey[0]);
        return clientKeys;
    }

    private static OperatorKey[] getOperatorKeys() {
        return getOperatorKeys("");
    }

    private static OperatorKey[] getOperatorKeys(String suffix) {
        OperatorKey[] operatorKeys = Instancio.ofList(OperatorKey.class)
                .size(3)
                .generate(field(OperatorKey::getKey), gen -> gen.string().suffix(suffix).minLength(44).mixedCase())
                .create().toArray(new OperatorKey[0]);
        return operatorKeys;
    }

    private static AdminUser[] getAdminUsers() {
        return getAdminUsers("");
    }

    private static AdminUser[] getAdminUsers(String suffix) {
        AdminUser[] adminUsers = Instancio.ofList(AdminUser.class)
                .size(3)
                .generate(field(AdminUser::getKey), gen -> gen.string().suffix(suffix).minLength(44).mixedCase())
                .create().toArray(new AdminUser[0]);
        return adminUsers;
    }

    private static void assertClientKey(ClientKey expected, JsonObject actual) {
        assertEquals(expected.getKey(), actual.getString("key"));
        assertEquals(expected.getSecret(), actual.getString("secret"));
        assertEquals(expected.getName(), actual.getString("name"));
        assertEquals(expected.getContact(), actual.getString("contact"));
        assertEquals(expected.getSiteId(), actual.getInteger("site_id"));

        assertRoles(expected.getRoles(), actual.getJsonArray("roles"));
    }

    private static void assertOperatorKey(OperatorKey expected, JsonObject actual) {
        assertEquals(expected.getKey(), actual.getString("key"));
        assertEquals(expected.getName(), actual.getString("name"));
        assertEquals(expected.getContact(), actual.getString("contact"));
        assertEquals(expected.getProtocol(), actual.getString("protocol"));
        assertEquals(expected.getSiteId(), actual.getInteger("site_id"));
        assertEquals(expected.getOperatorType().toString(), actual.getString("operator_type"));

        assertRoles(expected.getRoles(), actual.getJsonArray("roles"));
    }

    private static void assertAdminUser(AdminUser expected, JsonObject actual) {
        assertEquals(expected.getKey(), actual.getString("key"));
        assertEquals(expected.getName(), actual.getString("name"));
        assertEquals(expected.getContact(), actual.getString("contact"));
        assertEquals(expected.getSiteId(), actual.getInteger("site_id"));

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
