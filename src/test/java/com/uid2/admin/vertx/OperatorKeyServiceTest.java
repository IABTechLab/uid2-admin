package com.uid2.admin.vertx;

import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.service.OperatorKeyService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.auth.OperatorType;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.Site;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

public class OperatorKeyServiceTest extends ServiceTestBase {
    private final static String KEY_PREFIX = "UID2-O-L-";
    private final static String EXPECTED_OPERATOR_KEY = KEY_PREFIX + "5-abcdef.abcdefabcdefabcdef";
    private final static String EXPECTED_OPERATOR_KEY_HASH = "abcdefabcdefabcdefabcdef";
    private final static String EXPECTED_OPERATOR_KEY_SALT = "ghijklghijklghijklghijkl";

    @Override
    protected IService createService() {
        this.config.put("operator_key_prefix", KEY_PREFIX);
        return new OperatorKeyService(config, auth, writeLock, operatorKeyStoreWriter, operatorKeyProvider, siteProvider, keyGenerator, keyHasher);
    }

    private void checkOperatorKeyJson(OperatorKey expectedOperator, JsonObject actualOperator) {
        List<Role> actualRoles = actualOperator.getJsonArray("roles").stream()
                .map(r -> Role.valueOf((String) r))
                .collect(Collectors.toList());

        assertAll(
                "checkOperatorKeyJson",
                () -> assertEquals(expectedOperator.getKey(), actualOperator.getString("key")),
                () -> assertEquals(expectedOperator.getKeyHash(), actualOperator.getString("key_hash")),
                () -> assertEquals(expectedOperator.getKeySalt(), actualOperator.getString("key_salt")),
                () -> assertEquals(expectedOperator.getName(), actualOperator.getString("name")),
                () -> assertEquals(expectedOperator.getContact(), actualOperator.getString("contact")),
                () -> assertEquals(expectedOperator.getProtocol(), actualOperator.getString("protocol")),
                () -> assertEquals(expectedOperator.isDisabled(), actualOperator.getBoolean("disabled")),
                () -> assertEquals(expectedOperator.getSiteId(), actualOperator.getInteger("site_id")),
                () -> assertEquals(expectedOperator.getRoles().size(), actualRoles.size()),
                () -> assertTrue(actualRoles.containsAll(expectedOperator.getRoles())),
                () -> assertEquals(expectedOperator.getOperatorType(), OperatorType.valueOf(actualOperator.getString("operator_type"))));
    }

    @Test
    void operatorAdd(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.OPERATOR_MANAGER);

        Set<Role> roles = new HashSet<>();
        roles.add(Role.OPTOUT);
        roles.add(Role.OPERATOR);

        setSites(new Site(5, "test_site", true));

        OperatorKey expectedOperator = new OperatorKey(EXPECTED_OPERATOR_KEY, EXPECTED_OPERATOR_KEY_HASH, EXPECTED_OPERATOR_KEY_SALT, "test_operator", "test_operator", "trusted", 0, false, 5, roles, OperatorType.PUBLIC);

