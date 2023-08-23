package com.uid2.admin.vertx;

import com.uid2.admin.secret.SecureKeypairGenerator;
import com.uid2.admin.store.Clock;
import com.uid2.admin.vertx.service.ClientSideKeypairService;
import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.ClientSideKeypair;
import com.uid2.shared.model.Site;
import com.uid2.shared.secure.gcpoidc.Environment;
import com.uid2.shared.secure.gcpoidc.IdentityScope;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ClientSideKeypairServiceTest extends ServiceTestBase {

    private final Clock clock = mock(Clock.class);
    private static final long KEY_CREATE_TIME_IN_SECONDS = 1690680355L;

    private final String pub1 = "UID2-X-T-MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEhQ9i767j9beaz8sUhxkgrnW38gIUgG07+8+4ubb80NnikzLhVE7ZHd22haNF6iNNu8O7t7h21IizIifRkCC8OQ==";
    private final String pub2 = "UID2-X-T-MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE+igludojFNfaFcidrG13OdO8NnzMv6DfqCogaEP1JoQ/ciOA4RLx4djje8BtXddafFMPU8nG5qMomTSg67Lp+A==";
    private final String pub3 = "UID2-X-T-MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEoy42kazyAedMNvXoakdZWAMqbkr2TICCsAJzOpOtbYbxwsJgAFJso9NCJTSsvpb0ChivMkA6mesicVlGdLy1ng==";
    private final String pub4 = "UID2-X-T-MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEP5F7PslSFDWcTgasIc1x6183/JqI8WGOqXYxV2n7F6fAdZe8jLVvYtNhub2R+ZfXIDwdDepEZkuNSxfgwM27GA==";
    private final String priv1 = "UID2-Y-T-MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCAtmOklUGeCTv9XRp9cS9PIZAKW3bcntTVtzewaFw9/2A==";
    private final String priv2 = "UID2-Y-T-MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCAshNg/7jgVzpyueRlF73Y4YvH18P+4EUed5Pw5ZAbnqA==";
    private final String priv3 = "UID2-Y-T-MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCBt5EM8QQfaegeYWzxbFTkn+HRZmZ3kR0Eqeesv6aMHMA==";
    private final String priv4 = "UID2-Y-T-MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCDe6TIHd+Eyoczq1a8xeNGw17OWjeJHZwSLXtuMcqCXZQ==";
    @Override
    protected IService createService() {
        JsonObject config = new JsonObject();
        config.put("client_side_keypair_public_prefix", "UID2-X-T-");
        config.put("client_side_keypair_private_prefix", "UID2-Y-T-");
        return new ClientSideKeypairService(config, auth, writeLock, keypairStoreWriter, keypairProvider, siteProvider, new SecureKeypairGenerator(), clock);
    }
    @BeforeEach
    void setUp() {
        when(clock.now()).thenReturn(Instant.ofEpochSecond(KEY_CREATE_TIME_IN_SECONDS));
    }


    private void validateResponseKeypairs(Map<String, ClientSideKeypair> expectedKeypairs, JsonArray respArray) {
        for(int i = 0; i < expectedKeypairs.size(); i++) {
            JsonObject resp = respArray.getJsonObject(i);
            String subscriptionId = resp.getString("subscription_id");
            validateKeypair(expectedKeypairs.get(subscriptionId), resp);
        }
    }

    private void validateKeypair(ClientSideKeypair expectedKeypair, JsonObject resp) {
        assertEquals(expectedKeypair.getSubscriptionId(), resp.getString("subscription_id"));
        assertArrayEquals(expectedKeypair.getPublicKey().getEncoded(), Base64.getDecoder().decode(resp.getString("public_key").substring(ClientSideKeypair.KEYPAIR_KEY_PREFIX_LENGTH)));
        assertEquals(expectedKeypair.getSiteId(), resp.getInteger("site_id"));
        assertEquals(expectedKeypair.getContact(), resp.getString("contact"));
        assertEquals(expectedKeypair.getCreated().getEpochSecond(), resp.getLong("created"));
        assertEquals(expectedKeypair.isDisabled(), resp.getBoolean("disabled"));
        assertEquals("UID2-X-T-", resp.getString("public_key").substring(0, ClientSideKeypair.KEYPAIR_KEY_PREFIX_LENGTH));
        assertEquals(expectedKeypair.encodePublicKeyToString(), resp.getString("public_key"));
    }

    private void validateKeypairWithPrivateKey(ClientSideKeypair expectedKeypair, JsonObject resp) {
        assertEquals(expectedKeypair.getSubscriptionId(), resp.getString("subscription_id"));
        assertArrayEquals(expectedKeypair.getPublicKey().getEncoded(), Base64.getDecoder().decode(resp.getString("public_key").substring(ClientSideKeypair.KEYPAIR_KEY_PREFIX_LENGTH)));
        assertArrayEquals(expectedKeypair.getPrivateKey().getEncoded(), Base64.getDecoder().decode(resp.getString("private_key").substring(ClientSideKeypair.KEYPAIR_KEY_PREFIX_LENGTH)));
        assertEquals(expectedKeypair.getSiteId(), resp.getInteger("site_id"));
        assertEquals(expectedKeypair.getContact(), resp.getString("contact"));
        assertEquals(expectedKeypair.getCreated().getEpochSecond(), resp.getLong("created"));
        assertEquals(expectedKeypair.isDisabled(), resp.getBoolean("disabled"));
        assertEquals("UID2-X-T-", resp.getString("public_key").substring(0, ClientSideKeypair.KEYPAIR_KEY_PREFIX_LENGTH));
        assertEquals("UID2-Y-T-", resp.getString("private_key").substring(0, ClientSideKeypair.KEYPAIR_KEY_PREFIX_LENGTH));
        assertEquals(expectedKeypair.encodePublicKeyToString(), resp.getString("public_key"));
        assertEquals(expectedKeypair.encodePrivateKeyToString(), resp.getString("private_key"));
    }

    @Test
    void listAllEmpty(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.ADMINISTRATOR);

        setKeypairs(new ArrayList<>());

        get(vertx, "api/client_side_keypairs/list", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            JsonArray respArray = response.bodyAsJsonArray();
            assertEquals(0, respArray.size());

            testContext.completeNow();
        });
    }
    @Test
    void listAll(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.ADMINISTRATOR);

        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("aZ23456789", new ClientSideKeypair("aZ23456789", pub1, priv1, 123, "test@example.com", Instant.now(), false));
            put("89aZ234567", new ClientSideKeypair("89aZ234567", pub2, priv2, 124, "test-two@example.com", Instant.now(), true));
            put("9aZ2345678", new ClientSideKeypair("9aZ2345678", pub3, priv3, 123, "test@example.com", Instant.now(), true));
            put("789aZ23456", new ClientSideKeypair("789aZ23456", pub4, priv4, 125, "test-two@example.com", Instant.now(), false));
        }};
        setKeypairs(new ArrayList<>(expectedKeypairs.values()));

        get(vertx, "api/client_side_keypairs/list", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            JsonArray respArray = response.bodyAsJsonArray();
            validateResponseKeypairs(expectedKeypairs, respArray);

            testContext.completeNow();
        });
    }

    @Test
    void listKeypairSubscriptionIdNotFound(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.ADMINISTRATOR);

        setKeypairs(new ArrayList<>());

        get(vertx, "api/client_side_keypairs/aZ23456789", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(404, response.statusCode());
            assertEquals("Failed to find a keypair for subscription id: aZ23456789", response.bodyAsJsonObject().getString("message"));
            testContext.completeNow();
        });
    }

    @Test
    void listKeypair(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.ADMINISTRATOR);

        ClientSideKeypair queryKeypair = new ClientSideKeypair("aZ23456789", pub1, priv1, 123, "test@example.com", Instant.now(), false);

        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("aZ23456789", queryKeypair);
            put("89aZ234567", new ClientSideKeypair("89aZ234567", pub2, priv2, 124, "test-two@example.com", Instant.now(), true));
            put("9aZ2345678", new ClientSideKeypair("9aZ2345678", pub3, priv3, 123, "test@example.com", Instant.now(), true));
            put("789aZ23456", new ClientSideKeypair("789aZ23456", pub4, priv4, 125, "test-two@example.com", Instant.now(), false));
        }};
        setKeypairs(new ArrayList<>(expectedKeypairs.values()));

        get(vertx, "api/client_side_keypairs/aZ23456789", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            validateKeypairWithPrivateKey(queryKeypair, response.bodyAsJsonObject());

            testContext.completeNow();
        });
    }

    @Test
    void addKeypairNoSiteIdOrContact(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.ADMINISTRATOR);

        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("89aZ234567", new ClientSideKeypair("89aZ234567", pub1, priv1, 124, "test-two@example.com", Instant.now(), true));
            put("9aZ2345678", new ClientSideKeypair("9aZ2345678", pub2, priv2, 123, "test@example.com", Instant.now(), true));
            put("789aZ23456", new ClientSideKeypair("789aZ23456", pub3, priv3, 125, "test-two@example.com", Instant.now(), false));
        }};

        setKeypairs(new ArrayList<>(expectedKeypairs.values()));

        JsonObject jo = new JsonObject();

        post(vertx, "api/client_side_keypairs/add", jo.encode(), ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            assertEquals("Required parameters: site_id", response.bodyAsJsonObject().getString("message"));
            testContext.completeNow();
        });
    }

    @Test
    void addKeypairNoSiteId(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.ADMINISTRATOR);

        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("89aZ234567", new ClientSideKeypair("89aZ234567", pub1, priv1, 124, "test-two@example.com", Instant.now(), true));
            put("9aZ2345678", new ClientSideKeypair("9aZ2345678", pub2, priv2, 123, "test@example.com", Instant.now(), true));
            put("789aZ23456", new ClientSideKeypair("789aZ23456", pub3, priv3, 125, "test-two@example.com", Instant.now(), false));
        }};

        setKeypairs(new ArrayList<>(expectedKeypairs.values()));

        JsonObject jo = new JsonObject();
        jo.put("contact", "email@email.com");

        post(vertx, "api/client_side_keypairs/add", jo.encode(), ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            assertEquals("Required parameters: site_id", response.bodyAsJsonObject().getString("message"));
            testContext.completeNow();
        });
    }

    @Test
    void addKeypairBadSiteId(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.ADMINISTRATOR);

        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("89aZ234567", new ClientSideKeypair("89aZ234567", pub1, priv1, 124, "test-two@example.com", Instant.now(), true));
            put("9aZ2345678", new ClientSideKeypair("9aZ2345678", pub2, priv2, 123, "test@example.com", Instant.now(), true));
            put("789aZ23456", new ClientSideKeypair("789aZ23456", pub3, priv3, 125, "test-two@example.com", Instant.now(), false));
        }};

        setKeypairs(new ArrayList<>(expectedKeypairs.values()));

        JsonObject jo = new JsonObject();
        jo.put("site_id", 123);
        jo.put("contact", "contact@gmail.com");

        post(vertx, "api/client_side_keypairs/add", jo.encode(), ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(404, response.statusCode());
            assertEquals("site_id: 123 not valid", response.bodyAsJsonObject().getString("message"));
            testContext.completeNow();
        });
    }

    @Test
    void addKeypair(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.ADMINISTRATOR);

        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("89aZ234567", new ClientSideKeypair("89aZ234567", pub1, priv1, 124, "test-two@example.com", Instant.now(), true));
            put("9aZ2345678", new ClientSideKeypair("9aZ2345678", pub2, priv2, 123, "test@example.com", Instant.now(), true));
            put("789aZ23456", new ClientSideKeypair("789aZ23456", pub3, priv3, 125, "test-two@example.com", Instant.now(), false));
        }};

        setKeypairs(new ArrayList<>(expectedKeypairs.values()));
        setSites(new Site(123, "test", true));

        JsonObject jo = new JsonObject();
        jo.put("site_id", 123);
        jo.put("contact", "email@email.com");

        post(vertx, "api/client_side_keypairs/add", jo.encode(), ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            JsonObject resp = response.bodyAsJsonObject();
            assertEquals(123, resp.getInteger("site_id"));
            assertEquals("email@email.com", resp.getString("contact"));
            assertEquals(10, resp.getString("subscription_id").length());
            assertNotNull(resp.getString("public_key"));
            assertNotNull(resp.getString("private_key"));
            assertTrue(resp.getString("public_key").length() > 9);
            assertEquals("UID2-X-T-", resp.getString("public_key").substring(0, 9));
            assertTrue(resp.getString("private_key").length() > 9);
            assertEquals("UID2-Y-T-", resp.getString("private_key").substring(0, 9));
            assertEquals(KEY_CREATE_TIME_IN_SECONDS, resp.getLong("created"));
            assertEquals(false, resp.getBoolean("disabled"));
            assertEquals(false, resp.getBoolean("disabled"));
            testContext.completeNow();
        });
    }

    @Test
    void addKeypairNoContact(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.ADMINISTRATOR);

        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("89aZ234567", new ClientSideKeypair("89aZ234567", pub1, priv1, 124, "test-two@example.com", Instant.now(), true));
            put("9aZ2345678", new ClientSideKeypair("9aZ2345678", pub2, priv2, 123, "test@example.com", Instant.now(), true));
            put("789aZ23456", new ClientSideKeypair("789aZ23456", pub3, priv3, 125, "test-two@example.com", Instant.now(), false));
        }};

        setKeypairs(new ArrayList<>(expectedKeypairs.values()));
        setSites(new Site(123, "test", true));

        JsonObject jo = new JsonObject();
        jo.put("site_id", 123);

        post(vertx, "api/client_side_keypairs/add", jo.encode(), ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            JsonObject resp = response.bodyAsJsonObject();
            assertEquals(123, resp.getInteger("site_id"));
            assertEquals("", resp.getString("contact"));
            assertEquals(10, resp.getString("subscription_id").length());
            assertNotNull(resp.getString("public_key"));
            assertNotNull(resp.getString("private_key"));
            assertTrue(resp.getString("public_key").length() > 9);
            assertEquals("UID2-X-T-", resp.getString("public_key").substring(0, 9));
            assertTrue(resp.getString("private_key").length() > 9);
            assertEquals("UID2-Y-T-", resp.getString("private_key").substring(0, 9));
            assertEquals(KEY_CREATE_TIME_IN_SECONDS, resp.getLong("created"));
            assertEquals(false, resp.getBoolean("disabled"));
            assertEquals(false, resp.getBoolean("disabled"));
            testContext.completeNow();
        });
    }

    @Test
    void addKeypairDisabled(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.ADMINISTRATOR);

        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("89aZ234567", new ClientSideKeypair("89aZ234567", pub1, priv1, 124, "test-two@example.com", Instant.now(), true));
            put("9aZ2345678", new ClientSideKeypair("9aZ2345678", pub2, priv2, 123, "test@example.com", Instant.now(), true));
            put("789aZ23456", new ClientSideKeypair("789aZ23456", pub3, priv3, 125, "test-two@example.com", Instant.now(), false));
        }};

        setKeypairs(new ArrayList<>(expectedKeypairs.values()));
        setSites(new Site(123, "test", true));

        JsonObject jo = new JsonObject();
        jo.put("site_id", 123);
        jo.put("contact", "email@email.com");
        jo.put("disabled", true);

        post(vertx, "api/client_side_keypairs/add", jo.encode(), ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            JsonObject resp = response.bodyAsJsonObject();
            assertEquals(123, resp.getInteger("site_id"));
            assertEquals("email@email.com", resp.getString("contact"));
            assertEquals(10, resp.getString("subscription_id").length());
            assertNotNull(resp.getString("public_key"));
            assertNotNull(resp.getString("private_key"));
            assertTrue(resp.getString("public_key").length() > 0);
            assertTrue(resp.getString("private_key").length() > 0);
            assertEquals(KEY_CREATE_TIME_IN_SECONDS, resp.getLong("created"));
            assertEquals(true, resp.getBoolean("disabled"));
            testContext.completeNow();
        });
    }

    @Test
    void updateKeypairNoSubscriptionId(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.ADMINISTRATOR);

        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("89aZ234567", new ClientSideKeypair("89aZ234567", pub1, priv1, 124, "test-two@example.com", Instant.now(), true));
            put("9aZ2345678", new ClientSideKeypair("9aZ2345678", pub2, priv2, 123, "test@example.com", Instant.now(), true));
            put("789aZ23456", new ClientSideKeypair("789aZ23456", pub3, priv3, 125, "test-two@example.com", Instant.now(), false));
        }};

        setKeypairs(new ArrayList<>(expectedKeypairs.values()));
        setSites(new Site(123, "test", true));

        JsonObject jo = new JsonObject();
        jo.put("contact", "email@email.com");
        jo.put("disabled", true);

        post(vertx, "api/client_side_keypairs/update", jo.encode(), ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            assertEquals("Required parameters: subscription_id", response.bodyAsJsonObject().getString("message"));
            testContext.completeNow();
        });
    }

    @Test
    void updateKeypairBadSubscriptionId(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.ADMINISTRATOR);

        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("89aZ234567", new ClientSideKeypair("89aZ234567", pub1, priv1, 124, "test-two@example.com", Instant.now(), true));
            put("9aZ2345678", new ClientSideKeypair("9aZ2345678", pub2, priv2, 123, "test@example.com", Instant.now(), true));
            put("789aZ23456", new ClientSideKeypair("789aZ23456", pub3, priv3, 125, "test-two@example.com", Instant.now(), false));
        }};

        setKeypairs(new ArrayList<>(expectedKeypairs.values()));
        setSites(new Site(123, "test", true));

        JsonObject jo = new JsonObject();
        jo.put("subscription_id", "bad-id");
        jo.put("contact", "email@email.com");
        jo.put("disabled", true);

        post(vertx, "api/client_side_keypairs/update", jo.encode(), ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(404, response.statusCode());
            assertEquals("Failed to find a keypair for subscription id: bad-id", response.bodyAsJsonObject().getString("message"));
            testContext.completeNow();
        });
    }

    @Test
    void updateKeypairNoUpdateParams(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.ADMINISTRATOR);

        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("89aZ234567", new ClientSideKeypair("89aZ234567", pub1, priv1, 124, "test-two@example.com", Instant.now(), true));
            put("9aZ2345678", new ClientSideKeypair("9aZ2345678", pub2, priv2, 123, "test@example.com", Instant.now(), true));
            put("789aZ23456", new ClientSideKeypair("789aZ23456", pub3, priv3, 125, "test-two@example.com", Instant.now(), false));
        }};

        setKeypairs(new ArrayList<>(expectedKeypairs.values()));
        setSites(new Site(123, "test", true));

        JsonObject jo = new JsonObject();
        jo.put("subscription_id", "89aZ234567");

        post(vertx, "api/client_side_keypairs/update", jo.encode(), ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            assertEquals("Updatable parameters: contact, disabled", response.bodyAsJsonObject().getString("message"));
            testContext.completeNow();
        });
    }

    @Test
    void updateKeypairContactOnly(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.ADMINISTRATOR);

        Instant time = Instant.now();
        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("89aZ234567", new ClientSideKeypair("89aZ234567", pub1, priv1, 124, "test-two@example.com", time, true));
            put("9aZ2345678", new ClientSideKeypair("9aZ2345678", pub2, priv2, 123, "test@example.com", Instant.now(), true));
            put("789aZ23456", new ClientSideKeypair("789aZ23456", pub3, priv3, 125, "test-two@example.com", Instant.now(), false));
        }};

        setKeypairs(new ArrayList<>(expectedKeypairs.values()));
        setSites(new Site(123, "test", true));

        JsonObject jo = new JsonObject();
        jo.put("subscription_id", "89aZ234567");
        jo.put("contact", "updated@email.com");

        post(vertx, "api/client_side_keypairs/update", jo.encode(), ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            ClientSideKeypair expected = new ClientSideKeypair("89aZ234567", pub1, priv1, 124, "updated@email.com", time, true);
            validateKeypair(expected, response.bodyAsJsonObject());
            testContext.completeNow();
        });
    }

    @Test
    void updateKeypairDisabledOnly(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.ADMINISTRATOR);

        Instant time = Instant.now();
        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("89aZ234567", new ClientSideKeypair("89aZ234567", pub1, priv1, 124, "test-two@example.com", time, true));
            put("9aZ2345678", new ClientSideKeypair("9aZ2345678", pub2, priv2, 123, "test@example.com", Instant.now(), true));
            put("789aZ23456", new ClientSideKeypair("789aZ23456", pub3, priv3, 125, "test-two@example.com", Instant.now(), false));
        }};

        setKeypairs(new ArrayList<>(expectedKeypairs.values()));
        setSites(new Site(123, "test", true));

        JsonObject jo = new JsonObject();
        jo.put("subscription_id", "89aZ234567");
        jo.put("disabled", false);

        post(vertx, "api/client_side_keypairs/update", jo.encode(), ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            ClientSideKeypair expected = new ClientSideKeypair("89aZ234567", pub1, priv1, 124, "test-two@example.com", time, false);
            validateKeypair(expected, response.bodyAsJsonObject());
            testContext.completeNow();
        });
    }

    @Test
    void updateKeypairDisabledAndContact(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.ADMINISTRATOR);

        Instant time = Instant.now();
        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("89aZ234567", new ClientSideKeypair("89aZ234567", pub1, priv1, 124, "test-two@example.com", time, true));
            put("9aZ2345678", new ClientSideKeypair("9aZ2345678", pub2, priv2, 123, "test@example.com", Instant.now(), true));
            put("789aZ23456", new ClientSideKeypair("789aZ23456", pub3, priv3, 125, "test-two@example.com", Instant.now(), false));
        }};

        setKeypairs(new ArrayList<>(expectedKeypairs.values()));
        setSites(new Site(123, "test", true));

        JsonObject jo = new JsonObject();
        jo.put("subscription_id", "89aZ234567");
        jo.put("contact", "updated@email.com");
        jo.put("disabled", false);

        post(vertx, "api/client_side_keypairs/update", jo.encode(), ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            ClientSideKeypair expected = new ClientSideKeypair("89aZ234567", pub1, priv1, 124, "updated@email.com", time, false);
            validateKeypair(expected, response.bodyAsJsonObject());
            testContext.completeNow();
        });
    }
}

