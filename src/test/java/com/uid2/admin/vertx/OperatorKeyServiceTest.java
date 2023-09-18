package com.uid2.admin.vertx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uid2.admin.model.RevealedKey;
import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.service.OperatorKeyService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.auth.OperatorType;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.Site;
import com.uid2.shared.utils.ObjectMapperFactory;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

public class OperatorKeyServiceTest extends ServiceTestBase {
    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.build();
    private static final String KEY_PREFIX = "UID2-O-L-";
    private static final String EXPECTED_OPERATOR_KEY_HASH = "abcdefabcdefabcdefabcdef";
    private static final String EXPECTED_OPERATOR_KEY_SALT = "ghijklghijklghijklghijkl";

    @Override
    protected IService createService() {
        this.config.put("operator_key_prefix", KEY_PREFIX);
        return new OperatorKeyService(config, auth, writeLock, operatorKeyStoreWriter, operatorKeyProvider, siteProvider, keyGenerator, keyHasher);
    }

    @Test
    public void operatorAdd(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.OPERATOR_MANAGER);
        setSites(new Site(5, "test_site", true));

        Set<Role> roles = new HashSet<>();
        roles.add(Role.OPTOUT);
        roles.add(Role.OPERATOR);
        OperatorKey expectedOperator = new OperatorKey(EXPECTED_OPERATOR_KEY_HASH, EXPECTED_OPERATOR_KEY_SALT, "test_operator", "test_operator", "trusted", 0, false, 5, roles, OperatorType.PUBLIC);

