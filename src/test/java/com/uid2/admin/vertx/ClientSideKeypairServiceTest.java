package com.uid2.admin.vertx;

import com.uid2.admin.secret.SecureKeypairGenerator;
import com.uid2.admin.store.Clock;
import com.uid2.admin.vertx.service.ClientSideKeypairService;
import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.ClientSideKeypair;
import com.uid2.shared.model.Site;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ClientSideKeypairServiceTest extends ServiceTestBase {

    private final Clock clock = mock(Clock.class);
    private static final long KEY_CREATE_TIME_IN_SECONDS = 1690680355L;

    private final String pub1 = "UID2-X-L-MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEhQ9i767j9beaz8sUhxkgrnW38gIUgG07+8+4ubb80NnikzLhVE7ZHd22haNF6iNNu8O7t7h21IizIifRkCC8OQ==";
    private final String pub2 = "UID2-X-L-MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE+igludojFNfaFcidrG13OdO8NnzMv6DfqCogaEP1JoQ/ciOA4RLx4djje8BtXddafFMPU8nG5qMomTSg67Lp+A==";
    private final String pub3 = "UID2-X-L-MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEoy42kazyAedMNvXoakdZWAMqbkr2TICCsAJzOpOtbYbxwsJgAFJso9NCJTSsvpb0ChivMkA6mesicVlGdLy1ng==";
    private final String pub4 = "UID2-X-L-MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEP5F7PslSFDWcTgasIc1x6183/JqI8WGOqXYxV2n7F6fAdZe8jLVvYtNhub2R+ZfXIDwdDepEZkuNSxfgwM27GA==";
    private final String priv1 = "UID2-Y-L-MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCAtmOklUGeCTv9XRp9cS9PIZAKW3bcntTVtzewaFw9/2A==";
    private final String priv2 = "UID2-Y-L-MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCAshNg/7jgVzpyueRlF73Y4YvH18P+4EUed5Pw5ZAbnqA==";
    private final String priv3 = "UID2-Y-L-MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCBt5EM8QQfaegeYWzxbFTkn+HRZmZ3kR0Eqeesv6aMHMA==";
    private final String priv4 = "UID2-Y-L-MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCDe6TIHd+Eyoczq1a8xeNGw17OWjeJHZwSLXtuMcqCXZQ==";
    private final String name1 = "name 1";
    private final String name2 = "name 2";
    private final String name3 = "name 3";
    private final String name4 = "name 4";
    @Override
    protected IService createService() {
        config.put("client_side_keypair_public_prefix", "UID2-X-L-");
        config.put("client_side_keypair_private_prefix", "UID2-Y-L-");
        return new ClientSideKeypairService(config, auth, writeLock, keypairStoreWriter, keypairProvider, siteProvider, keysetManager, new SecureKeypairGenerator(), clock);
    }
    @BeforeEach
    void setUp() {
        when(clock.now()).thenReturn(Instant.ofEpochSecond(KEY_CREATE_TIME_IN_SECONDS));
    }


    private void validateResponseKeypairs(Map<String, ClientSideKeypair> expectedKeypairs, Map<Integer, Site> sites, JsonArray respArray) {
        for(int i = 0; i < expectedKeypairs.size(); i++) {
            JsonObject resp = respArray.getJsonObject(i);
            String subscriptionId = resp.getString("subscription_id");
            ClientSideKeypair expectedKey = expectedKeypairs.get(subscriptionId);
            Site site = sites.get(expectedKey.getSiteId());
            validateKeypair(expectedKey, site.getName(), resp);
        }
    }

    private void validateKeypair(ClientSideKeypair expectedKeypair, String siteName, JsonObject resp) {
        assertEquals(expectedKeypair.getSubscriptionId(), resp.getString("subscription_id"));
        assertArrayEquals(expectedKeypair.getPublicKey().getEncoded(), Base64.getDecoder().decode(resp.getString("public_key").substring(ClientSideKeypair.KEYPAIR_KEY_PREFIX_LENGTH)));
        assertEquals(expectedKeypair.getSiteId(), resp.getInteger("site_id"));
        assertEquals(expectedKeypair.getContact(), resp.getString("contact"));
        assertEquals(expectedKeypair.getCreated().getEpochSecond(), resp.getLong("created"));
        assertEquals(expectedKeypair.isDisabled(), resp.getBoolean("disabled"));
        assertEquals("UID2-X-L-", resp.getString("public_key").substring(0, ClientSideKeypair.KEYPAIR_KEY_PREFIX_LENGTH));
        assertEquals(expectedKeypair.encodePublicKeyToString(), resp.getString("public_key"));
        assertEquals(siteName, resp.getString("site_name"));
    }

    private void validateKeypairWithPrivateKey(ClientSideKeypair expectedKeypair, JsonObject resp) {
        assertEquals(expectedKeypair.getSubscriptionId(), resp.getString("subscription_id"));
        assertArrayEquals(expectedKeypair.getPublicKey().getEncoded(), Base64.getDecoder().decode(resp.getString("public_key").substring(ClientSideKeypair.KEYPAIR_KEY_PREFIX_LENGTH)));
        assertArrayEquals(expectedKeypair.getPrivateKey().getEncoded(), Base64.getDecoder().decode(resp.getString("private_key").substring(ClientSideKeypair.KEYPAIR_KEY_PREFIX_LENGTH)));
        assertEquals(expectedKeypair.getSiteId(), resp.getInteger("site_id"));
        assertEquals(expectedKeypair.getContact(), resp.getString("contact"));
        assertEquals(expectedKeypair.getCreated().getEpochSecond(), resp.getLong("created"));
        assertEquals(expectedKeypair.isDisabled(), resp.getBoolean("disabled"));
        assertEquals("UID2-X-L-", resp.getString("public_key").substring(0, ClientSideKeypair.KEYPAIR_KEY_PREFIX_LENGTH));
        assertEquals("UID2-Y-L-", resp.getString("private_key").substring(0, ClientSideKeypair.KEYPAIR_KEY_PREFIX_LENGTH));
        assertEquals(expectedKeypair.encodePublicKeyToString(), resp.getString("public_key"));
        assertEquals(expectedKeypair.encodePrivateKeyToString(), resp.getString("private_key"));
    }

    @Test
    void listAllEmpty(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.MAINTAINER);

        setKeypairs(new ArrayList<>());

        get(vertx, testContext, "api/client_side_keypairs/list", response -> {
            assertEquals(200, response.statusCode());

            JsonArray respArray = response.bodyAsJsonArray();
            assertEquals(0, respArray.size());

            testContext.completeNow();
        });
    }
    @Test
    void listAll(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.MAINTAINER);

        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("aZ23456789", new ClientSideKeypair("aZ23456789", pub1, priv1, 123, "test@example.com", Instant.now(), false, name1));
            put("89aZ234567", new ClientSideKeypair("89aZ234567", pub2, priv2, 124, "test-two@example.com", Instant.now(), true, name2));
            put("9aZ2345678", new ClientSideKeypair("9aZ2345678", pub3, priv3, 123, "test@example.com", Instant.now(), true, name3));
            put("789aZ23456", new ClientSideKeypair("789aZ23456", pub4, priv4, 125, "test-two@example.com", Instant.now(), false, name4));
        }};
        setKeypairs(new ArrayList<>(expectedKeypairs.values()));
        Map<Integer, Site> sites = new HashMap<>() {{
            put(123, new Site(123, "site1", false));
            put(124, new Site(124, "site2", true));
            put(125, new Site(125, "site3", false, Set.of("test1.com", "test2.net")));
        }};
        setSites(sites.values().toArray(new Site[0]));

        get(vertx, testContext, "api/client_side_keypairs/list", response -> {
            assertEquals(200, response.statusCode());

            JsonArray respArray = response.bodyAsJsonArray();
            validateResponseKeypairs(expectedKeypairs, sites, respArray);

            testContext.completeNow();
        });
    }

    @Test
    void listKeypairSubscriptionIdNotFound(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.MAINTAINER);

        setKeypairs(new ArrayList<>());

        get(vertx, testContext, "api/client_side_keypairs/aZ23456789", response -> {
            assertEquals(404, response.statusCode());
            assertEquals("Failed to find a keypair for subscription id: aZ23456789", response.bodyAsJsonObject().getString("message"));
            testContext.completeNow();
        });
    }

    @Test
    void listKeypair(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.MAINTAINER);

        ClientSideKeypair queryKeypair = new ClientSideKeypair("aZ23456789", pub1, priv1, 123, "test@example.com", Instant.now(), false, name1);

        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("aZ23456789", queryKeypair);
            put("89aZ234567", new ClientSideKeypair("89aZ234567", pub2, priv2, 124, "test-two@example.com", Instant.now(), true, name2));
            put("9aZ2345678", new ClientSideKeypair("9aZ2345678", pub3, priv3, 123, "test@example.com", Instant.now(), true, name3));
            put("789aZ23456", new ClientSideKeypair("789aZ23456", pub4, priv4, 125, "test-two@example.com", Instant.now(), false, name4));
        }};
        setKeypairs(new ArrayList<>(expectedKeypairs.values()));
        setSites(new Site(123, "test", true));

        get(vertx, testContext, "api/client_side_keypairs/aZ23456789", response -> {
            assertEquals(200, response.statusCode());

            validateKeypairWithPrivateKey(queryKeypair, response.bodyAsJsonObject());

            testContext.completeNow();
        });
    }

    @Test
    void addKeypairNoSiteIdOrContact(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.MAINTAINER);

        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("89aZ234567", new ClientSideKeypair("89aZ234567", pub1, priv1, 124, "test-two@example.com", Instant.now(), true, name1));
            put("9aZ2345678", new ClientSideKeypair("9aZ2345678", pub2, priv2, 123, "test@example.com", Instant.now(), true, name2));
            put("789aZ23456", new ClientSideKeypair("789aZ23456", pub3, priv3, 125, "test-two@example.com", Instant.now(), false, name3));
        }};

        setKeypairs(new ArrayList<>(expectedKeypairs.values()));

        JsonObject jo = new JsonObject();

        post(vertx, testContext, "api/client_side_keypairs/add", jo.encode(), response -> {
            assertEquals(400, response.statusCode());
            assertEquals("Required parameters: site_id", response.bodyAsJsonObject().getString("message"));
            verify(keypairStoreWriter, times(0)).upload(any(), isNull());
            testContext.completeNow();
        });
    }

    @Test
    void addKeypairNoSiteId(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.MAINTAINER);

        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("89aZ234567", new ClientSideKeypair("89aZ234567", pub1, priv1, 124, "test-two@example.com", Instant.now(), true, name1));
            put("9aZ2345678", new ClientSideKeypair("9aZ2345678", pub2, priv2, 123, "test@example.com", Instant.now(), true, name2));
            put("789aZ23456", new ClientSideKeypair("789aZ23456", pub3, priv3, 125, "test-two@example.com", Instant.now(), false, name3));
        }};

        setKeypairs(new ArrayList<>(expectedKeypairs.values()));

        JsonObject jo = new JsonObject();
        jo.put("contact", "email@email.com");

        post(vertx, testContext, "api/client_side_keypairs/add", jo.encode(), response -> {
            assertEquals(400, response.statusCode());
            assertEquals("Required parameters: site_id", response.bodyAsJsonObject().getString("message"));
            verify(keypairStoreWriter, times(0)).upload(any(), isNull());
            testContext.completeNow();
        });
    }

    @Test
    void addKeypairBadSiteId(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.MAINTAINER);

        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("89aZ234567", new ClientSideKeypair("89aZ234567", pub1, priv1, 124, "test-two@example.com", Instant.now(), true, name1));
            put("9aZ2345678", new ClientSideKeypair("9aZ2345678", pub2, priv2, 123, "test@example.com", Instant.now(), true, name2));
            put("789aZ23456", new ClientSideKeypair("789aZ23456", pub3, priv3, 125, "test-two@example.com", Instant.now(), false, name3));
        }};

        setKeypairs(new ArrayList<>(expectedKeypairs.values()));

        JsonObject jo = new JsonObject();
        jo.put("site_id", 123);
        jo.put("contact", "contact@gmail.com");

        post(vertx, testContext, "api/client_side_keypairs/add", jo.encode(), response -> {
            assertEquals(404, response.statusCode());
            assertEquals("site_id: 123 not valid", response.bodyAsJsonObject().getString("message"));
            verify(keypairStoreWriter, times(0)).upload(any(), isNull());
            testContext.completeNow();
        });
    }

    @Test
    void addKeypair(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.SHARING_PORTAL);

        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("89aZ234567", new ClientSideKeypair("89aZ234567", pub1, priv1, 124, "test-two@example.com", Instant.now(), true, name1));
            put("9aZ2345678", new ClientSideKeypair("9aZ2345678", pub2, priv2, 123, "test@example.com", Instant.now(), true, name2));
            put("789aZ23456", new ClientSideKeypair("789aZ23456", pub3, priv3, 125, "test-two@example.com", Instant.now(), false, name3));
        }};

        setKeypairs(new ArrayList<>(expectedKeypairs.values()));
        setSites(new Site(123, "test", true));

        JsonObject jo = new JsonObject();
        jo.put("site_id", 123);
        jo.put("contact", "email@email.com");

        post(vertx, testContext, "api/client_side_keypairs/add", jo.encode(), response -> {
            final ArgumentCaptor<Integer> siteId = ArgumentCaptor.forClass(Integer.class);
            try {
                verify(this.keysetManager).createKeysetForSite(siteId.capture());
            } catch (Exception e) {
                fail(e);
            }
            assertEquals(123, siteId.getValue());
            assertEquals(200, response.statusCode());
            JsonObject resp = response.bodyAsJsonObject();
            assertEquals(123, resp.getInteger("site_id"));
            assertEquals("email@email.com", resp.getString("contact"));
            assertEquals(10, resp.getString("subscription_id").length());
            assertNotNull(resp.getString("public_key"));
            assertNull(resp.getString("private_key"));
            assertTrue(resp.getString("public_key").length() > 9);
            assertEquals("UID2-X-L-", resp.getString("public_key").substring(0, 9));
            assertEquals(KEY_CREATE_TIME_IN_SECONDS, resp.getLong("created"));
            assertEquals(false, resp.getBoolean("disabled"));
            verify(keypairStoreWriter, times(1)).upload(any(), isNull());
            testContext.completeNow();
        });
    }

    @Test
    void addKeypairNoContact(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.MAINTAINER);

        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("89aZ234567", new ClientSideKeypair("89aZ234567", pub1, priv1, 124, "test-two@example.com", Instant.now(), true, name1));
            put("9aZ2345678", new ClientSideKeypair("9aZ2345678", pub2, priv2, 123, "test@example.com", Instant.now(), true, name2));
            put("789aZ23456", new ClientSideKeypair("789aZ23456", pub3, priv3, 125, "test-two@example.com", Instant.now(), false, name3));
        }};

        setKeypairs(new ArrayList<>(expectedKeypairs.values()));
        setSites(new Site(123, "test", true));

        JsonObject jo = new JsonObject();
        jo.put("site_id", 123);

        post(vertx, testContext, "api/client_side_keypairs/add", jo.encode(), response -> {
            assertEquals(200, response.statusCode());
            JsonObject resp = response.bodyAsJsonObject();
            assertEquals(123, resp.getInteger("site_id"));
            assertEquals("", resp.getString("contact"));
            assertEquals(10, resp.getString("subscription_id").length());
            assertNotNull(resp.getString("public_key"));
            assertNull(resp.getString("private_key"));
            assertTrue(resp.getString("public_key").length() > 9);
            assertEquals("UID2-X-L-", resp.getString("public_key").substring(0, 9));
            assertEquals(KEY_CREATE_TIME_IN_SECONDS, resp.getLong("created"));
            assertEquals(false, resp.getBoolean("disabled"));
            assertEquals(false, resp.getBoolean("disabled"));
            verify(keypairStoreWriter, times(1)).upload(any(), isNull());
            testContext.completeNow();
        });
    }

    @Test
    void addKeypairDisabled(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.MAINTAINER);

        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("89aZ234567", new ClientSideKeypair("89aZ234567", pub1, priv1, 124, "test-two@example.com", Instant.now(), true, name1));
            put("9aZ2345678", new ClientSideKeypair("9aZ2345678", pub2, priv2, 123, "test@example.com", Instant.now(), true, name2));
            put("789aZ23456", new ClientSideKeypair("789aZ23456", pub3, priv3, 125, "test-two@example.com", Instant.now(), false, name3));
        }};

        setKeypairs(new ArrayList<>(expectedKeypairs.values()));
        setSites(new Site(123, "test", true));

        JsonObject jo = new JsonObject();
        jo.put("site_id", 123);
        jo.put("contact", "email@email.com");
        jo.put("disabled", true);

        post(vertx, testContext, "api/client_side_keypairs/add", jo.encode(), response -> {
            assertEquals(200, response.statusCode());
            JsonObject resp = response.bodyAsJsonObject();
            assertEquals(123, resp.getInteger("site_id"));
            assertEquals("email@email.com", resp.getString("contact"));
            assertEquals(10, resp.getString("subscription_id").length());
            assertNotNull(resp.getString("public_key"));
            assertNull(resp.getString("private_key"));
            assertTrue(resp.getString("public_key").length() > 0);
            assertEquals(KEY_CREATE_TIME_IN_SECONDS, resp.getLong("created"));
            assertEquals(true, resp.getBoolean("disabled"));
            verify(keypairStoreWriter, times(1)).upload(any(), isNull());
            testContext.completeNow();
        });
    }

    @Test
    void updateKeypairNoSubscriptionId(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.MAINTAINER);

        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("89aZ234567", new ClientSideKeypair("89aZ234567", pub1, priv1, 124, "test-two@example.com", Instant.now(), true, name1));
            put("9aZ2345678", new ClientSideKeypair("9aZ2345678", pub2, priv2, 123, "test@example.com", Instant.now(), true, name2));
            put("789aZ23456", new ClientSideKeypair("789aZ23456", pub3, priv3, 125, "test-two@example.com", Instant.now(), false, name3));
        }};

        setKeypairs(new ArrayList<>(expectedKeypairs.values()));
        setSites(new Site(123, "test", true));

        JsonObject jo = new JsonObject();
        jo.put("contact", "email@email.com");
        jo.put("disabled", true);

        post(vertx, testContext, "api/client_side_keypairs/update", jo.encode(), response -> {
            assertEquals(400, response.statusCode());
            assertEquals("Required parameters: subscription_id", response.bodyAsJsonObject().getString("message"));
            verify(keypairStoreWriter, times(0)).upload(any(), isNull());
            testContext.completeNow();
        });
    }

    @Test
    void updateKeypairBadSubscriptionId(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.MAINTAINER);

        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("89aZ234567", new ClientSideKeypair("89aZ234567", pub1, priv1, 124, "test-two@example.com", Instant.now(), true, name1));
            put("9aZ2345678", new ClientSideKeypair("9aZ2345678", pub2, priv2, 123, "test@example.com", Instant.now(), true, name2));
            put("789aZ23456", new ClientSideKeypair("789aZ23456", pub3, priv3, 125, "test-two@example.com", Instant.now(), false, name3));
        }};

        setKeypairs(new ArrayList<>(expectedKeypairs.values()));
        setSites(new Site(123, "test", true));

        JsonObject jo = new JsonObject();
        jo.put("subscription_id", "bad-id");
        jo.put("contact", "email@email.com");
        jo.put("disabled", true);

        post(vertx, testContext, "api/client_side_keypairs/update", jo.encode(), response -> {
            assertEquals(404, response.statusCode());
            assertEquals("Failed to find a keypair for subscription id: bad-id", response.bodyAsJsonObject().getString("message"));
            verify(keypairStoreWriter, times(0)).upload(any(), isNull());
            testContext.completeNow();
        });
    }

    @Test
    void updateKeypairNoUpdateParams(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.MAINTAINER);

        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("89aZ234567", new ClientSideKeypair("89aZ234567", pub1, priv1, 124, "test-two@example.com", Instant.now(), true, name1));
            put("9aZ2345678", new ClientSideKeypair("9aZ2345678", pub2, priv2, 123, "test@example.com", Instant.now(), true, name2));
            put("789aZ23456", new ClientSideKeypair("789aZ23456", pub3, priv3, 125, "test-two@example.com", Instant.now(), false, name3));
        }};

        setKeypairs(new ArrayList<>(expectedKeypairs.values()));
        setSites(new Site(123, "test", true));

        JsonObject jo = new JsonObject();
        jo.put("subscription_id", "89aZ234567");

        post(vertx, testContext, "api/client_side_keypairs/update", jo.encode(), response -> {
            assertEquals(400, response.statusCode());
            assertEquals("Updatable parameters: contact, disabled, name", response.bodyAsJsonObject().getString("message"));
            verify(keypairStoreWriter, times(0)).upload(any(), isNull());
            testContext.completeNow();
        });
    }

    @Test
    void updateKeypairContactOnly(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.MAINTAINER);

        Instant time = Instant.now();
        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("89aZ234567", new ClientSideKeypair("89aZ234567", pub1, priv1, 124, "test-two@example.com", time, true, name1));
            put("9aZ2345678", new ClientSideKeypair("9aZ2345678", pub2, priv2, 123, "test@example.com", Instant.now(), true, name2));
            put("789aZ23456", new ClientSideKeypair("789aZ23456", pub3, priv3, 125, "test-two@example.com", Instant.now(), false, name3));
        }};

        setKeypairs(new ArrayList<>(expectedKeypairs.values()));
        setSites(new Site(124, "test", true));

        JsonObject jo = new JsonObject();
        jo.put("subscription_id", "89aZ234567");
        jo.put("contact", "updated@email.com");

        post(vertx, testContext, "api/client_side_keypairs/update", jo.encode(), response -> {
            assertEquals(200, response.statusCode());
            ClientSideKeypair expected = new ClientSideKeypair("89aZ234567", pub1, priv1, 124, "updated@email.com", time, true, name1);
            validateKeypair(expected, "test", response.bodyAsJsonObject());
            verify(keypairStoreWriter, times(1)).upload(any(), isNull());
            testContext.completeNow();
        });
    }

    @Test
    void updateKeypairNameOnly(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.MAINTAINER);

        Instant time = Instant.now();
        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("89aZ234567", new ClientSideKeypair("89aZ234567", pub1, priv1, 124, "test-two@example.com", time, true, name1));
            put("9aZ2345678", new ClientSideKeypair("9aZ2345678", pub2, priv2, 123, "test@example.com", Instant.now(), true, name2));
            put("789aZ23456", new ClientSideKeypair("789aZ23456", pub3, priv3, 125, "test-two@example.com", Instant.now(), false, name3));
        }};

        setKeypairs(new ArrayList<>(expectedKeypairs.values()));
        setSites(new Site(124, "test", true));

        JsonObject jo = new JsonObject();
        jo.put("subscription_id", "89aZ234567");
        jo.put("name", "updated name");

        post(vertx, testContext, "api/client_side_keypairs/update", jo.encode(), response -> {
            assertEquals(200, response.statusCode());
            ClientSideKeypair expected = new ClientSideKeypair("89aZ234567", pub1, priv1, 124, "test-two@example.com", time, true, "updated name");
            validateKeypair(expected, "test", response.bodyAsJsonObject());
            verify(keypairStoreWriter, times(1)).upload(any(), isNull());
            testContext.completeNow();
        });
    }

    @Test
    void updateKeypairDisabledOnly(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.MAINTAINER);

        Instant time = Instant.now();
        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("89aZ234567", new ClientSideKeypair("89aZ234567", pub1, priv1, 124, "test-two@example.com", time, true, name1));
            put("9aZ2345678", new ClientSideKeypair("9aZ2345678", pub2, priv2, 123, "test@example.com", Instant.now(), true, name2));
            put("789aZ23456", new ClientSideKeypair("789aZ23456", pub3, priv3, 125, "test-two@example.com", Instant.now(), false, name3));
        }};

        setKeypairs(new ArrayList<>(expectedKeypairs.values()));
        setSites(new Site(124, "test", true));

        JsonObject jo = new JsonObject();
        jo.put("subscription_id", "89aZ234567");
        jo.put("disabled", false);

        post(vertx, testContext, "api/client_side_keypairs/update", jo.encode(), response -> {
            assertEquals(200, response.statusCode());
            ClientSideKeypair expected = new ClientSideKeypair("89aZ234567", pub1, priv1, 124, "test-two@example.com", time, false, name1);
            validateKeypair(expected, "test", response.bodyAsJsonObject());
            verify(keypairStoreWriter, times(1)).upload(any(), isNull());
            testContext.completeNow();
        });
    }

    @Test
    void updateKeypairDisabledAndContact(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.MAINTAINER);

        Instant time = Instant.now();
        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("89aZ234567", new ClientSideKeypair("89aZ234567", pub1, priv1, 124, "test-two@example.com", time, true, name1));
            put("9aZ2345678", new ClientSideKeypair("9aZ2345678", pub2, priv2, 123, "test@example.com", Instant.now(), true, name2));
            put("789aZ23456", new ClientSideKeypair("789aZ23456", pub3, priv3, 125, "test-two@example.com", Instant.now(), false, name3));
        }};

        setKeypairs(new ArrayList<>(expectedKeypairs.values()));
        setSites(new Site(124, "test", true));

        JsonObject jo = new JsonObject();
        jo.put("subscription_id", "89aZ234567");
        jo.put("contact", "updated@email.com");
        jo.put("disabled", false);

        post(vertx, testContext, "api/client_side_keypairs/update", jo.encode(), response -> {
            assertEquals(200, response.statusCode());
            ClientSideKeypair expected = new ClientSideKeypair("89aZ234567", pub1, priv1, 124, "updated@email.com", time, false, name1);
            validateKeypair(expected, "test", response.bodyAsJsonObject());
            verify(keypairStoreWriter, times(1)).upload(any(), isNull());
            testContext.completeNow();
        });
    }

    @Test
    void updateKeypairDisabledAndName(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.MAINTAINER);

        Instant time = Instant.now();
        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("89aZ234567", new ClientSideKeypair("89aZ234567", pub1, priv1, 124, "test-two@example.com", time, true, name1));
            put("9aZ2345678", new ClientSideKeypair("9aZ2345678", pub2, priv2, 123, "test@example.com", Instant.now(), true, name2));
            put("789aZ23456", new ClientSideKeypair("789aZ23456", pub3, priv3, 125, "test-two@example.com", Instant.now(), false, name3));
        }};

        setKeypairs(new ArrayList<>(expectedKeypairs.values()));
        setSites(new Site(124, "test", true));

        JsonObject jo = new JsonObject();
        jo.put("subscription_id", "89aZ234567");
        jo.put("name", "updated name");
        jo.put("disabled", false);

        post(vertx, testContext, "api/client_side_keypairs/update", jo.encode(), response -> {
            assertEquals(200, response.statusCode());
            ClientSideKeypair expected = new ClientSideKeypair("89aZ234567", pub1, priv1, 124, "test-two@example.com", time, false, "updated name");
            validateKeypair(expected, "test", response.bodyAsJsonObject());
            verify(keypairStoreWriter, times(1)).upload(any(), isNull());
            testContext.completeNow();
        });
    }

    @Test
    void deleteKeypairNoSubscriptionId(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.PRIVILEGED);

        setKeypairs(new ArrayList<>());

        JsonObject jo = new JsonObject();

        post(vertx, testContext, "api/client_side_keypairs/delete", jo.encode(), response -> {
            assertEquals(400, response.statusCode());
            assertEquals("Required parameters: subscription_id", response.bodyAsJsonObject().getString("message"));
            verify(keypairStoreWriter, times(0)).upload(any(), isNull());
            testContext.completeNow();
        });
    }

    @Test
    void deleteKeypairBadSubscriptionId(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.PRIVILEGED);

        Map<String, ClientSideKeypair> keypairs = new HashMap<>() {{
            put("89aZ234567", new ClientSideKeypair("89aZ234567", pub1, priv1, 124, "test@example.com", Instant.now(), false, name1));
            put("9aZ2345678", new ClientSideKeypair("9aZ2345678", pub2, priv2, 125, "test@example.com", Instant.now(), false, name2));
        }};
        setKeypairs(new ArrayList<>(keypairs.values()));

        JsonObject jo = new JsonObject();
        jo.put("subscription_id", "bad-id");

        post(vertx, testContext, "api/client_side_keypairs/delete", jo.encode(), response -> {
            assertEquals(404, response.statusCode());
            assertEquals("Failed to find a keypair for subscription id: bad-id", response.bodyAsJsonObject().getString("message"));
            verify(keypairStoreWriter, times(0)).upload(any(), isNull());
            testContext.completeNow();
        });
    }

    @Test
    void deleteKeypair(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.PRIVILEGED);

        Instant time = Instant.now();
        ClientSideKeypair keypairToDelete = new ClientSideKeypair("89aZ234567", pub1, priv1, 124, "test@example.com", time, false, name1);
        ClientSideKeypair remainingKeypair = new ClientSideKeypair("9aZ2345678", pub2, priv2, 124, "test@example.com", time, false, name2);

        setKeypairs(List.of(keypairToDelete, remainingKeypair));
        setSites(new Site(124, "test", true));

        JsonObject jo = new JsonObject();
        jo.put("subscription_id", "89aZ234567");

        post(vertx, testContext, "api/client_side_keypairs/delete", jo.encode(), response -> {
            assertEquals(200, response.statusCode());
            assertEquals(true, response.bodyAsJsonObject().getBoolean("success"));
            validateKeypair(keypairToDelete, "test", response.bodyAsJsonObject().getJsonObject("deleted_keypair"));
            verify(keypairStoreWriter, times(1)).upload(collectionOfSize(1), isNull());
            testContext.completeNow();
        });
    }

    private static Stream<Arguments> deleteRoles() {
        return Stream.of(
                Arguments.of(Role.MAINTAINER, 401, false),
                Arguments.of(Role.PRIVILEGED, 200, true),
                Arguments.of(Role.SHARING_PORTAL, 200, true)
        );
    }

    @ParameterizedTest
    @MethodSource("deleteRoles")
    void deleteKeypairAuthorization(Role role, int expectedStatus, boolean shouldSucceed, Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(role);

        Instant time = Instant.now();
        ClientSideKeypair keypairToDelete = new ClientSideKeypair("CC12345678", pub1, priv1, 222, "contact@example.com", time, false, name1);
        ClientSideKeypair remainingKeypair = new ClientSideKeypair("DD12345678", pub2, priv2, 222, "contact@example.com", time, false, name2);

        setKeypairs(List.of(keypairToDelete, remainingKeypair));
        setSites(new Site(222, "test", true));

        JsonObject jo = new JsonObject().put("subscription_id", "CC12345678");

        post(vertx, testContext, "api/client_side_keypairs/delete", jo.encode(), response -> {
            assertEquals(expectedStatus, response.statusCode());

            if (shouldSucceed) {
                assertTrue(response.bodyAsJsonObject().getBoolean("success"));
                validateKeypair(keypairToDelete, "test", response.bodyAsJsonObject().getJsonObject("deleted_keypair"));
                verify(keypairStoreWriter, times(1)).upload(collectionOfSize(1), isNull());
            } else {
                verify(keypairStoreWriter, times(0)).upload(any(), isNull());
            }
            testContext.completeNow();
        });
    }
}