        post(vertx, "api/operator/add?name=test_operator&protocol=trusted&site_id=5&roles=optout&operator_type=public", "", ar -> {
            HttpResponse<Buffer> response = ar.result();
            assertAll(
                    "operatorAdd",
                    () -> assertTrue(ar.succeeded()),
                    () -> assertEquals(200, response.statusCode()),
                    () -> checkOperatorKeyJson(expectedOperator, response.bodyAsJsonObject()));

            try {
                verify(operatorKeyStoreWriter).upload(collectionOfSize(1));
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
    }

    @Test
    void operatorAddUsesConfigPrefix(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.OPERATOR_MANAGER);

        Set<Role> roles = new HashSet<>();
        roles.add(Role.OPTOUT);
        roles.add(Role.OPERATOR);

        setSites(new Site(5, "test_site", true));

        OperatorKey expectedOperator = new OperatorKey(EXPECTED_OPERATOR_KEY, EXPECTED_OPERATOR_KEY_HASH, EXPECTED_OPERATOR_KEY_SALT, "test_operator", "test_operator", "trusted", 0, false, 5, roles, OperatorType.PUBLIC);

        post(vertx, "api/operator/add?name=test_operator&protocol=trusted&site_id=5&roles=optout&operator_type=public", "", ar -> {
            HttpResponse<Buffer> response = ar.result();
            assertAll(
                    "operatorAdd",
                    () -> assertTrue(ar.succeeded()),
                    () -> assertEquals(200, response.statusCode()),
                    () -> checkOperatorKeyJson(expectedOperator, response.bodyAsJsonObject()));

            try {
                verify(operatorKeyStoreWriter).upload(collectionOfSize(1));
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
    }

    @Test
    void operatorAddUnknownSiteId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.OPERATOR_MANAGER);
        post(vertx, "api/operator/add?name=test_client&protocol=trusted&site_id=5&roles=optout", "", expectHttpError(testContext, 400));
    }

    @Test
    void operatorKeyAddEmptyRole(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.OPERATOR_MANAGER);

        Set<Role> roles = new HashSet<>();
        roles.add(Role.OPERATOR);

        setSites(new Site(5, "test_site", true));

        OperatorKey expectedOperator = new OperatorKey(EXPECTED_OPERATOR_KEY, EXPECTED_OPERATOR_KEY_HASH, EXPECTED_OPERATOR_KEY_SALT, "test_operator", "test_operator", "trusted", 0, false, 5, roles, OperatorType.PRIVATE);

        post(vertx, "api/operator/add?name=test_operator&protocol=trusted&site_id=5&roles=&operator_type=private", "", ar -> {
            HttpResponse<Buffer> response = ar.result();
            assertAll(
                    "operatorKeyAddEmptyRole",
                    () -> assertTrue(ar.succeeded()),
                    () -> assertEquals(200, response.statusCode()),
                    () -> checkOperatorKeyJson(expectedOperator, response.bodyAsJsonObject()));

            try {
                verify(operatorKeyStoreWriter).upload(collectionOfSize(1));
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
    }

    @Test
    void operatorKeyAddWithoutRole(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.OPERATOR_MANAGER);

        Set<Role> roles = new HashSet<>();
        roles.add(Role.OPERATOR);

        setSites(new Site(5, "test_site", true));

        OperatorKey expectedOperator = new OperatorKey(EXPECTED_OPERATOR_KEY, EXPECTED_OPERATOR_KEY_HASH, EXPECTED_OPERATOR_KEY_SALT, "test_operator", "test_operator", "trusted", 0, false, 5, roles, OperatorType.PRIVATE);

        post(vertx, "api/operator/add?name=test_operator&protocol=trusted&site_id=5&operator_type=private", "", ar -> {
            HttpResponse<Buffer> response = ar.result();
            assertAll(
                    "operatorKeyAddWithoutRole",
                    () -> assertTrue(ar.succeeded()),
                    () -> assertEquals(200, response.statusCode()),
                    () -> checkOperatorKeyJson(expectedOperator, response.bodyAsJsonObject()));

            try {
                verify(operatorKeyStoreWriter).upload(collectionOfSize(1));
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
    }

    @Test
    void operatorKeyAddInvalidRoleWithOperatorAndNonExistent(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.OPERATOR_MANAGER);
        post(vertx, "api/operator/add?name=test_client&protocol=trusted&site_id=5&roles=operator,nonexistent", "", expectHttpError(testContext, 400));
    }

    @Test
    public void operatorKeyAddInvalidRoleCombinationWithOperatorAndOptoutService(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.OPERATOR_MANAGER);
        post(vertx, "api/operator/add?name=test_client&protocol=trusted&site_id=5&roles=operator,optout_service", "", expectHttpError(testContext, 400));
    }

    @Test
    public void operatorKeyAddInvalidRoleCombinationWithOptoutAndOptoutService(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.OPERATOR_MANAGER);
        post(vertx, "api/operator/add?name=test_client&protocol=trusted&site_id=5&roles=optout,optout_service", "", expectHttpError(testContext, 400));
    }

    @Test
    public void operatorKeyAddInvalidRoleCombinationWithOperatorAndOptoutAndOptoutService(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.OPERATOR_MANAGER);
        post(vertx, "api/operator/add?name=test_client&protocol=trusted&site_id=5&roles=operator,optout,optout_service", "", expectHttpError(testContext, 400));
    }

    @Test
    void operatorUpdate(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setSites(new Site(5, "test_site", true));

        setOperatorKeys(new OperatorKey(EXPECTED_OPERATOR_KEY, EXPECTED_OPERATOR_KEY_HASH, EXPECTED_OPERATOR_KEY_SALT, "test_operator", "test_operator", "trusted", 0, false, 5, new HashSet<>(List.of(Role.OPERATOR)), OperatorType.PRIVATE));

        OperatorKey expectedOperator = new OperatorKey(EXPECTED_OPERATOR_KEY, EXPECTED_OPERATOR_KEY_HASH, EXPECTED_OPERATOR_KEY_SALT, "test_operator", "test_operator", "trusted", 0, false, 5, new HashSet<>(List.of(Role.OPERATOR)), OperatorType.PUBLIC);

        post(vertx, "api/operator/update?name=test_operator&site_id=5&operator_type=public", "", ar -> {
            HttpResponse<Buffer> response = ar.result();
            assertAll(
                    "operatorUpdate",
                    () -> assertTrue(ar.succeeded()),
                    () -> assertEquals(200, response.statusCode()),
                    () -> checkOperatorKeyJson(expectedOperator, response.bodyAsJsonObject()));

            try {
                verify(operatorKeyStoreWriter).upload(collectionOfSize(1));
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
    }

    @Test
    void operatorUpdateUnknownSiteId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);
        setOperatorKeys(new OperatorKey(EXPECTED_OPERATOR_KEY, EXPECTED_OPERATOR_KEY_HASH, EXPECTED_OPERATOR_KEY_SALT, "test_operator", "test_operator", "trusted", 0, false));
        post(vertx, "api/operator/update?name=test_client&site_id=5", "", expectHttpError(testContext, 404));
    }

    //update Public Operator Status to Private
    @Test
    void operatorFlipPublicOperatorStatusViaUpdate(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        Set<Role> roles = new HashSet<>();
        roles.add(Role.OPERATOR);

        setSites(new Site(5, "test_site", true));

        setOperatorKeys(new OperatorKey(EXPECTED_OPERATOR_KEY, EXPECTED_OPERATOR_KEY_HASH, EXPECTED_OPERATOR_KEY_SALT, "test_operator", "test_operator", "trusted", 0, false, 5, roles, OperatorType.PUBLIC));

        OperatorKey expectedOperator = new OperatorKey(EXPECTED_OPERATOR_KEY, EXPECTED_OPERATOR_KEY_HASH, EXPECTED_OPERATOR_KEY_SALT, "test_operator", "test_operator", "trusted", 0, false, 5, roles, OperatorType.PRIVATE);

        post(vertx, "api/operator/update?name=test_operator&operator_type=private", "", ar -> {
            HttpResponse<Buffer> response = ar.result();
            assertAll(
                    "operatorFlipPublicOperatorStatusViaUpdate",
                    () -> assertTrue(ar.succeeded()),
                    () -> assertEquals(200, response.statusCode()),
                    () -> checkOperatorKeyJson(expectedOperator, response.bodyAsJsonObject()));

            try {
                verify(operatorKeyStoreWriter).upload(collectionOfSize(1));
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
    }

    @Test
    void operatorKeySetRole(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.OPERATOR_MANAGER);

        Set<Role> roles = new HashSet<>();
        roles.add(Role.OPTOUT);
        roles.add(Role.OPERATOR);

        setOperatorKeys(new OperatorKey(EXPECTED_OPERATOR_KEY, EXPECTED_OPERATOR_KEY_HASH, EXPECTED_OPERATOR_KEY_SALT, "test_operator", "test_operator", "trusted", 0, false, 5, new HashSet<>(List.of(Role.OPERATOR))));

        OperatorKey expectedOperator = new OperatorKey(EXPECTED_OPERATOR_KEY, EXPECTED_OPERATOR_KEY_HASH, EXPECTED_OPERATOR_KEY_SALT, "test_operator", "test_operator", "trusted", 0, false, 5, roles);

        post(vertx, "api/operator/roles?name=test_operator&roles=optout", "", ar -> {
            HttpResponse<Buffer> response = ar.result();
            assertAll(
                    "operatorKeySetRole",
                    () -> assertTrue(ar.succeeded()),
                    () -> assertEquals(200, response.statusCode()),
                    () -> checkOperatorKeyJson(expectedOperator, response.bodyAsJsonObject()));

            try {
                verify(operatorKeyStoreWriter).upload(collectionOfSize(1));
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
    }

    @Test
    void operatorKeySetInvalidRoleCombinationWithOperatorAndNonexistent(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.OPERATOR_MANAGER);
        setOperatorKeys(new OperatorKey(EXPECTED_OPERATOR_KEY, EXPECTED_OPERATOR_KEY_HASH, EXPECTED_OPERATOR_KEY_SALT, "test_operator", "test_operator", "trusted", 0, false, 5, new HashSet<>(List.of(Role.OPERATOR))));
        post(vertx, "api/operator/roles?name=test_operator&roles=operator,nonexistent", "", expectHttpError(testContext, 400));
    }

    @Test
    public void operatorKeySetInvalidRoleCombinationWithOperatorAndOptoutService(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.OPERATOR_MANAGER);
        post(vertx, "api/operator/add?name=test_client&protocol=trusted&site_id=5&roles=operator,optout_service", "", expectHttpError(testContext, 400));
    }

    @Test
    public void operatorKeySetInvalidRoleCombinationWithOptoutAndOptoutService(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.OPERATOR_MANAGER);
        setOperatorKeys(new OperatorKey(EXPECTED_OPERATOR_KEY, EXPECTED_OPERATOR_KEY_HASH, EXPECTED_OPERATOR_KEY_SALT, "test_operator", "test_operator", "trusted", 0, false, 5, new HashSet<>(List.of(Role.OPERATOR))));
        post(vertx, "api/operator/roles?name=test_operator&roles=optout,optout_service", "", expectHttpError(testContext, 400));
    }

    @Test
    public void operatorKeySetInvalidRoleCombinationWithOperatorptoutAndOptoutService(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.OPERATOR_MANAGER);
        setOperatorKeys(new OperatorKey(EXPECTED_OPERATOR_KEY, EXPECTED_OPERATOR_KEY_HASH, EXPECTED_OPERATOR_KEY_SALT, "test_operator", "test_operator", "trusted", 0, false, 5, new HashSet<>(List.of(Role.OPERATOR))));
        post(vertx, "api/operator/roles?name=test_operator&roles=operator,optout,optout_service", "", expectHttpError(testContext, 400));
    }

    @Test
    void operatorKeySetEmptyRole(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.OPERATOR_MANAGER);
        setOperatorKeys(new OperatorKey(EXPECTED_OPERATOR_KEY, EXPECTED_OPERATOR_KEY_HASH, EXPECTED_OPERATOR_KEY_SALT, "test_operator", "test_operator", "trusted", 0, false, 5, new HashSet<>(List.of(Role.OPERATOR))));
        post(vertx, "api/operator/roles?name=test_operator&roles=", "", expectHttpError(testContext, 400));
    }

    @Test
    void operatorKeySetRoleWithoutRoleParam(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.OPERATOR_MANAGER);
        setOperatorKeys(new OperatorKey(EXPECTED_OPERATOR_KEY, EXPECTED_OPERATOR_KEY_HASH, EXPECTED_OPERATOR_KEY_SALT, "test_operator", "test_operator", "trusted", 0, false, 5, new HashSet<>(List.of(Role.OPERATOR))));
        post(vertx, "api/operator/roles?name=test_operator", "", expectHttpError(testContext, 400));
    }
}
