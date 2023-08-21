package com.uid2.admin.vertx;

import com.uid2.admin.managers.KeysetManager;
import com.uid2.admin.model.Site;
import com.uid2.admin.vertx.service.ClientKeyService;
import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.auth.ClientKey;
import com.uid2.shared.auth.Role;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.VertxTestContext;
import org.assertj.core.util.Sets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ClientKeyServiceTest extends ServiceTestBase {
    @Override
    protected IService createService() {
        KeysetManager keysetManager = new KeysetManager(adminKeysetProvider, adminKeysetWriter, keysetKeyManager, true);
        return new ClientKeyService(config, auth, writeLock, clientKeyStoreWriter, clientKeyProvider, siteProvider, keysetManager, keyGenerator);
    }

    private void checkClientKeyResponse(ClientKey[] expectedClients, Object[] actualClients) {
        assertEquals(expectedClients.length, actualClients.length);
        for (int i = 0; i < expectedClients.length; ++i) {
            ClientKey expectedClient = expectedClients[i];
            JsonObject actualClient = (JsonObject) actualClients[i];
            assertEquals(expectedClient.getName(), actualClient.getString("name"));
            assertEquals(expectedClient.getContact(), actualClient.getString("contact"));
            assertEquals(expectedClient.isDisabled(), actualClient.getBoolean("disabled"));
            assertEquals(expectedClient.getSiteId(), actualClient.getInteger("site_id"));

            List<Role> actualRoles = actualClient.getJsonArray("roles").stream()
                    .map(r -> Role.valueOf((String) r))
                    .collect(Collectors.toList());
            assertEquals(expectedClient.getRoles().size(), actualRoles.size());
            for (Role role : expectedClient.getRoles()) {
                assertTrue(actualRoles.contains(role));
            }
        }
    }

    @Test
    void clientRename(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites(new Site(5, "test_site", true, new HashSet<>()));
        setClientKeys(new ClientKey("", "","test_client").withRoles(Role.GENERATOR).withSiteId(5));
        ClientKey[] expectedClients = {
                new ClientKey("", "", "test_client1").withRoles(Role.GENERATOR).withSiteId(5)
        };

        post(vertx, "api/client/rename?oldName=test_client&newName=test_client1", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkClientKeyResponse(expectedClients, new Object[]{response.bodyAsJsonObject()});

            try {
                verify(clientKeyStoreWriter).upload(collectionOfSize(1), isNull());
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
    }

    @Test
    void clientRenameWithExistingName(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites(new Site(5, "test_site", true, new HashSet<>()));
        setClientKeys(
                new ClientKey("", "","test_client").withRoles(Role.GENERATOR).withSiteId(5),
                new ClientKey("", "","test_client1").withRoles(Role.GENERATOR).withSiteId(5));
        ClientKey[] expectedClients = {
                new ClientKey("", "", "test_client1").withRoles(Role.GENERATOR).withSiteId(5)
        };

        post(vertx, "api/client/rename?oldName=test_client&newName=test_client1", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());

            testContext.completeNow();
        });
    }

    @Test
    void clientAdd(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites(new Site(5, "test_site", true, new HashSet<>()));
        ClientKey[] expectedClients = {
                new ClientKey("", "", "test_client").withRoles(Role.GENERATOR).withSiteId(5)
        };

        post(vertx, "api/client/add?name=test_client&roles=generator&site_id=5", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkClientKeyResponse(expectedClients, new Object[]{response.bodyAsJsonObject()});

            try {
                verify(clientKeyStoreWriter).upload(collectionOfSize(1), isNull());
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
    }

    @ParameterizedTest
    @MethodSource("createSiteKeyIfNoneExistsTestData")
    void clientAddCreatesSiteKeyIfNoneExists(Set<Role> roles, boolean siteKeyShouldBeCreatedIfNoneExists, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites(new Site(5, "test_site", true, new HashSet<>()));

        final String endpoint = String.format("api/client/add?name=test_client&site_id=5&roles=%s", RequestUtil.getRolesSpec(roles));

        post(vertx, endpoint, "", testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(200, response.statusCode());

            if (siteKeyShouldBeCreatedIfNoneExists) {
                verify(keysetKeyManager).addKeysetKey(4);
            }
            verifyNoMoreInteractions(keysetKeyManager);

            testContext.completeNow();
        })));
    }

    static Stream<Arguments> createSiteKeyIfNoneExistsTestData() {
        return Stream.of(
            Arguments.of(Sets.set(Role.GENERATOR), true),
            Arguments.of(Sets.set(Role.ID_READER), false),
            Arguments.of(Sets.set(Role.ID_READER, Role.GENERATOR), true),
            Arguments.of(Sets.set(Role.SHARER), true),
            Arguments.of(Sets.set(Role.SHARER, Role.GENERATOR), true)
        );
    }

    @Test
    void clientAddUnknownSiteId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        post(vertx, "api/client/add?name=test_client&roles=generator&site_id=5", "", expectHttpError(testContext, 404));
    }

    @Test
    void clientAddSpecialSiteId1(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        post(vertx, "api/client/add?name=test_client&roles=generator&site_id=1", "", expectHttpError(testContext, 400));
    }

    @Test
    void clientAddSpecialSiteId2(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        post(vertx, "api/client/add?name=test_client&roles=generator&site_id=2", "", expectHttpError(testContext, 400));
    }

    @Test
    void clientUpdate(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites(new Site(5, "test_site", true, new HashSet<>()));
        setClientKeys(new ClientKey("", "","test_client").withRoles(Role.GENERATOR).withSiteId(4));
        ClientKey[] expectedClients = {
                new ClientKey("", "", "test_client").withRoles(Role.GENERATOR).withSiteId(5)
        };

        post(vertx, "api/client/update?name=test_client&site_id=5", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkClientKeyResponse(expectedClients, new Object[]{response.bodyAsJsonObject()});

            try {
                verify(clientKeyStoreWriter).upload(collectionOfSize(1), isNull());
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
    }

    @ParameterizedTest
    @MethodSource("createSiteKeyIfNoneExistsTestData")
    void clientUpdateCreatesSiteKeyIfNoneExists(Set<Role> roles, boolean siteKeyShouldBeCreatedIfNoneExists, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites(new Site(5, "test_site", true, new HashSet<>()));
        setClientKeys(new ClientKey("", "","test_client")
                .withRoles(roles)
                .withSiteId(4));

        post(vertx, "api/client/update?name=test_client&site_id=5", "", testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(200, response.statusCode());

            if (siteKeyShouldBeCreatedIfNoneExists) {
                verify(keysetKeyManager).addKeysetKey(eq(4));
            }
            verifyNoMoreInteractions(keysetKeyManager);

            testContext.completeNow();
        })));
    }

    @Test
    void clientUpdateUnknownClientName(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites(new Site(5, "test_site", true, new HashSet<>()));
        post(vertx, "api/client/update?name=test_client&site_id=5", "", expectHttpError(testContext, 404));
    }

    @Test
    void clientUpdateUnknownSiteId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setClientKeys(new ClientKey("", "","test_client").withRoles(Role.GENERATOR).withSiteId(4));
        post(vertx, "api/client/update?name=test_client&site_id=5", "", expectHttpError(testContext, 404));
    }

    @Test
    void clientUpdateSpecialSiteId1(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setClientKeys(new ClientKey("", "","test_client").withRoles(Role.GENERATOR).withSiteId(4));
        post(vertx, "api/client/update?name=test_client&site_id=1", "", expectHttpError(testContext, 400));
    }

    @Test
    void clientUpdateSpecialSiteId2(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setClientKeys(new ClientKey("", "","test_client").withRoles(Role.GENERATOR).withSiteId(4));
        post(vertx, "api/client/update?name=test_client&site_id=2", "", expectHttpError(testContext, 400));
    }

    @ParameterizedTest
    @MethodSource("createSiteKeyIfNoneExistsTestData")
    void clientRolesCreatesSiteKeyIfNoneExists(Set<Role> roles, boolean siteKeyShouldBeCreatedIfNoneExists, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setClientKeys(new ClientKey("", "","test_client").withRoles(Role.GENERATOR).withSiteId(5));

        final String endpoint = String.format("api/client/roles?name=test_client&roles=%s", RequestUtil.getRolesSpec(roles));

        post(vertx, endpoint, "", testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(200, response.statusCode());

            if (siteKeyShouldBeCreatedIfNoneExists) {
                verify(keysetKeyManager).addKeysetKey(4);
            }
            verifyNoMoreInteractions(keysetKeyManager);

            testContext.completeNow();
        })));
    }
}
