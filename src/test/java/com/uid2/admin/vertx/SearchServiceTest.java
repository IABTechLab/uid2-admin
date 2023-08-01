package com.uid2.admin.vertx;

import com.uid2.admin.auth.AdminUser;
import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.service.SearchService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.auth.ClientKey;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.auth.Role;
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
                    "SearchAsAdminPasses",
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

    @Test
    void searchClientKeyFindsKey(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);
        ClientKey[] clientKeys = new ClientKey[3];
        clientKeys[0] = new ClientKey(
                "UID2-C-L-997-fCXrMM.fsR3mDqAXELtWWMS+xG1s7RdgRTMqdOH2qaAo=",
                "UID2-C-L-997-P7MIM/IqqkdIKFnm7T6dFSlL5DdZOAi11ll5/kVZk9SPc/CsLxziRRfklj7hEcOi99GOB/ynxZIgZP0Pwf7dYQ==$qJ+O3DQmu2elWU+WvvFJZtiPJVIcNd507gkgptSCo4C=",
                "DzBzbjTJcYL0swDtFs2krRNu+g1Eokm2tBU4dEuD0Wk=")
                .withNameAndContact("MegaTest Client")
                .withRoles(Role.OPERATOR)
                .withSiteId(5);
        clientKeys[1] = new ClientKey(
                "UID2-C-L-998-t32pCM.5NCX1E94UgOd2f8zhsKmxzCoyhXohHYSSWR8U=",
                "UID2-C-L-998-P7MIM/IqqkdIKFnm7T6dFSlL5DdZOAi11ll5/kVZk9SPc/CsLxziRRfklj7hEcOi99GOB/ynxZIgZP0Pwf7dYQ==$qJ+O3DQmu2elWU+WvvFJZtiPJVIcNd507gkgptSCo4B=",
                "FsD4bvtjMkeTonx6HvQp6u0EiI1ApGH4pIZzZ5P7UcQ=")
                .withNameAndContact("MegaTest Client 2")
                .withRoles(Role.MAPPER)
                .withSiteId(5);
        clientKeys[2] = new ClientKey(
                "UID2-C-L-999-H8VwqX.l2G4TCuUWYAqdqkeG/UqtFoPEoXirKn4kHWxc=",
                "UID2-C-L-999-P7MIM/IqqkdIKFnm7T6dFSlL5DdZOAi11ll5/kVZk9SPc/CsLxziRRfklj7hEcOi99GOB/ynxZIgZP0Pwf7dYQ==$qJ+O3DQmu2elWU+WvvFJZtiPJVIcNd507gkgptSCo4A=",
                "NcMgi6Y8C80SlxvV7pYlfcvEIo+2b0508tYQ3pKK8HM=")
                .withNameAndContact("TestCorp Client")
                .withRoles(Role.OPERATOR)
                .withSiteId(5);

        setClientKeys(clientKeys);
        post(vertx, searchUrl, "UID2-C-L-997-", response -> {
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

    @ParameterizedTest
    @MethodSource("searchByClientKey")
    void searchByClientKeySuccess(ClientKey[] clientKeys, OperatorKey[] operatorKeys, AdminUser[] adminUsers, String searchString, Vertx vertx, VertxTestContext testContext) {
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
                        "searchByClientKeySuccess",
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

    private static Stream<Arguments> searchByClientKey() {
        ClientKey[] clientKeys = getClientKeys();
        OperatorKey[] operatorKeys = getOperatorKeys();
        AdminUser[] adminUsers = getAdminUsers();

        String key = clientKeys[1].getKey();
        return Stream.of(
                Arguments.of(clientKeys, operatorKeys, adminUsers, key.substring(0, 8)),
                Arguments.of(clientKeys, operatorKeys, adminUsers, key.substring(key.length() - 8)),
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
    @MethodSource("searchByOperatorKeySuccess")
    void searchByOperatorKeySuccess(ClientKey[] clientKeys, OperatorKey[] operatorKeys, AdminUser[] adminUsers, String searchString, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setClientKeys(clientKeys);
        setOperatorKeys(operatorKeys);
        setAdminUsers(adminUsers);

        post(vertx, searchUrl, searchString, response -> {
            try {
                HttpResponse<Buffer> httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();
                JsonArray foundKeys = result.getJsonArray("OperatorKeys");
                JsonObject operator = foundKeys.getJsonObject(0);

                assertAll(
                        "searchByOperatorKeySuccess",
                        () -> assertTrue(response.succeeded()),
                        () -> assertEquals(1, foundKeys.size()),
                        () -> assertOperatorKey(operatorKeys[1], operator)
                );
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
                Arguments.of(clientKeys, operatorKeys, adminUsers, key.substring(key.length() - 8)),
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

        post(vertx, searchUrl, searchString, response -> {
            try {
                HttpResponse<Buffer> httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();
                JsonArray foundKeys = result.getJsonArray("AdministratorKeys");
                JsonObject operator = foundKeys.getJsonObject(0);

                assertAll(
                        "searchByAdminKeySuccess",
                        () -> assertTrue(response.succeeded()),
                        () -> assertEquals(1, foundKeys.size()),
                        () -> assertAdminUser(adminUsers[1], operator)
                );
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
                Arguments.of(clientKeys, operatorKeys, adminUsers, key.substring(key.length() - 8)),
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

        post(vertx, searchUrl, searchString, response -> {
            try {
                HttpResponse<Buffer> httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();
                JsonArray foundClientKeys = result.getJsonArray("ClientKeys");
                JsonArray foundOperatorKeys = result.getJsonArray("OperatorKeys");
                JsonArray foundAdminKeys = result.getJsonArray("AdministratorKeys");
                assertTrue(response.succeeded());

                assertAll(
                        "searchByAllKeySuccess",
                        () -> assertEquals(3, foundClientKeys.size()),
                        () -> assertEquals(3, foundOperatorKeys.size()),
                        () -> assertEquals(3, foundAdminKeys.size())
                );

                for (int i = 0; i < 3; i++) {
                    final int counter = i;
                    JsonObject clientKey = foundClientKeys.getJsonObject(i);
                    JsonObject operatorKey = foundOperatorKeys.getJsonObject(i);
                    JsonObject adminKey = foundAdminKeys.getJsonObject(i);
                    assertAll(
                            "searchByAllKeySuccess for instances",
                            () -> assertClientKey(clientKeys[counter], clientKey),
                            () -> assertOperatorKey(operatorKeys[counter], operatorKey),
                            () -> assertAdminUser(adminUsers[counter], adminKey)
                    );
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

        post(vertx, searchUrl, searchString, response -> {
            try {
                HttpResponse<Buffer> httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();
                JsonArray foundKeys = result.getJsonArray("ClientKeys");

                assertAll(
                        "searchByAllClientKeySuccess",
                        () -> assertTrue(response.succeeded()),
                        () -> assertEquals(3, foundKeys.size())
                );

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

        post(vertx, searchUrl, searchString, response -> {
            try {
                HttpResponse<Buffer> httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();
                JsonArray foundKeys = result.getJsonArray("OperatorKeys");

                assertAll(
                        "searchByAllOperatorKeySuccess",
                        () -> assertTrue(response.succeeded()),
                        () -> assertEquals(3, foundKeys.size())
                );

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

        post(vertx, searchUrl, searchString, response -> {
            try {
                HttpResponse<Buffer> httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();
                JsonArray foundKeys = result.getJsonArray("AdministratorKeys");

                assertAll(
                        "searchByAllAdminKeySuccess",
                        () -> assertTrue(response.succeeded()),
                        () -> assertEquals(3, foundKeys.size())
                );
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
    @ValueSource(strings = {"OPLCLXt/gh", "/ght6tOD", "t/ght6tOD+8", "+8mmodEtI3J6", "oqMMFk="})
    void searchBySpecialCharactersSuccess(String searchString, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        ClientKey[] clientKeys = Instancio.ofList(ClientKey.class)
                .size(1)
                .set(field(ClientKey::getSecret), "OPLCLXt/ght6tOD+8mmodEtI3J67LW3vcX50LOsQR4oqMMFk=")
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
        return Instancio.ofList(ClientKey.class)
                .size(3)
                .generate(field(ClientKey::getKey), gen -> gen.string().suffix(keySuffix).minLength(44).mixedCase())
                .generate(field(ClientKey::getSecret), gen -> gen.string().suffix(secretSuffix).minLength(44).mixedCase())
                .create().toArray(new ClientKey[0]);
    }

    private static OperatorKey[] getOperatorKeys() {
        return getOperatorKeys("");
    }

    private static OperatorKey[] getOperatorKeys(String suffix) {
        return Instancio.ofList(OperatorKey.class)
                .size(3)
                .generate(field(OperatorKey::getKey), gen -> gen.string().suffix(suffix).minLength(44).mixedCase())
                .create().toArray(new OperatorKey[0]);
    }

    private static AdminUser[] getAdminUsers() {
        return getAdminUsers("");
    }

    private static AdminUser[] getAdminUsers(String suffix) {
        return Instancio.ofList(AdminUser.class)
                .size(3)
                .generate(field(AdminUser::getKey), gen -> gen.string().suffix(suffix).minLength(44).mixedCase())
                .create().toArray(new AdminUser[0]);
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
