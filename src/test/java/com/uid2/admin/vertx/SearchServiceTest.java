package com.uid2.admin.vertx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uid2.admin.auth.AdminUser;
import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.service.SearchService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.Utils;
import com.uid2.shared.auth.ClientKey;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.auth.Role;
import com.uid2.shared.secret.SecureKeyGenerator;
import com.uid2.shared.utils.ObjectMapperFactory;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
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

import java.time.Instant;
import java.util.Set;
import java.util.stream.Stream;

import static org.instancio.Assign.valueOf;
import static org.instancio.Select.field;
import static org.junit.jupiter.api.Assertions.*;

public class SearchServiceTest extends ServiceTestBase {
    private static final Instant NOW = Instant.now();
    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.build();
    private static final SecureKeyGenerator SECURE_KEY_GENERATOR = new SecureKeyGenerator();
    private static final String SEARCH_URL = "api/search";

    @Override
    protected IService createService() {
        return new SearchService(auth, clientKeyProvider, operatorKeyProvider, adminUserProvider);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMINISTRATOR"}, mode = EnumSource.Mode.EXCLUDE)
    public void searchAsNonAdminFails(Role role, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(role);

        post(vertx, SEARCH_URL, "1234567", expectHttpError(testContext, 401));
    }

    @Test
    public void searchAsAdminPasses(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        post(vertx, SEARCH_URL, "123456", response -> {
            assertAll(
                    "searchAsAdminPasses",
                    () -> assertTrue(response.succeeded()),
                    () -> assertEquals(200, response.result().statusCode())
            );
            testContext.completeNow();
        });
    }

    @Test
    public void searchWithoutRoleFails(Vertx vertx, VertxTestContext testContext) {
        post(vertx, SEARCH_URL, "1234567", expectHttpError(testContext, 401));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "aa", "aaa", "aaaa", "aaaaa"})
    public void searchWithShortQueryStringReturns400Error(String parameter, Vertx vertx, VertxTestContext testContext) {
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
    public void searchClientKeyFindsKey(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        ClientKey[] clientKeys = new ClientKey[3];
        clientKeys[0] = new ClientKey(
                "LOCALbGlvbnVuZGVybGluZXdpbmRzY2FyZWRzb2Z0ZGVzZXI=",
                "OPIi+MWKNz41wzu+atsBLAtXDTLFhLWPq5mCxA3L8anX+fjKaVDAf55D98BSLAh/EFQE/xTQyo/YK5snPS8ivA==",
                "FpgbvHGqpVhi3I/b8/9HnguiycUzb2y+KsdicPpNLJI=",
                "c3RlZXBzcGVuZHNsb3BlZnJlcXVlbnRseWRvd2lkZWM=",
                "Special (Old)",
                NOW,
                Set.of(Role.MAPPER, Role.GENERATOR, Role.ID_READER, Role.SHARER, Role.OPTOUT),
                999
        );
        clientKeys[1] = new ClientKey(
                "UID2-C-L-123-t32pCM.5NCX1E94UgOd2f8zhsKmxzCoyhXohHYSSWR8U=",
                "vVb/MjymmYAE3L6as5t1DCjbts4bT2wZh4V4iAagOAe97jthFmT4YAb6gGVfEs4Pq+CqNPgz+X338RNRa8NOlA==",
                "G36g+KxlS+z5NwSXUOnBtc9yJKHECvXgjbS13X5A7rw=",
                "FsD4bvtjMkeTonx6HvQp6u0EiI1ApGH4pIZzZ5P7UcQ=",
                "DSP",
                NOW,
                Set.of(Role.ID_READER),
                123
        );
        clientKeys[2] = new ClientKey(
                "UID2-C-L-124-H8VwqX.l2G4TCuUWYAqdqkeG/UqtFoPEoXirKn4kHWxc=",
                "uA1aRDR9owk53W47zZpI6cS/bRVgKm4ggRvr9m0pz+I/5IzQcIQqfurm1Ors96r8Q2xC8GZVG3spwR/H89rQmA==",
                "rSwnZ5aKauMLPLMHvvH25C1LU5MdJv5+fjQ5/Yy5hP0=",
                "NcMgi6Y8C80SlxvV7pYlfcvEIo+2b0508tYQ3pKK8HM=",
                "Publisher",
                NOW,
                Set.of(Role.GENERATOR, Role.SHARER),
                124
        );
        setClientKeys(clientKeys);

        post(vertx, SEARCH_URL, "bGlvbn", response -> {
            try {
                HttpResponse<Buffer> httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();
                ClientKey[] foundClientKeys = OBJECT_MAPPER.readValue(result.getJsonArray("ClientKeys").toString(), ClientKey[].class);

                assertAll(
                        "searchClientKeyFindsKey",
                        () -> assertTrue(response.succeeded()),
                        () -> assertEquals(1, foundClientKeys.length),
                        () -> assertEquals(clientKeys[0], foundClientKeys[0])
                );
                testContext.completeNow();
            } catch (Throwable t) {
                testContext.failNow(t);
            }
        });
    }

    @ParameterizedTest
    @MethodSource("searchByClientKeySuccess")
    public void searchByClientKeySuccess(ClientKey[] clientKeys, OperatorKey[] operatorKeys, AdminUser[] adminUsers, String searchString, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setClientKeys(clientKeys);
        setOperatorKeys(operatorKeys);
        setAdminUsers(adminUsers);

        post(vertx, SEARCH_URL, searchString, response -> {
            try {
                HttpResponse<Buffer> httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();
                ClientKey[] foundClientKeys = OBJECT_MAPPER.readValue(result.getJsonArray("ClientKeys").toString(), ClientKey[].class);

                assertAll(
                        "searchByClientKeySuccess",
                        () -> assertTrue(response.succeeded()),
                        () -> assertEquals(1, foundClientKeys.length),
                        () -> assertEquals(clientKeys[1], foundClientKeys[0])
                );
                testContext.completeNow();
            } catch (Throwable ex) {
                testContext.failNow(ex);
            }
        });
    }

    private static Stream<Arguments> searchByClientKeySuccess() {
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
    public void searchByClientKeyNotFound(ClientKey[] clientKeys, OperatorKey[] operatorKeys, AdminUser[] adminUsers, String searchString, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setClientKeys(clientKeys);
        setOperatorKeys(operatorKeys);
        setAdminUsers(adminUsers);

        post(vertx, SEARCH_URL, searchString, response -> {
            try {

                HttpResponse<Buffer> httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();
                ClientKey[] foundClientKeys = OBJECT_MAPPER.readValue(result.getJsonArray("ClientKeys").toString(), ClientKey[].class);
                OperatorKey[] foundOperatorKeys = OBJECT_MAPPER.readValue(result.getJsonArray("OperatorKeys").toString(), OperatorKey[].class);
                AdminUser[] foundAdminUsers = OBJECT_MAPPER.readValue(result.getJsonArray("AdministratorKeys").toString(), AdminUser[].class);

                assertAll(
                        "searchByClientKeyNotFound",
                        () -> assertTrue(response.succeeded()),
                        () -> assertEquals(0, foundClientKeys.length),
                        () -> assertEquals(0, foundOperatorKeys.length),
                        () -> assertEquals(0, foundAdminUsers.length)
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
    public void searchByClientSecretSuccess(ClientKey[] clientKeys, OperatorKey[] operatorKeys, AdminUser[] adminUsers, String searchString, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setClientKeys(clientKeys);
        setOperatorKeys(operatorKeys);
        setAdminUsers(adminUsers);

        post(vertx, SEARCH_URL, searchString, response -> {
            try {
                HttpResponse<Buffer> httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();
                ClientKey[] foundClientKeys = OBJECT_MAPPER.readValue(result.getJsonArray("ClientKeys").toString(), ClientKey[].class);

                assertAll(
                        "searchByClientSecretSuccess",
                        () -> assertTrue(response.succeeded()),
                        () -> assertEquals(1, foundClientKeys.length),
                        () -> assertEquals(clientKeys[1], foundClientKeys[0])
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
    public void searchByOperatorKeySuccess(ClientKey[] clientKeys, OperatorKey[] operatorKeys, AdminUser[] adminUsers, String searchString, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setClientKeys(clientKeys);
        setOperatorKeys(operatorKeys);
        setAdminUsers(adminUsers);

        post(vertx, SEARCH_URL, searchString, response -> {
            try {
                HttpResponse<Buffer> httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();
                OperatorKey[] foundOperatorKeys = OBJECT_MAPPER.readValue(result.getJsonArray("OperatorKeys").toString(), OperatorKey[].class);

                assertAll(
                        "searchByOperatorKeySuccess",
                        () -> assertTrue(response.succeeded()),
                        () -> assertEquals(1, foundOperatorKeys.length),
                        () -> assertEquals(operatorKeys[1], foundOperatorKeys[0])
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

        String keyHash = operatorKeys[1].getKeyHash();
        return Stream.of(
                Arguments.of(clientKeys, operatorKeys, adminUsers, keyHash.substring(0, 8)),
                Arguments.of(clientKeys, operatorKeys, adminUsers, keyHash.substring(keyHash.length() - 8)),
                Arguments.of(clientKeys, operatorKeys, adminUsers, keyHash.substring(10, 20)),
                Arguments.of(clientKeys, operatorKeys, adminUsers, keyHash)
        );
    }

    @ParameterizedTest
    @MethodSource("searchByAdminKeySuccess")
    public void searchByAdminKeySuccess(ClientKey[] clientKeys, OperatorKey[] operatorKeys, AdminUser[] adminUsers, String searchString, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setClientKeys(clientKeys);
        setOperatorKeys(operatorKeys);
        setAdminUsers(adminUsers);

        post(vertx, SEARCH_URL, searchString, response -> {
            try {
                HttpResponse<Buffer> httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();
                AdminUser[] foundAdminUsers = OBJECT_MAPPER.readValue(result.getJsonArray("AdministratorKeys").toString(), AdminUser[].class);

                assertAll(
                        "searchByAdminKeySuccess",
                        () -> assertTrue(response.succeeded()),
                        () -> assertEquals(1, foundAdminUsers.length),
                        () -> assertEquals(adminUsers[1], foundAdminUsers[0])
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
    public void searchByAllKeySuccess(ClientKey[] clientKeys, OperatorKey[] operatorKeys, AdminUser[] adminUsers, String searchString, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setClientKeys(clientKeys);
        setOperatorKeys(operatorKeys);
        setAdminUsers(adminUsers);

        post(vertx, SEARCH_URL, searchString, response -> {
            try {
                HttpResponse<Buffer> httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();

                ClientKey[] foundClientKeys = OBJECT_MAPPER.readValue(result.getJsonArray("ClientKeys").toString(), ClientKey[].class);
                OperatorKey[] foundOperatorKeys = OBJECT_MAPPER.readValue(result.getJsonArray("OperatorKeys").toString(), OperatorKey[].class);
                AdminUser[] foundAdminUsers = OBJECT_MAPPER.readValue(result.getJsonArray("AdministratorKeys").toString(), AdminUser[].class);

                assertAll(
                        "searchByAllKeySuccess",
                        () -> assertTrue(response.succeeded()),
                        () -> assertArrayEquals(clientKeys, foundClientKeys),
                        () -> assertArrayEquals(operatorKeys, foundOperatorKeys),
                        () -> assertArrayEquals(adminUsers, foundAdminUsers)
                );
                testContext.completeNow();
            } catch (Throwable ex) {
                testContext.failNow(ex);
            }
        });
    }

    private static Stream<Arguments> searchByAllKeySuccess() {
        ClientKey[] clientKeys = getClientKeys("SHAREDVALUE");
        OperatorKey[] operatorKeys = getOperatorKeys("SHAREDVALUE");
        AdminUser[] adminUsers = getAdminUsers("SHAREDVALUE");

        return Stream.of(
                Arguments.of(clientKeys, operatorKeys, adminUsers, "SHAREDVALUE")
        );
    }

    @ParameterizedTest
    @MethodSource("searchByAllClientKeySuccess")
    public void searchByAllClientKeySuccess(ClientKey[] clientKeys, OperatorKey[] operatorKeys, AdminUser[] adminUsers, String searchString, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setClientKeys(clientKeys);
        setOperatorKeys(operatorKeys);
        setAdminUsers(adminUsers);

        post(vertx, SEARCH_URL, searchString, response -> {
            try {
                HttpResponse<Buffer> httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();
                ClientKey[] foundClientKeys = OBJECT_MAPPER.readValue(result.getJsonArray("ClientKeys").toString(), ClientKey[].class);

                assertAll(
                        "searchByAllClientKeySuccess",
                        () -> assertTrue(response.succeeded()),
                        () -> assertArrayEquals(clientKeys, foundClientKeys)
                );
                testContext.completeNow();
            } catch (Throwable ex) {
                testContext.failNow(ex);
            }
        });
    }

    private static Stream<Arguments> searchByAllClientKeySuccess() {
        ClientKey[] clientKeys = getClientKeys("SHAREDVALUE");
        OperatorKey[] operatorKeys = getOperatorKeys();
        AdminUser[] adminUsers = getAdminUsers();

        return Stream.of(
                Arguments.of(clientKeys, operatorKeys, adminUsers, "SHAREDVALUE")
        );
    }

    @ParameterizedTest
    @MethodSource("searchByAllOperatorKeySuccess")
    public void searchByAllOperatorKeySuccess(ClientKey[] clientKeys, OperatorKey[] operatorKeys, AdminUser[] adminUsers, String searchString, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setClientKeys(clientKeys);
        setOperatorKeys(operatorKeys);
        setAdminUsers(adminUsers);

        post(vertx, SEARCH_URL, searchString, response -> {
            try {
                HttpResponse<Buffer> httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();
                OperatorKey[] foundOperatorKeys = OBJECT_MAPPER.readValue(result.getJsonArray("OperatorKeys").toString(), OperatorKey[].class);

                assertAll(
                        "searchByAllOperatorKeySuccess",
                        () -> assertTrue(response.succeeded()),
                        () -> assertArrayEquals(operatorKeys, foundOperatorKeys)
                );
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
    public void searchByAllAdminKeySuccess(ClientKey[] clientKeys, OperatorKey[] operatorKeys, AdminUser[] adminUsers, String searchString, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setClientKeys(clientKeys);
        setOperatorKeys(operatorKeys);
        setAdminUsers(adminUsers);

        post(vertx, SEARCH_URL, searchString, response -> {
            try {
                HttpResponse<Buffer> httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();
                AdminUser[] foundAdminUsers = OBJECT_MAPPER.readValue(result.getJsonArray("AdministratorKeys").toString(), AdminUser[].class);

                assertAll(
                        "searchByAllAdminKeySuccess",
                        () -> assertTrue(response.succeeded()),
                        () -> assertArrayEquals(adminUsers, foundAdminUsers)
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

    @ParameterizedTest
    @ValueSource(strings = {"UID2-C-L-123-t32", "t32pCM", "pCM.5N", "8zhsKmxz", "yhXohHYSSWR8U="})
    public void searchBySpecialCharactersSuccess(String searchString, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        ClientKey clientKey = new ClientKey(
                "UID2-C-L-123-t32pCM.5NCX1E94UgOd2f8zhsKmxzCoyhXohHYSSWR8U=",
                "vVb/MjymmYAE3L6as5t1DCjbts4bT2wZh4V4iAagOAe97jthFmT4YAb6gGVfEs4Pq+CqNPgz+X338RNRa8NOlA==",
                "G36g+KxlS+z5NwSXUOnBtc9yJKHECvXgjbS13X5A7rw=",
                "FsD4bvtjMkeTonx6HvQp6u0EiI1ApGH4pIZzZ5P7UcQ=",
                "DSP",
                NOW,
                Set.of(Role.ID_READER),
                123
        );
        setClientKeys(clientKey);

        post(vertx, SEARCH_URL, searchString, response -> {
            try {
                HttpResponse<Buffer> httpResponse = response.result();
                JsonObject result = httpResponse.bodyAsJsonObject();
                ClientKey[] foundClientKeys = OBJECT_MAPPER.readValue(result.getJsonArray("ClientKeys").toString(), ClientKey[].class);

                assertAll(
                        "searchBySpecialCharactersSuccess",
                        () -> assertTrue(response.succeeded()),
                        () -> assertEquals(1, foundClientKeys.length),
                        () -> assertEquals(clientKey, foundClientKeys[0])
                );
                testContext.completeNow();
            } catch (Throwable ex) {
                testContext.failNow(ex);
            }
        });
    }

    private static ClientKey[] getClientKeys() {
        return getClientKeys("");
    }

    private static ClientKey[] getClientKeys(String keySuffix) {
        return Instancio.ofList(ClientKey.class)
                .size(3)
                .generate(field(ClientKey::getKey), gen -> gen.string().suffix(keySuffix).minLength(44).mixedCase())
                .supply(field(ClientKey::getSecretBytes), () -> SECURE_KEY_GENERATOR.generateRandomKey(32))
                .assign(valueOf(ClientKey::getSecretBytes)
                        .to(ClientKey::getSecret)
                        .as(Utils::toBase64String))
                .create()
                .toArray(new ClientKey[0]);
    }

    private static OperatorKey[] getOperatorKeys() {
        return getOperatorKeys("");
    }

    private static OperatorKey[] getOperatorKeys(String suffix) {
        return Instancio.ofList(OperatorKey.class)
                .size(3)
                .generate(field(OperatorKey::getKeyHash), gen -> gen.string().suffix(suffix).minLength(44).mixedCase())
                .generate(field(OperatorKey::getRoles), gen -> gen.collection().with(Role.OPERATOR))
                .create()
                .toArray(new OperatorKey[0]);
    }

    private static AdminUser[] getAdminUsers() {
        return getAdminUsers("");
    }

    private static AdminUser[] getAdminUsers(String suffix) {
        return Instancio.ofList(AdminUser.class)
                .size(3)
                .generate(field(AdminUser::getKey), gen -> gen.string().suffix(suffix).minLength(44).mixedCase())
                .create()
                .toArray(new AdminUser[0]);
    }
}