        post(vertx, "api/operator/add?name=test_operator&protocol=trusted&site_id=5&roles=optout&operator_type=public", "", ar -> {
            try {
                HttpResponse<Buffer> response = ar.result();
                RevealedKey<OperatorKey> revealedOperator = OBJECT_MAPPER.readValue(response.bodyAsString(), new TypeReference<>() {});

                assertAll(
                        "operatorAdd",
                        () -> assertTrue(ar.succeeded()),
                        () -> assertEquals(200, response.statusCode()),
                        () -> assertOperatorKeyEquals(expectedOperator, revealedOperator.getAuthorizable()),
                        () -> assertNotNull(revealedOperator.getPlaintextKey()),
                        () -> verify(operatorKeyStoreWriter).upload(collectionOfSize(1)));
            } catch (Exception ex) {
                fail(ex);
            }
            testContext.completeNow();
        });
    }

    @Test
    public void operatorAddUsesConfigPrefix(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.OPERATOR_MANAGER);
        setSites(new Site(5, "test_site", true));

        Set<Role> roles = new HashSet<>();
        roles.add(Role.OPTOUT);
        roles.add(Role.OPERATOR);
        OperatorKey expectedOperator = new OperatorKey(EXPECTED_OPERATOR_KEY_HASH, EXPECTED_OPERATOR_KEY_SALT, "test_operator", "test_operator", "trusted", 0, false, 5, roles, OperatorType.PUBLIC);

        post(vertx, "api/operator/add?name=test_operator&protocol=trusted&site_id=5&roles=optout&operator_type=public", "", ar -> {
            try {
                HttpResponse<Buffer> response = ar.result();
                RevealedKey<OperatorKey> revealedOperator = OBJECT_MAPPER.readValue(response.bodyAsString(), new TypeReference<>() {});

                assertAll(
                        "operatorAddUsesConfigPrefix",
                        () -> assertTrue(ar.succeeded()),
                        () -> assertEquals(200, response.statusCode()),
                        () -> assertOperatorKeyEquals(expectedOperator, revealedOperator.getAuthorizable()),
                        () -> assertNotNull(revealedOperator.getPlaintextKey()),
                        () -> verify(operatorKeyStoreWriter).upload(collectionOfSize(1)));
            } catch (Exception ex) {
                fail(ex);
            }
            testContext.completeNow();
        });
    }

    @Test
    public void operatorAddUnknownSiteId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.OPERATOR_MANAGER);
        post(vertx, "api/operator/add?name=test_client&protocol=trusted&site_id=5&roles=optout", "", expectHttpError(testContext, 400));
    }

    @Test
    public void operatorKeyAddEmptyRole(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.OPERATOR_MANAGER);
        setSites(new Site(5, "test_site", true));

        Set<Role> roles = new HashSet<>();
        roles.add(Role.OPERATOR);
        OperatorKey expectedOperator = new OperatorKey(EXPECTED_OPERATOR_KEY_HASH, EXPECTED_OPERATOR_KEY_SALT, "test_operator", "test_operator", "trusted", 0, false, 5, roles, OperatorType.PRIVATE);

        post(vertx, "api/operator/add?name=test_operator&protocol=trusted&site_id=5&roles=&operator_type=private", "", ar -> {
            try {
                HttpResponse<Buffer> response = ar.result();
                RevealedKey<OperatorKey> revealedOperator = OBJECT_MAPPER.readValue(response.bodyAsString(), new TypeReference<>() {});

                assertAll(
                        "operatorKeyAddEmptyRole",
                        () -> assertTrue(ar.succeeded()),
                        () -> assertEquals(200, response.statusCode()),
                        () -> assertOperatorKeyEquals(expectedOperator, revealedOperator.getAuthorizable()),
                        () -> assertNotNull(revealedOperator.getPlaintextKey()),
                        () -> verify(operatorKeyStoreWriter).upload(collectionOfSize(1)));
            } catch (Exception ex) {
                fail(ex);
            }
            testContext.completeNow();
        });
    }

    @Test
    public void operatorKeyAddWithoutRole(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.OPERATOR_MANAGER);
        setSites(new Site(5, "test_site", true));

        Set<Role> roles = new HashSet<>();
        roles.add(Role.OPERATOR);
        OperatorKey expectedOperator = new OperatorKey(EXPECTED_OPERATOR_KEY_HASH, EXPECTED_OPERATOR_KEY_SALT, "test_operator", "test_operator", "trusted", 0, false, 5, roles, OperatorType.PRIVATE);

        post(vertx, "api/operator/add?name=test_operator&protocol=trusted&site_id=5&operator_type=private", "", ar -> {
            try {
                HttpResponse<Buffer> response = ar.result();
                RevealedKey<OperatorKey> revealedOperator = OBJECT_MAPPER.readValue(response.bodyAsString(), new TypeReference<>() {});

                assertAll(
                        "operatorKeyAddWithoutRole",
                        () -> assertTrue(ar.succeeded()),
                        () -> assertEquals(200, response.statusCode()),
                        () -> assertOperatorKeyEquals(expectedOperator, revealedOperator.getAuthorizable()),
                        () -> assertNotNull(revealedOperator.getPlaintextKey()),
                        () -> verify(operatorKeyStoreWriter).upload(collectionOfSize(1)));
            } catch (Exception ex) {
                fail(ex);
            }
            testContext.completeNow();
        });
    }

    @Test
    public void operatorKeyAddInvalidRoleWithOperatorAndNonExistent(Vertx vertx, VertxTestContext testContext) {
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
    public void operatorUpdate(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);
        setSites(new Site(5, "test_site", true));
        setOperatorKeys(new OperatorKey(EXPECTED_OPERATOR_KEY_HASH, EXPECTED_OPERATOR_KEY_SALT, "test_operator", "test_operator", "trusted", 0, false, 5, new HashSet<>(List.of(Role.OPERATOR)), OperatorType.PRIVATE));

        OperatorKey expectedOperator = new OperatorKey(EXPECTED_OPERATOR_KEY_HASH, EXPECTED_OPERATOR_KEY_SALT, "test_operator", "test_operator", "trusted", 0, false, 5, new HashSet<>(List.of(Role.OPERATOR)), OperatorType.PUBLIC);

        post(vertx, "api/operator/update?name=test_operator&site_id=5&operator_type=public", "", ar -> {
            try {
                HttpResponse<Buffer> response = ar.result();
                OperatorKey operatorKey = OBJECT_MAPPER.readValue(response.bodyAsString(), OperatorKey.class);

                assertAll(
                        "operatorUpdate",
                        () -> assertTrue(ar.succeeded()),
                        () -> assertEquals(200, response.statusCode()),
                        () -> assertOperatorKeyEquals(expectedOperator, operatorKey),
                        () -> verify(operatorKeyStoreWriter).upload(collectionOfSize(1)));
            } catch (Exception ex) {
                fail(ex);
            }
            testContext.completeNow();
        });
    }

    @Test
    public void operatorUpdateUnknownSiteId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);
        setOperatorKeys(new OperatorKey(EXPECTED_OPERATOR_KEY_HASH, EXPECTED_OPERATOR_KEY_SALT, "test_operator", "test_operator", "trusted", 0, false));
        post(vertx, "api/operator/update?name=test_client&site_id=5", "", expectHttpError(testContext, 404));
    }

    @Test
    public void operatorFlipPublicOperatorStatusViaUpdate(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        Set<Role> roles = new HashSet<>();
        roles.add(Role.OPERATOR);

        setSites(new Site(5, "test_site", true));
        setOperatorKeys(new OperatorKey(EXPECTED_OPERATOR_KEY_HASH, EXPECTED_OPERATOR_KEY_SALT, "test_operator", "test_operator", "trusted", 0, false, 5, roles, OperatorType.PUBLIC));

        OperatorKey expectedOperator = new OperatorKey(EXPECTED_OPERATOR_KEY_HASH, EXPECTED_OPERATOR_KEY_SALT, "test_operator", "test_operator", "trusted", 0, false, 5, roles, OperatorType.PRIVATE);

        post(vertx, "api/operator/update?name=test_operator&operator_type=private", "", ar -> {
            try {
                HttpResponse<Buffer> response = ar.result();
                OperatorKey operatorKey = OBJECT_MAPPER.readValue(response.bodyAsString(), OperatorKey.class);

                assertAll(
                        "operatorFlipPublicOperatorStatusViaUpdate",
                        () -> assertTrue(ar.succeeded()),
                        () -> assertEquals(200, response.statusCode()),
                        () -> assertOperatorKeyEquals(expectedOperator, operatorKey),
                        () -> verify(operatorKeyStoreWriter).upload(collectionOfSize(1)));
            } catch (Exception ex) {
                fail(ex);
            }
            testContext.completeNow();
        });
    }

    @Test
    public void operatorKeySetRole(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.OPERATOR_MANAGER);
        setOperatorKeys(new OperatorKey(EXPECTED_OPERATOR_KEY_HASH, EXPECTED_OPERATOR_KEY_SALT, "test_operator", "test_operator", "trusted", 0, false, 5, new HashSet<>(List.of(Role.OPERATOR))));

        Set<Role> roles = new HashSet<>();
        roles.add(Role.OPTOUT);
        roles.add(Role.OPERATOR);
        OperatorKey expectedOperator = new OperatorKey(EXPECTED_OPERATOR_KEY_HASH, EXPECTED_OPERATOR_KEY_SALT, "test_operator", "test_operator", "trusted", 0, false, 5, roles);

        post(vertx, "api/operator/roles?name=test_operator&roles=optout", "", ar -> {
            try {
                HttpResponse<Buffer> response = ar.result();
                OperatorKey operatorKey = OBJECT_MAPPER.readValue(response.bodyAsString(), OperatorKey.class);

                assertAll(
                        "operatorKeySetRole",
                        () -> assertTrue(ar.succeeded()),
                        () -> assertEquals(200, response.statusCode()),
                        () -> assertOperatorKeyEquals(expectedOperator, operatorKey),
                        () -> verify(operatorKeyStoreWriter).upload(collectionOfSize(1)));
            } catch (Exception ex) {
                fail(ex);
            }
            testContext.completeNow();
        });
    }

    @Test
    public void operatorKeySetInvalidRoleCombinationWithOperatorAndNonexistent(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.OPERATOR_MANAGER);
        setOperatorKeys(new OperatorKey(EXPECTED_OPERATOR_KEY_HASH, EXPECTED_OPERATOR_KEY_SALT, "test_operator", "test_operator", "trusted", 0, false, 5, new HashSet<>(List.of(Role.OPERATOR))));
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
        setOperatorKeys(new OperatorKey(EXPECTED_OPERATOR_KEY_HASH, EXPECTED_OPERATOR_KEY_SALT, "test_operator", "test_operator", "trusted", 0, false, 5, new HashSet<>(List.of(Role.OPERATOR))));
        post(vertx, "api/operator/roles?name=test_operator&roles=optout,optout_service", "", expectHttpError(testContext, 400));
    }

    @Test
    public void operatorKeySetInvalidRoleCombinationWithOperatorptoutAndOptoutService(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.OPERATOR_MANAGER);
        setOperatorKeys(new OperatorKey(EXPECTED_OPERATOR_KEY_HASH, EXPECTED_OPERATOR_KEY_SALT, "test_operator", "test_operator", "trusted", 0, false, 5, new HashSet<>(List.of(Role.OPERATOR))));
        post(vertx, "api/operator/roles?name=test_operator&roles=operator,optout,optout_service", "", expectHttpError(testContext, 400));
    }

    @Test
    public void operatorKeySetEmptyRole(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.OPERATOR_MANAGER);
        setOperatorKeys(new OperatorKey(EXPECTED_OPERATOR_KEY_HASH, EXPECTED_OPERATOR_KEY_SALT, "test_operator", "test_operator", "trusted", 0, false, 5, new HashSet<>(List.of(Role.OPERATOR))));
        post(vertx, "api/operator/roles?name=test_operator&roles=", "", expectHttpError(testContext, 400));
    }

    @Test
    public void operatorKeySetRoleWithoutRoleParam(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.OPERATOR_MANAGER);
        setOperatorKeys(new OperatorKey(EXPECTED_OPERATOR_KEY_HASH, EXPECTED_OPERATOR_KEY_SALT, "test_operator", "test_operator", "trusted", 0, false, 5, new HashSet<>(List.of(Role.OPERATOR))));
        post(vertx, "api/operator/roles?name=test_operator", "", expectHttpError(testContext, 400));
    }

    private static void assertOperatorKeyEquals(OperatorKey expected, OperatorKey actual) {
        assertThat(actual)
                .usingRecursiveComparison()
                .ignoringFields("created")
                .isEqualTo(expected);
    }
}
