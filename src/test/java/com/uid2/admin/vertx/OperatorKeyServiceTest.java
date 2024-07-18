package com.uid2.admin.vertx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uid2.admin.auth.RevealedKey;
import com.uid2.admin.managers.S3KeyManager;
import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.service.OperatorKeyService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.auth.OperatorType;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.Site;
import com.uid2.shared.util.Mapper;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class OperatorKeyServiceTest extends ServiceTestBase {
    private static final ObjectMapper OBJECT_MAPPER = Mapper.getInstance();
    private static final String KEY_PREFIX = "UID2-O-L-";
    private static final String EXPECTED_OPERATOR_KEY_HASH = "abcdefabcdefabcdefabcdef";
    private static final String EXPECTED_OPERATOR_KEY_SALT = "ghijklghijklghijklghijkl";
    private S3KeyManager s3KeyManager;

    @Override
    protected IService createService() {
        this.config.put("operator_key_prefix", KEY_PREFIX);
        this.config.put("s3_key_activates_in_seconds", 3600L);
        this.config.put("s3_key_count_per_site", 5);
        this.s3KeyManager = Mockito.mock(S3KeyManager.class);
        return new OperatorKeyService(config, auth, writeLock, operatorKeyStoreWriter, operatorKeyProvider, siteProvider, keyGenerator, keyHasher, s3KeyManager);
    }

    @BeforeEach
    public void setup() {
        setSites(new Site(1, "original_site", true), new Site(5, "new_site", true));
    }

    @Test
    public void operatorAdd(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        OperatorKey expectedOperator = new OperatorBuilder()
                .withRoles(Role.OPTOUT, Role.OPERATOR)
                .withType(OperatorType.PUBLIC)
                .build();
        post(vertx, testContext, "api/operator/add?name=test_operator&protocol=trusted&site_id=5&roles=optout&operator_type=public", "", response -> {
            RevealedKey<OperatorKey> revealedOperator = OBJECT_MAPPER.readValue(response.bodyAsString(), new TypeReference<>() {});

            assertAll(
                    "operatorAdd",
                    () -> assertEquals(200, response.statusCode()),
                    () -> assertAddedOperatorKeyEquals(expectedOperator, revealedOperator.getAuthorizable()),
                    () -> assertNotNull(revealedOperator.getPlaintextKey()),
                    () -> verify(operatorKeyStoreWriter).upload(collectionOfSize(1)));
            testContext.completeNow();
        });
    }

    @Test
    public void operatorAddUsesConfigPrefix(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        OperatorKey expectedOperator = new OperatorBuilder()
                .withRoles(Role.OPTOUT, Role.OPERATOR)
                .withType(OperatorType.PUBLIC)
                .build();
        post(vertx, testContext, "api/operator/add?name=test_operator&protocol=trusted&site_id=5&roles=optout&operator_type=public", "", response -> {
            RevealedKey<OperatorKey> revealedOperator = OBJECT_MAPPER.readValue(response.bodyAsString(), new TypeReference<>() {});

            assertAll(
                    "operatorAddUsesConfigPrefix",
                    () -> assertEquals(200, response.statusCode()),
                    () -> assertAddedOperatorKeyEquals(expectedOperator, revealedOperator.getAuthorizable()),
                    () -> assertNotNull(revealedOperator.getPlaintextKey()),
                    () -> verify(operatorKeyStoreWriter).upload(collectionOfSize(1)));
            testContext.completeNow();
        });
    }

    @Test
    public void operatorAddUnknownSiteId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);
        post(vertx, testContext, "api/operator/add?name=test_client&protocol=trusted&site_id=4&roles=optout", "", expectHttpStatus(testContext, 400));
    }

    @Test
    public void operatorKeyAddEmptyRole(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        OperatorKey expectedOperator = new OperatorBuilder().build();
        post(vertx, testContext, "api/operator/add?name=test_operator&protocol=trusted&site_id=5&roles=&operator_type=private", "", response -> {
            RevealedKey<OperatorKey> revealedOperator = OBJECT_MAPPER.readValue(response.bodyAsString(), new TypeReference<>() {});

            assertAll(
                    "operatorKeyAddEmptyRole",
                    () -> assertEquals(200, response.statusCode()),
                    () -> assertAddedOperatorKeyEquals(expectedOperator, revealedOperator.getAuthorizable()),
                    () -> assertNotNull(revealedOperator.getPlaintextKey()),
                    () -> verify(operatorKeyStoreWriter).upload(collectionOfSize(1)));
            testContext.completeNow();
        });
    }

    @Test
    public void operatorKeyAddWithoutRole(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        OperatorKey expectedOperator = new OperatorBuilder().build();
        post(vertx, testContext, "api/operator/add?name=test_operator&protocol=trusted&site_id=5&operator_type=private", "", response -> {
            RevealedKey<OperatorKey> revealedOperator = OBJECT_MAPPER.readValue(response.bodyAsString(), new TypeReference<>() {});

            assertAll(
                    "operatorKeyAddWithoutRole",
                    () -> assertEquals(200, response.statusCode()),
                    () -> assertAddedOperatorKeyEquals(expectedOperator, revealedOperator.getAuthorizable()),
                    () -> assertNotNull(revealedOperator.getPlaintextKey()),
                    () -> verify(operatorKeyStoreWriter).upload(collectionOfSize(1)));
            testContext.completeNow();
        });
    }

    @Test
    void operatorKeyAddInvalidRoleWithOperatorAndNonExistent(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);
        post(vertx, testContext, "api/operator/add?name=test_client&protocol=trusted&site_id=5&roles=operator,nonexistent", "", expectHttpStatus(testContext, 400));
    }

    @Test
    public void operatorKeyAddInvalidRoleCombinationWithOperatorAndOptoutService(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);
        post(vertx, testContext, "api/operator/add?name=test_client&protocol=trusted&site_id=5&roles=operator,optout_service", "", expectHttpStatus(testContext, 400));
    }

    @Test
    public void operatorKeyAddInvalidRoleCombinationWithOptoutAndOptoutService(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);
        post(vertx, testContext, "api/operator/add?name=test_client&protocol=trusted&site_id=5&roles=optout,optout_service", "", expectHttpStatus(testContext, 400));
    }

    @Test
    public void operatorKeyAddInvalidRoleCombinationWithOperatorAndOptoutAndOptoutService(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);
        post(vertx, testContext, "api/operator/add?name=test_client&protocol=trusted&site_id=5&roles=operator,optout,optout_service", "", expectHttpStatus(testContext, 400));
    }

    @Test
    public void operatorUpdate(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);
        setOperatorKeys(new OperatorBuilder().build());

        OperatorKey expectedOperator = new OperatorBuilder()
                .withType(OperatorType.PUBLIC)
                .build();
        post(vertx, testContext, "api/operator/update?name=test_operator&site_id=5&operator_type=public", "", response -> {
            OperatorKey operatorKey = OBJECT_MAPPER.readValue(response.bodyAsString(), OperatorKey.class);

            assertAll(
                    "operatorUpdate",
                    () -> assertEquals(200, response.statusCode()),
                    () -> assertEquals(expectedOperator, operatorKey),
                    () -> verify(operatorKeyStoreWriter).upload(collectionOfSize(1)));
            testContext.completeNow();
        });
    }

    @Test
    public void operatorUpdateUnknownSiteId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);
        setOperatorKeys(new OperatorBuilder().build());
        post(vertx, testContext, "api/operator/update?name=test_client&site_id=4", "", expectHttpStatus(testContext, 404));
    }

    @Test
    public void operatorFlipPublicOperatorStatusViaUpdate(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);
        setOperatorKeys(new OperatorBuilder()
                .withType(OperatorType.PUBLIC)
                .build()
        );

        OperatorKey expectedOperator = new OperatorBuilder().build();
        post(vertx, testContext, "api/operator/update?name=test_operator&operator_type=private", "", response -> {
            OperatorKey operatorKey = OBJECT_MAPPER.readValue(response.bodyAsString(), OperatorKey.class);

            assertAll(
                    "operatorFlipPublicOperatorStatusViaUpdate",
                    () -> assertEquals(200, response.statusCode()),
                    () -> assertEquals(expectedOperator, operatorKey),
                    () -> verify(operatorKeyStoreWriter).upload(collectionOfSize(1)));
            testContext.completeNow();
        });
    }

    @Test
    public void operatorKeySetRole(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);
        setOperatorKeys(new OperatorBuilder().build());

        OperatorKey expectedOperator = new OperatorBuilder()
                .withRoles(Role.OPTOUT, Role.OPERATOR)
                .build();
        post(vertx, testContext, "api/operator/roles?name=test_operator&roles=optout", "", response -> {
            OperatorKey operatorKey = OBJECT_MAPPER.readValue(response.bodyAsString(), OperatorKey.class);

            assertAll(
                    "operatorKeySetRole",
                    () -> assertEquals(200, response.statusCode()),
                    () -> assertEquals(expectedOperator, operatorKey),
                    () -> verify(operatorKeyStoreWriter).upload(collectionOfSize(1)));
            testContext.completeNow();
        });
    }

    @Test
    public void operatorKeySetInvalidRoleCombinationWithOperatorAndNonexistent(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);
        setOperatorKeys(new OperatorBuilder().build());
        post(vertx, testContext, "api/operator/roles?name=test_operator&roles=operator,nonexistent", "", expectHttpStatus(testContext, 400));
    }

    @Test
    public void operatorKeySetInvalidRoleCombinationWithOperatorAndOptoutService(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);
        setOperatorKeys(new OperatorBuilder().build());
        post(vertx, testContext, "api/operator/add?name=test_client&protocol=trusted&site_id=5&roles=operator,optout_service", "", expectHttpStatus(testContext, 400));
    }

    @Test
    public void operatorKeySetInvalidRoleCombinationWithOptoutAndOptoutService(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);
        setOperatorKeys(new OperatorBuilder().build());
        post(vertx, testContext, "api/operator/roles?name=test_operator&roles=optout,optout_service", "", expectHttpStatus(testContext, 400));
    }

    @Test
    public void operatorKeySetInvalidRoleCombinationWithOperatorptoutAndOptoutService(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);
        setOperatorKeys(new OperatorBuilder().build());
        post(vertx, testContext, "api/operator/roles?name=test_operator&roles=operator,optout,optout_service", "", expectHttpStatus(testContext, 400));
    }

    @Test
    public void operatorKeySetEmptyRole(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);
        setOperatorKeys(new OperatorBuilder().build());
        post(vertx, testContext, "api/operator/roles?name=test_operator&roles=", "", expectHttpStatus(testContext, 400));
    }

    @Test
    public void operatorKeySetRoleWithoutRoleParam(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);
        setOperatorKeys(new OperatorBuilder().build());
        post(vertx, testContext, "api/operator/roles?name=test_operator", "", expectHttpStatus(testContext, 400));
    }

    @Test
    public void operatorAddGeneratesS3Keys(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);
        OperatorKey existingOperator = new OperatorBuilder().withSiteId(1).build();
        setOperatorKeys(existingOperator);

        post(vertx, testContext, "api/operator/add?name=test_operator&protocol=trusted&site_id=5&roles=optout&operator_type=public", "", response -> {
            try {
                RevealedKey<OperatorKey> revealedOperator = OBJECT_MAPPER.readValue(response.bodyAsString(), new TypeReference<>() {});

                assertAll(
                        "operatorAddGeneratesS3Keys",
                        () -> assertEquals(200, response.statusCode()),
                        () -> assertNotNull(revealedOperator.getAuthorizable()),
                        () -> verify(s3KeyManager).generateKeysForOperators(
                                argThat(collection -> collection.size() == 1 && collection.iterator().next().getName().equals("test_operator")),
                                eq(3600L),
                                eq(5)
                        )
                );
                testContext.completeNow();
            } catch (Exception e) {
                testContext.failNow(e);
            }
        });
    }

    @Test
    public void operatorUpdateSiteIdGeneratesS3Keys(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        OperatorKey existingOperator = new OperatorBuilder().withSiteId(1).build();
        setOperatorKeys(existingOperator);

        System.out.println("Initial existing operator site ID: " + existingOperator.getSiteId());

        post(vertx, testContext, "api/operator/update?name=test_operator&site_id=5", "", response -> {
            try {
                OperatorKey updatedOperator = OBJECT_MAPPER.readValue(response.bodyAsString(), OperatorKey.class);

                System.out.println("Existing operator site ID after update: " + existingOperator.getSiteId());
                System.out.println("Updated operator site ID: " + updatedOperator.getSiteId());

                assertAll(
                        "operatorUpdateSiteIdGeneratesS3Keys",
                        () -> assertEquals(200, response.statusCode()),
                        () -> assertEquals(5, updatedOperator.getSiteId()),
                        () -> assertNotEquals(1, updatedOperator.getSiteId()),
                        () -> verify(s3KeyManager).generateKeysForOperators(
                                argThat(collection -> collection.size() == 1 && collection.iterator().next().getName().equals("test_operator")),
                                eq(3600L),
                                eq(5)
                        )
                );
                testContext.completeNow();
            } catch (Exception e) {
                testContext.failNow(e);
            }
        });
    }

    @Test
    public void operatorUpdateWithoutSiteIdChangeDoesNotGenerateS3Keys(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        OperatorKey existingOperator = new OperatorBuilder().build();
        setOperatorKeys(existingOperator);

        post(vertx, testContext, "api/operator/update?name=test_operator&operator_type=public", "", response -> {
            try {
                OperatorKey updatedOperator = OBJECT_MAPPER.readValue(response.bodyAsString(), OperatorKey.class);

                assertAll(
                        "operatorUpdateWithoutSiteIdChangeDoesNotGenerateS3Keys",
                        () -> assertEquals(200, response.statusCode()),
                        () -> assertEquals(existingOperator.getSiteId(), updatedOperator.getSiteId()),
                        () -> verify(s3KeyManager, never()).generateKeysForOperators(any(), anyLong(), anyInt())
                );
                testContext.completeNow();
            } catch (Exception e) {
                testContext.failNow(e);
            }
        });
    }

    private static void assertAddedOperatorKeyEquals(OperatorKey expected, OperatorKey actual) {
        assertThat(actual)
                .usingRecursiveComparison()
                .ignoringFields("created")
                .isEqualTo(expected);
    }

    private static class OperatorBuilder {
        private final OperatorKey operator = new OperatorKey(
                EXPECTED_OPERATOR_KEY_HASH,
                EXPECTED_OPERATOR_KEY_SALT,
                "test_operator",
                "test_operator",
                "trusted",
                0,
                false,
                5,
                Set.of(Role.OPERATOR),
                OperatorType.PRIVATE,
                "UID2-O-L-5-abcde"
        );

        public OperatorBuilder() {
        }

        public OperatorBuilder withRoles(Role... roles) {
            this.operator.setRoles(Set.of(roles));
            return this;
        }

        public OperatorBuilder withType(OperatorType operatorType) {
            this.operator.setOperatorType(operatorType);
            return this;
        }

        public OperatorKey build() {
            return operator;
        }

        public OperatorBuilder withSiteId(int siteId) {
            this.operator.setSiteId(siteId);
            return this;
        }
    }
}
