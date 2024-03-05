package com.uid2.admin.vertx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uid2.admin.auth.RevealedKey;
import com.uid2.admin.legacy.LegacyClientKey;
import com.uid2.admin.managers.KeysetManager;
import com.uid2.admin.vertx.service.ClientKeyService;
import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.auth.ClientKey;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.Site;
import com.uid2.shared.util.Mapper;
import io.vertx.core.Vertx;
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
        setSites(new Site(999, "test_site", true));
    }

    @Test
    public void clientRename(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.DEFAULT);
        setClientKeys(new LegacyClientBuilder().build());

        post(vertx, testContext, "api/client/rename?contact=test_contact&newName=test_client1", "", response -> {
            ClientKey expected = new LegacyClientBuilder().withName("test_client1").build().toClientKey();
            assertAll(
                    "clientRename",
                    () -> assertEquals(200, response.statusCode()),
                    () -> assertEquals(expected, OBJECT_MAPPER.readValue(response.bodyAsString(), ClientKey.class)),
                    () -> verify(clientKeyStoreWriter).upload(collectionOfSize(1), isNull())
            );
            testContext.completeNow();
        });
    }

    @Test
    public void clientRenameBlankNewName(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.DEFAULT);
        setClientKeys(new LegacyClientBuilder().build());

        post(vertx, testContext, "api/client/rename?contact=test_contact&newName=", "", expectHttpStatus(testContext, 400));
    }

    @Test
    public void clientRenameInvalidContact(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.DEFAULT);

        post(vertx, testContext, "api/client/rename?contact=test_contact&newName=testing", "", expectHttpStatus(testContext, 404));
    }

    @Test
    public void clientAdd(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.DEFAULT);

        LegacyClientKey expectedClient = new LegacyClientBuilder().withServiceId(145).build();
        post(vertx, testContext, "api/client/add?name=test_client&roles=generator&site_id=999&service_id=145", "", response -> {
            RevealedKey<ClientKey> revealedClient = OBJECT_MAPPER.readValue(response.bodyAsString(), new TypeReference<>() {});

            assertAll(
                    "clientAdd",
                    () -> assertEquals(200, response.statusCode()),
                    () -> assertAddedClientKeyEquals(expectedClient.toClientKey(), revealedClient.getAuthorizable()),
                    () -> assertNotNull(revealedClient.getPlaintextKey()),
                    () -> verify(clientKeyStoreWriter).upload(collectionOfSize(1), isNull())
            );
            testContext.completeNow();
        });
    }

    @ParameterizedTest
    @MethodSource("createSiteKeyIfNoneExistsTestData")
    public void clientAddCreatesSiteKeyIfNoneExists(Set<Role> roles, boolean siteKeyShouldBeCreatedIfNoneExists, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.DEFAULT);

        String endpoint = String.format("api/client/add?name=test_client&site_id=999&roles=%s", RequestUtil.getRolesSpec(roles));
        post(vertx, testContext, endpoint, "", response -> {
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
        });
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
        fakeAuth(Role.DEFAULT);
        post(vertx, testContext, "api/client/add?name=test_client&roles=generator&site_id=4", "", expectHttpStatus(testContext, 404));
    }

    @Test
    public void clientAddSpecialSiteId1(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.DEFAULT);
        post(vertx, testContext, "api/client/add?name=test_client&roles=generator&site_id=1", "", expectHttpStatus(testContext, 400));
    }

    @Test
    public void clientAddSpecialSiteId2(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.DEFAULT);
        post(vertx, testContext, "api/client/add?name=test_client&roles=generator&site_id=2", "", expectHttpStatus(testContext, 400));
    }

    @Test
    public void clientAddInvalidRole(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.DEFAULT);
        post(vertx, testContext, "api/client/add?name=test_client&roles=invalid&site_id=999", "", expectHttpStatus(testContext, 400));
    }

    @Test
    public void clientAddBlankName(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.DEFAULT);
        post(vertx, testContext, "api/client/add?name=&roles=generator&site_id=999", "", expectHttpStatus(testContext, 400));
    }

    @Test
    public void clientUpdate(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.DEFAULT);
        setClientKeys(new LegacyClientBuilder().withServiceId(165).build());

        post(vertx, testContext, "api/client/update?contact=test_contact&site_id=999&service_id=200", "", response -> {
            ClientKey expected = new LegacyClientBuilder().withServiceId(200).build().toClientKey();
            assertAll(
                    "clientUpdate",
                    () -> assertEquals(200, response.statusCode()),
                    () -> assertEquals(expected, OBJECT_MAPPER.readValue(response.bodyAsString(), ClientKey.class)),
                    () -> verify(clientKeyStoreWriter).upload(collectionOfSize(1), isNull())
            );
            testContext.completeNow();
        });
    }

    @ParameterizedTest
    @MethodSource("createSiteKeyIfNoneExistsTestData")
    public void clientUpdateCreatesSiteKeyIfNoneExists(Set<Role> roles, boolean siteKeyShouldBeCreatedIfNoneExists, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.DEFAULT);
        setClientKeys(
                new LegacyClientBuilder()
                        .withRoles(roles)
                        .withSiteId(4)
                        .withServiceId(145)
                        .build()
        );

        post(vertx, testContext, "api/client/update?contact=test_contact&site_id=999&service_id=145", "", response -> {
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
        });
    }

    @Test
    public void clientUpdateUnknownClientName(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.DEFAULT);
        post(vertx, testContext, "api/client/update?contact=test_contact&site_id=999", "", expectHttpStatus(testContext, 404));
    }

    @Test
    public void clientUpdateUnknownSiteId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.DEFAULT);
        setClientKeys(new LegacyClientBuilder().build());
        post(vertx, testContext, "api/client/update?contact=test_contact&site_id=4", "", expectHttpStatus(testContext, 404));
    }

    @Test
    public void clientUpdateSpecialSiteId1(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.DEFAULT);
        setClientKeys(new LegacyClientBuilder().build());
        post(vertx, testContext, "api/client/update?contact=test_contact&site_id=1", "", expectHttpStatus(testContext, 400));
    }

    @Test
    public void clientUpdateSpecialSiteId2(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.DEFAULT);
        setClientKeys(new LegacyClientBuilder().build());
        post(vertx, testContext, "api/client/update?contact=test_contact&site_id=2", "", expectHttpStatus(testContext, 400));
    }

    @ParameterizedTest
    @MethodSource("createSiteKeyIfNoneExistsTestData")
    public void clientRolesCreatesSiteKeyIfNoneExists(Set<Role> roles, boolean siteKeyShouldBeCreatedIfNoneExists, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);
        setClientKeys(new LegacyClientBuilder().build());

        String endpoint = String.format("api/client/roles?contact=test_contact&roles=%s", RequestUtil.getRolesSpec(roles));
        post(vertx, testContext, endpoint, "", response -> {
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
        });
    }

    @Test
    public void updateRoles(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);
        setClientKeys(new LegacyClientBuilder().build());

        Set<Role> newRoles = Set.of(Role.SHARER, Role.MAPPER);

        post(vertx, testContext, "api/client/roles?contact=test_contact&roles=" + RequestUtil.getRolesSpec(newRoles), "", response -> {
            ClientKey expected = new LegacyClientBuilder().withRoles(newRoles).build().toClientKey();
            assertAll(
                    "clientUpdate",
                    () -> assertEquals(200, response.statusCode()),
                    () -> assertEquals(expected, OBJECT_MAPPER.readValue(response.bodyAsString(), ClientKey.class)),
                    () -> verify(clientKeyStoreWriter).upload(collectionOfSize(1), isNull())
            );
            testContext.completeNow();
        });
    }

    @Test
    public void updateRolesWithInvalidContact(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        Set<Role> newRoles = Set.of(Role.SHARER, Role.MAPPER);

        post(vertx, testContext, "api/client/roles?contact=test_contact&roles=" + RequestUtil.getRolesSpec(newRoles), "", expectHttpStatus(testContext, 404));
    }

    @Test
    public void updateRolesWithNoRoles(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);
        setClientKeys(new LegacyClientBuilder().build());

        post(vertx, testContext, "api/client/roles?contact=test_contact&roles=", "", expectHttpStatus(testContext, 400));
    }

    @Test
    public void updateRolesWithInvalidRoles(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);
        setClientKeys(new LegacyClientBuilder().build());

        post(vertx, testContext, "api/client/roles?contact=test_contact&roles=invalid", "", expectHttpStatus(testContext, 400));
    }

    @Test
    public void listBySiteStringId(Vertx vertx, VertxTestContext testContext){
        fakeAuth(Role.DEFAULT);

        get(vertx, testContext, "api/client/list/test", expectHttpStatus(testContext, 400));
    }

    @Test
    public void listBySiteInvalidId(Vertx vertx, VertxTestContext testContext){
        fakeAuth(Role.DEFAULT);

        get(vertx, testContext, "api/client/list/0", expectHttpStatus(testContext, 400));
    }

    @Test
    public void listBySiteUnusedId(Vertx vertx, VertxTestContext testContext){
        fakeAuth(Role.DEFAULT);

        get(vertx, testContext, "api/client/list/100", expectHttpStatus(testContext, 404));
    }

    @Test
    public void listBySite(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.DEFAULT);

        setSites(
                new Site(666, "test2_site", true),
                new Site(999, "test_site", true)
        );
        setClientKeys(
                new LegacyClientBuilder().withSiteId(999).withName("999").build(),
                new LegacyClientBuilder().withSiteId(666).withName("666").build()
        );

        get(vertx, testContext, "api/client/list/999", response -> {
            assertAll(
                    "Should just get key for this site",
                    () -> assertEquals(200, response.statusCode()),
                    () -> assertEquals(response.bodyAsJsonArray().size(), 1)
            );
            testContext.completeNow();
        });
    }

    @Test
    public void getByKeyId(Vertx vertx, VertxTestContext testContext){
        fakeAuth(Role.DEFAULT);

        setClientKeys(
                new LegacyClientBuilder().withKeyId("testId1").build(),
                new LegacyClientBuilder().withKeyId("testId2").build()
        );

        get(vertx, testContext, "/api/client/keyId?keyId=testId1", response -> {
            assertAll(
                    "clientUpdate",
                    () -> assertEquals(200, response.statusCode()),
                    () -> assertEquals("testId1", response.bodyAsJsonObject().getString("key_id"))
            );
            testContext.completeNow();
        });
    }

    @Test
    public void getByContactInvalidContact(Vertx vertx, VertxTestContext testContext){
        fakeAuth(Role.DEFAULT);

        get(vertx, testContext, "api/client/contact?contact=100", expectHttpStatus(testContext, 404));
    }

    @Test
    public void getByContactNoGivenContact(Vertx vertx, VertxTestContext testContext){
        fakeAuth(Role.DEFAULT);

        get(vertx, testContext, "api/client/contact", expectHttpStatus(testContext, 400));
    }

    @Test
    public void getByContact(Vertx vertx, VertxTestContext testContext){
        fakeAuth(Role.DEFAULT);

        setClientKeys(
                new LegacyClientBuilder().withContact("testContact1").build(),
                new LegacyClientBuilder().withContact("testContact2").build()
        );

        get(vertx, testContext, "/api/client/contact?contact=testContact1", response -> {
            assertAll(
                    "clientUpdate",
                    () -> assertEquals(200, response.statusCode()),
                    () -> assertEquals("testContact1", response.bodyAsJsonObject().getString("contact"))
            );
            testContext.completeNow();
        });
    }

    @Test
    public void getByKeyIdInvalidId(Vertx vertx, VertxTestContext testContext){
        fakeAuth(Role.DEFAULT);

        get(vertx, testContext, "api/client/keyId?keyId=100", expectHttpStatus(testContext, 404));
    }

    @Test
    public void getByKeyIdNoGivenId(Vertx vertx, VertxTestContext testContext){
        fakeAuth(Role.DEFAULT);

        get(vertx, testContext, "api/client/keyId", expectHttpStatus(testContext, 400));
    }

    @Test
    public void setContact(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.DEFAULT);

        setClientKeys(new LegacyClientBuilder().build());

        post(vertx, testContext, "api/client/contact?oldContact=test_contact&newContact=test_contact1", "", response -> {
            ClientKey expected = new LegacyClientBuilder().withContact("test_contact1").build().toClientKey();
            assertAll(
                    "clientSetContact",
                    () -> assertEquals(200, response.statusCode()),
                    () -> assertEquals(expected, OBJECT_MAPPER.readValue(response.bodyAsString(), ClientKey.class)),
                    () -> verify(clientKeyStoreWriter).upload(collectionOfSize(1), isNull())
            );
            testContext.completeNow();
        });
    }

    @Test
    public void setContactWithExistingContact(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.DEFAULT);

        setClientKeys(
                new LegacyClientBuilder().build(),
                new LegacyClientBuilder().withContact("test_contact1").build()
        );

        post(vertx, testContext, "api/client/contact?oldContact=test_contact&newContact=test_contact1", "", expectHttpStatus(testContext, 400));
    }

    @Test
    public void setContactWithUnknownContact(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.DEFAULT);

        post(vertx, testContext, "api/client/contact?oldContact=test_contact&newContact=test_contact1", "", expectHttpStatus(testContext, 404));
    }

    @Test
    public void setContactWithBlankNewContact(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.DEFAULT);

        setClientKeys(
                new LegacyClientBuilder().build()
        );

        post(vertx, testContext, "api/client/contact?oldContact=test_contact&newContact=", "", expectHttpStatus(testContext, 400));
    }

    private static void assertAddedClientKeyEquals(ClientKey expected, ClientKey actual) {
        assertThat(actual)
                .usingRecursiveComparison()
                .ignoringFields("secret", "secretBytes", "created", "contact")
                .isEqualTo(expected);
    }

    private static class LegacyClientBuilder {
        private String name = "test_client";
        private String contact = "test_contact";
        private int siteId = 999;
        private Set<Role> roles = Set.of(Role.GENERATOR);
        private int serviceId = 0;
        private String keyId = "UID2-C-L-999-abcde";

        public LegacyClientBuilder withName(String name) {
            this.name = name;
            return this;
        }

        public LegacyClientBuilder withContact(String contact) {
            this.contact = contact;
            return this;
        }

        public LegacyClientBuilder withSiteId(int siteId) {
            this.siteId = siteId;
            return this;
        }

        public LegacyClientBuilder withRoles(Set<Role> roles) {
            this.roles = roles;
            return this;
        }

        public LegacyClientBuilder withServiceId(int serviceId) {
            this.serviceId = serviceId;
            return this;
        }

        public LegacyClientBuilder withKeyId(String keyId) {
            this.keyId = keyId;
            return this;
        }

        public LegacyClientKey build() {
            return new LegacyClientKey(
                    "UID2-C-L-999-abcdeM.fsR3mDqAXELtWWMS+xG1s7RdgRTMqdOH2qaAo=",
                    EXPECTED_CLIENT_KEY_HASH,
                    EXPECTED_CLIENT_KEY_SALT,
                    "",
                    name,
                    contact,
                    0,
                    roles,
                    siteId,
                    false,
                    serviceId,
                    keyId
            );
        }
    }
}
