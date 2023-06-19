package com.uid2.admin.vertx;

import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.service.SearchService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.auth.ClientKey;
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
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.instancio.Select.field;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SearchServiceTest extends ServiceTestBase {
    private final static String searchUrl = "api/search?keyOrSecret=";

    @Override
    protected IService createService() {
        return new SearchService(auth, clientKeyProvider);
    }

    @ParameterizedTest
    @MethodSource("nonAdminRolesTestData")
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
            assertTrue(response.succeeded());
            HttpResponse httpResponse = response.result();
            JsonArray result = httpResponse.bodyAsJsonArray();
            assertEquals(1, result.size());
            JsonObject client = result.getJsonObject(0);
            AssertClientKey(clientKeys[0], client);
            testContext.completeNow();
        });
    }

    @ParameterizedTest
    @MethodSource("clientsToFindBySearchString")
    void searchClientKeyFindsKeyNew(ClientKey[] clientKeys, String searchString, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setClientKeys(clientKeys);

        get(vertx, searchUrl + searchString, response -> {
            try {
                assertTrue(response.succeeded());
                HttpResponse httpResponse = response.result();
                JsonArray result = httpResponse.bodyAsJsonArray();
                assertEquals(1, result.size());
                JsonObject client = result.getJsonObject(0);
                AssertClientKey(clientKeys[1], client);
                testContext.completeNow();
            } catch (Throwable ex) {
                testContext.failNow(ex);
            }
        });
    }

    private static Stream<Arguments> clientsToFindBySearchString() {
        ClientKey[] clientKeys = getClientKeys();
        String key = clientKeys[1].getKey();
        return Stream.of(
                Arguments.of(clientKeys, key.substring(0, 8)),
                Arguments.of(clientKeys, key.substring(key.length() - 8, key.length())),
                Arguments.of(clientKeys, key.substring(10, 20)),
                Arguments.of(clientKeys, key)
        );
    }

    @ParameterizedTest
    @MethodSource("clientsToNotFindByCase")
    void searchClientKeyDoesNotFindDifferentCase(ClientKey[] clientKeys, String searchString, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setClientKeys(clientKeys);

        get(vertx, searchUrl + searchString, response -> {
            try {
                assertTrue(response.succeeded());
                HttpResponse httpResponse = response.result();
                JsonArray result = httpResponse.bodyAsJsonArray();
                assertEquals(0, result.size());
                testContext.completeNow();
            } catch (Throwable ex) {
                testContext.failNow(ex);
            }
        });
    }

    private static Stream<Arguments> clientsToNotFindByCase() {
        ClientKey[] clientKeys = getClientKeys();
        String key = clientKeys[1].getKey();
        return Stream.of(
                Arguments.of(clientKeys, key.toLowerCase()),
                Arguments.of(clientKeys, key.toUpperCase())
        );
    }


    private static Stream<Arguments> nonAdminRolesTestData() {
        return Stream.of(
                Arguments.of(Role.GENERATOR),
                Arguments.of(Role.MAPPER),
                Arguments.of(Role.ID_READER),
                Arguments.of(Role.SHARER),
                Arguments.of(Role.OPERATOR),
                Arguments.of(Role.OPTOUT),
                Arguments.of(Role.CLIENTKEY_ISSUER),
                Arguments.of(Role.OPERATOR_MANAGER),
                Arguments.of(Role.SECRET_MANAGER),
                Arguments.of(Role.SHARING_PORTAL),
                Arguments.of(Role.PRIVATE_SITE_REFRESHER)
        );
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

    private static void AssertClientKey(ClientKey expected, JsonObject actual) {
        assertEquals(expected.getKey(), actual.getString("key"));
        assertEquals(expected.getSecret(), actual.getString("secret"));
        assertEquals(expected.getName(), actual.getString("name"));
        assertEquals(expected.getContact(), actual.getString("contact"));
        assertEquals(expected.getSiteId(), actual.getInteger("site_id"));

        List<Role> actualRoles = actual.getJsonArray("roles").stream()
                .map(r -> Role.valueOf((String) r))
                .collect(Collectors.toList());
        assertEquals(expected.getRoles().size(), actualRoles.size());
        for (Role role : expected.getRoles()) {
            assertTrue(actualRoles.contains(role));
        }
    }

    private static ClientKey[] getClientKeys() {
        ClientKey[] clientKeys = Instancio.ofList(ClientKey.class)
                .size(3)
                .generate(field(ClientKey::getKey), gen -> gen.string().minLength(44).mixedCase())
                .generate(field(ClientKey::getSecret), gen -> gen.string().minLength(44).mixedCase())
                .create().toArray(new ClientKey[0]);
        return clientKeys;
    }
}
