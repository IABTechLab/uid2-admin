package com.uid2.admin.vertx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uid2.admin.managers.KeysetManager;
import com.uid2.admin.vertx.service.ClientKeyService;
import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.auth.ClientKey;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.Site;
import com.uid2.shared.util.Mapper;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.VertxTestContext;
import org.assertj.core.util.Sets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ClientKeyServiceTest extends ServiceTestBase {
    private static final ObjectMapper OBJECT_MAPPER = Mapper.getInstance();
    private static final String KEY_PREFIX = "UID2-C-L-";
    private static final String EXPECTED_CLIENT_KEY_HASH = "abcdefabcdefabcdefabcdef";
    private static final String EXPECTED_CLIENT_KEY_SALT = "ghijklghijklghijklghijkl";

    @Override
    protected IService createService() {
        this.config.put("client_key_prefix", KEY_PREFIX);
        KeysetManager keysetManager = new KeysetManager(adminKeysetProvider, adminKeysetWriter, keysetKeyManager, true);
        return new ClientKeyService(config, auth, writeLock, clientKeyStoreWriter, clientKeyProvider, siteProvider, keysetManager, keyGenerator, keyHasher);
    }

    @BeforeEach
    public void setup() {
        setSites(new Site(5, "test_site", true));
    }

    @Test
    public void clientRename(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setClientKeys(new ClientBuilder().build());

        post(vertx, "api/client/rename?oldName=test_client&newName=test_client1", "", ar -> {
            HttpResponse<Buffer> response = ar.result();
            ClientKey expected = new ClientBuilder().withName("test_client1").build();
            try {
                assertAll(
                        "clientRename",
                        () -> assertTrue(ar.succeeded()),
                        () -> assertEquals(200, response.statusCode()),
                        () -> assertEquals(expected, OBJECT_MAPPER.readValue(response.bodyAsString(), ClientKey.class)),
                        () -> verify(clientKeyStoreWriter).upload(collectionOfSize(1), isNull())
                );
            } catch (Exception ex) {
                fail(ex);
            }
            testContext.completeNow();
        });
    }

    @Test
    public void clientRenameWithExistingName(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setClientKeys(
                new ClientBuilder().build(),
                new ClientBuilder().withName("test_client1").build()
        );

        post(vertx, "api/client/rename?oldName=test_client&newName=test_client1", "", ar -> {
            HttpResponse<Buffer> response = ar.result();
            assertAll(
                    "clientRenameWithExistingName",
                    () -> assertTrue(ar.succeeded()),
                    () -> assertEquals(400, response.statusCode())
            );
            testContext.completeNow();
        });
    }

    @Test
    public void clientAdd(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);

        post(vertx, "api/client/add?name=test_client&roles=generator&site_id=5&service_id=145", "", ar -> {
            HttpResponse<Buffer> response = ar.result();
            ClientKey expected = new ClientBuilder().withServiceId(145).build();
            try {
                assertAll(
                        "clientAdd",
                        () -> assertTrue(ar.succeeded()),
                        () -> assertEquals(200, response.statusCode()),
                        () -> assertAddedClientKeyEquals(expected, OBJECT_MAPPER.readValue(response.bodyAsString(), ClientKey.class)),
                        () -> verify(clientKeyStoreWriter).upload(collectionOfSize(1), isNull())
                );
            } catch (Exception ex) {
                fail(ex);
            }
            testContext.completeNow();
        });
    }

    @ParameterizedTest
    @MethodSource("createSiteKeyIfNoneExistsTestData")
    public void clientAddCreatesSiteKeyIfNoneExists(Set<Role> roles, boolean siteKeyShouldBeCreatedIfNoneExists, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);

        String endpoint = String.format("api/client/add?name=test_client&site_id=5&roles=%s", RequestUtil.getRolesSpec(roles));
        post(vertx, endpoint, "", testContext.succeeding(response -> testContext.verify(() -> {
            assertAll(
                    "clientAddCreatesSiteKeyIfNoneExists",
                    () -> assertEquals(200, response.statusCode()),
                    () -> {
                        if (siteKeyShouldBeCreatedIfNoneExists) {
                            verify(keysetKeyManager).addKeysetKey(4);
                        }
                    },
                    () -> verifyNoMoreInteractions(keysetKeyManager)
            );
            testContext.completeNow();
        })));
    }

    private static Stream<Arguments> createSiteKeyIfNoneExistsTestData() {
        return Stream.of(
                Arguments.of(Sets.set(Role.GENERATOR), true),
                Arguments.of(Sets.set(Role.ID_READER), false),
                Arguments.of(Sets.set(Role.ID_READER, Role.GENERATOR), true),
                Arguments.of(Sets.set(Role.SHARER), true),
                Arguments.of(Sets.set(Role.SHARER, Role.GENERATOR), true)
        );
    }

    @Test
    public void clientAddUnknownSiteId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        post(vertx, "api/client/add?name=test_client&roles=generator&site_id=4", "", expectHttpError(testContext, 404));
    }

    @Test
    public void clientAddSpecialSiteId1(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        post(vertx, "api/client/add?name=test_client&roles=generator&site_id=1", "", expectHttpError(testContext, 400));
    }

    @Test
    public void clientAddSpecialSiteId2(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        post(vertx, "api/client/add?name=test_client&roles=generator&site_id=2", "", expectHttpError(testContext, 400));
    }

    @Test
    public void clientUpdate(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setClientKeys(new ClientBuilder().withServiceId(165).build());

        post(vertx, "api/client/update?name=test_client&site_id=5&service_id=200", "", ar -> {
            HttpResponse<Buffer> response = ar.result();
            ClientKey expected = new ClientBuilder().withServiceId(200).build();
            try {
                assertAll(
                        "clientUpdate",
                        () -> assertTrue(ar.succeeded()),
                        () -> assertEquals(200, response.statusCode()),
                        () -> assertEquals(expected, OBJECT_MAPPER.readValue(response.bodyAsString(), ClientKey.class)),
                        () -> verify(clientKeyStoreWriter).upload(collectionOfSize(1), isNull())
                );
            } catch (Exception ex) {
                fail(ex);
            }
            testContext.completeNow();
        });
    }

    @ParameterizedTest
    @MethodSource("createSiteKeyIfNoneExistsTestData")
    public void clientUpdateCreatesSiteKeyIfNoneExists(Set<Role> roles, boolean siteKeyShouldBeCreatedIfNoneExists, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setClientKeys(
                new ClientBuilder()
                        .withRoles(roles)
                        .withSiteId(4)
                        .withServiceId(145)
                        .build()
        );

        post(vertx, "api/client/update?name=test_client&site_id=5&service_id=145", "", testContext.succeeding(response -> testContext.verify(() -> {
            assertAll(
                    "clientUpdateCreatesSiteKeyIfNoneExists",
                    () -> assertEquals(200, response.statusCode()),
                    () -> {
                        if (siteKeyShouldBeCreatedIfNoneExists) {
                            verify(keysetKeyManager).addKeysetKey(eq(4));
                        }
                    },
                    () -> verifyNoMoreInteractions(keysetKeyManager)
            );
            testContext.completeNow();
        })));
    }

    @Test
    public void clientUpdateUnknownClientName(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        post(vertx, "api/client/update?name=test_client&site_id=5", "", expectHttpError(testContext, 404));
    }

    @Test
    public void clientUpdateUnknownSiteId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setClientKeys(new ClientBuilder().build());
        post(vertx, "api/client/update?name=test_client&site_id=4", "", expectHttpError(testContext, 404));
    }

    @Test
    public void clientUpdateSpecialSiteId1(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setClientKeys(new ClientBuilder().build());
        post(vertx, "api/client/update?name=test_client&site_id=1", "", expectHttpError(testContext, 400));
    }

    @Test
    public void clientUpdateSpecialSiteId2(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setClientKeys(new ClientBuilder().build());
        post(vertx, "api/client/update?name=test_client&site_id=2", "", expectHttpError(testContext, 400));
    }

    @ParameterizedTest
    @MethodSource("createSiteKeyIfNoneExistsTestData")
    public void clientRolesCreatesSiteKeyIfNoneExists(Set<Role> roles, boolean siteKeyShouldBeCreatedIfNoneExists, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setClientKeys(new ClientBuilder().build());

        String endpoint = String.format("api/client/roles?name=test_client&roles=%s", RequestUtil.getRolesSpec(roles));
        post(vertx, endpoint, "", testContext.succeeding(response -> testContext.verify(() -> {
            assertAll(
                    "createSiteKeyIfNoneExistsTestData",
                    () -> assertEquals(200, response.statusCode()),
                    () -> {
                        if (siteKeyShouldBeCreatedIfNoneExists) {
                            verify(keysetKeyManager).addKeysetKey(4);
                        }
                    },
                    () -> verifyNoMoreInteractions(keysetKeyManager)
            );
            testContext.completeNow();
        })));
    }

    private static void assertAddedClientKeyEquals(ClientKey expected, ClientKey actual) {
        assertThat(actual)
                .usingRecursiveComparison()
                .ignoringFields("secret", "secretBytes", "created")
                .isEqualTo(expected);
    }

    private static class ClientBuilder {
        private String name = "test_client";
        private int siteId = 5;
        private Set<Role> roles = Set.of(Role.GENERATOR);
        private int serviceId = 0;

        public ClientBuilder withName(String name) {
            this.name = name;
            return this;
        }

        public ClientBuilder withSiteId(int siteId) {
            this.siteId = siteId;
            return this;
        }

        public ClientBuilder withRoles(Set<Role> roles) {
            this.roles = roles;
            return this;
        }

        public ClientBuilder withServiceId(int serviceId) {
            this.serviceId = serviceId;
            return this;
        }

        public ClientKey build() {
            return new ClientKey(
                    EXPECTED_CLIENT_KEY_HASH,
                    EXPECTED_CLIENT_KEY_SALT,
                    "",
                    name,
                    name,
                    0,
                    roles,
                    siteId,
                    false,
                    serviceId
            );
        }
    }
}
