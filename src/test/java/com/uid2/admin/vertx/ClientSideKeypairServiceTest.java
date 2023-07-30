package com.uid2.admin.vertx;

import com.uid2.admin.model.Site;
import com.uid2.admin.secret.SecureKeypairGenerator;
import com.uid2.admin.store.Clock;
import com.uid2.admin.vertx.service.ClientSideKeypairService;
import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.ClientSideKeypair;
import com.uid2.shared.secure.gcpoidc.Environment;
import com.uid2.shared.secure.gcpoidc.IdentityScope;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ClientSideKeypairServiceTest extends ServiceTestBase {

    private final Clock clock = mock(Clock.class);
    private static final long KEY_CREATE_TIME_IN_SECONDS = 1690680355L;
    @Override
    protected IService createService() {
        return new ClientSideKeypairService(auth, writeLock, keypairStoreWriter, keypairProvider, siteProvider, new SecureKeypairGenerator(IdentityScope.UID2, Environment.Test), clock);
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
        assertArrayEquals(expectedKeypair.getPublicKeyBytes(), Base64.getDecoder().decode(resp.getString("public_key").substring(ClientSideKeypair.KEYPAIR_KEY_PREFIX_LENGTH)));
        assertArrayEquals(expectedKeypair.getPrivateKeyBytes(), Base64.getDecoder().decode(resp.getString("private_key").substring(ClientSideKeypair.KEYPAIR_KEY_PREFIX_LENGTH)));
        assertEquals(expectedKeypair.getSiteId(), resp.getInteger("site_id"));
        assertEquals(expectedKeypair.getContact(), resp.getString("contact"));
        assertEquals(expectedKeypair.getCreated().getEpochSecond(), resp.getLong("created"));
        assertEquals(expectedKeypair.isDisabled(), resp.getBoolean("disabled"));
        assertEquals("UID2-X-T-", resp.getString("public_key").substring(0, ClientSideKeypair.KEYPAIR_KEY_PREFIX_LENGTH));
        assertEquals("UID2-Y-T-", resp.getString("private_key").substring(0, ClientSideKeypair.KEYPAIR_KEY_PREFIX_LENGTH));
    }

    @Test
    void listAllEmpty(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

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
            put("0123456789", new ClientSideKeypair("0123456789", new byte [] {0, 1, 2}, new byte [] {4, 5, 6}, 123, "test@example.com", Instant.now(), false, "UID2-X-T-", "UID2-Y-T-"));
            put("8901234567", new ClientSideKeypair("8901234567", new byte [] {3, 4, 5}, new byte [] {7, 8, 9}, 124, "test-two@example.com", Instant.now(), true, "UID2-X-T-", "UID2-Y-T-"));
            put("9012345678", new ClientSideKeypair("9012345678", new byte [] {6, 7, 8}, new byte [] {10, 11, 12}, 123, "test@example.com", Instant.now(), true, "UID2-X-T-", "UID2-Y-T-"));
            put("7890123456", new ClientSideKeypair("7890123456", new byte [] {9, 10, 11}, new byte [] {13, 14, 15}, 125, "test-two@example.com", Instant.now(), false, "UID2-X-T-", "UID2-Y-T-"));
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

        get(vertx, "api/client_side_keypairs/0123456789", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(404, response.statusCode());
            assertEquals("Failed to find a keypair for subscription id: 0123456789", response.bodyAsJsonObject().getString("message"));
            testContext.completeNow();
        });
    }

    @Test
    void listKeypair(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.ADMINISTRATOR);

        ClientSideKeypair queryKeypair = new ClientSideKeypair("0123456789", new byte [] {0, 1, 2}, new byte [] {4, 5, 6}, 123, "test@example.com", Instant.now(), false, "UID2-X-T-", "UID2-Y-T-");

        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("0123456789", queryKeypair);
            put("8901234567", new ClientSideKeypair("8901234567", new byte [] {3, 4, 5}, new byte [] {7, 8, 9}, 124, "test-two@example.com", Instant.now(), true, "UID2-X-T-", "UID2-Y-T-"));
            put("9012345678", new ClientSideKeypair("9012345678", new byte [] {6, 7, 8}, new byte [] {10, 11, 12}, 123, "test@example.com", Instant.now(), true, "UID2-X-T-", "UID2-Y-T-"));
            put("7890123456", new ClientSideKeypair("7890123456", new byte [] {9, 10, 11}, new byte [] {13, 14, 15}, 125, "test-two@example.com", Instant.now(), false, "UID2-X-T-", "UID2-Y-T-"));
        }};
        setKeypairs(new ArrayList<>(expectedKeypairs.values()));

        get(vertx, "api/client_side_keypairs/0123456789", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            validateKeypair(queryKeypair, response.bodyAsJsonObject());

            testContext.completeNow();
        });
    }

    @Test
    void addKeypairNoSiteIdOrContact(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.ADMINISTRATOR);

        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("8901234567", new ClientSideKeypair("8901234567", new byte [] {3, 4, 5}, new byte [] {7, 8, 9}, 124, "test-two@example.com", Instant.now(), true, "UID2-X-T-", "UID2-Y-T"));
            put("9012345678", new ClientSideKeypair("9012345678", new byte [] {6, 7, 8}, new byte [] {10, 11, 12}, 123, "test@example.com", Instant.now(), true, "UID2-X-T-", "UID2-Y-T"));
            put("7890123456", new ClientSideKeypair("7890123456", new byte [] {9, 10, 11}, new byte [] {13, 14, 15}, 125, "test-two@example.com", Instant.now(), false, "UID2-X-T-", "UID2-Y-T"));
        }};

        setKeypairs(new ArrayList<>(expectedKeypairs.values()));

        JsonObject jo = new JsonObject();

        post(vertx, "api/client_side_keypairs/add", jo.encode(), ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            assertEquals("Required parameters: site_id, contact", response.bodyAsJsonObject().getString("message"));
            testContext.completeNow();
        });
    }

    @Test
    void addKeypairNoSiteId(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.ADMINISTRATOR);

        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("8901234567", new ClientSideKeypair("8901234567", new byte [] {3, 4, 5}, new byte [] {7, 8, 9}, 124, "test-two@example.com", Instant.now(), true, "UID2-X-T-", "UID2-Y-T"));
            put("9012345678", new ClientSideKeypair("9012345678", new byte [] {6, 7, 8}, new byte [] {10, 11, 12}, 123, "test@example.com", Instant.now(), true, "UID2-X-T-", "UID2-Y-T"));
            put("7890123456", new ClientSideKeypair("7890123456", new byte [] {9, 10, 11}, new byte [] {13, 14, 15}, 125, "test-two@example.com", Instant.now(), false, "UID2-X-T-", "UID2-Y-T"));
        }};

        setKeypairs(new ArrayList<>(expectedKeypairs.values()));

        JsonObject jo = new JsonObject();
        jo.put("contact", "email@email.com");

        post(vertx, "api/client_side_keypairs/add", jo.encode(), ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            assertEquals("Required parameters: site_id, contact", response.bodyAsJsonObject().getString("message"));
            testContext.completeNow();
        });
    }

    @Test
    void addKeypairNoContact(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.ADMINISTRATOR);

        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("8901234567", new ClientSideKeypair("8901234567", new byte [] {3, 4, 5}, new byte [] {7, 8, 9}, 124, "test-two@example.com", Instant.now(), true, "UID2-X-T-", "UID2-Y-T"));
            put("9012345678", new ClientSideKeypair("9012345678", new byte [] {6, 7, 8}, new byte [] {10, 11, 12}, 123, "test@example.com", Instant.now(), true, "UID2-X-T-", "UID2-Y-T"));
            put("7890123456", new ClientSideKeypair("7890123456", new byte [] {9, 10, 11}, new byte [] {13, 14, 15}, 125, "test-two@example.com", Instant.now(), false, "UID2-X-T-", "UID2-Y-T"));
        }};

        setKeypairs(new ArrayList<>(expectedKeypairs.values()));

        JsonObject jo = new JsonObject();
        jo.put("site_id", 123);

        post(vertx, "api/client_side_keypairs/add", jo.encode(), ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            assertEquals("Required parameters: site_id, contact", response.bodyAsJsonObject().getString("message"));
            testContext.completeNow();
        });
    }

    @Test
    void addKeypairBadSiteId(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.ADMINISTRATOR);

        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("8901234567", new ClientSideKeypair("8901234567", new byte [] {3, 4, 5}, new byte [] {7, 8, 9}, 124, "test-two@example.com", Instant.now(), true, "UID2-X-T-", "UID2-Y-T"));
            put("9012345678", new ClientSideKeypair("9012345678", new byte [] {6, 7, 8}, new byte [] {10, 11, 12}, 123, "test@example.com", Instant.now(), true, "UID2-X-T-", "UID2-Y-T"));
            put("7890123456", new ClientSideKeypair("7890123456", new byte [] {9, 10, 11}, new byte [] {13, 14, 15}, 125, "test-two@example.com", Instant.now(), false, "UID2-X-T-", "UID2-Y-T"));
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
    void addKeypairBadContact(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.ADMINISTRATOR);

        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("8901234567", new ClientSideKeypair("8901234567", new byte [] {3, 4, 5}, new byte [] {7, 8, 9}, 124, "test-two@example.com", Instant.now(), true, "UID2-X-T-", "UID2-Y-T"));
            put("9012345678", new ClientSideKeypair("9012345678", new byte [] {6, 7, 8}, new byte [] {10, 11, 12}, 123, "test@example.com", Instant.now(), true, "UID2-X-T-", "UID2-Y-T"));
            put("7890123456", new ClientSideKeypair("7890123456", new byte [] {9, 10, 11}, new byte [] {13, 14, 15}, 125, "test-two@example.com", Instant.now(), false, "UID2-X-T-", "UID2-Y-T"));
        }};

        setKeypairs(new ArrayList<>(expectedKeypairs.values()));
        setSites(new Site(123, "test", true));

        JsonObject jo = new JsonObject();
        jo.put("site_id", 123);
        jo.put("contact", "not-an-email");

        post(vertx, "api/client_side_keypairs/add", jo.encode(), ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            assertEquals("contact email: not-an-email not valid", response.bodyAsJsonObject().getString("message"));
            testContext.completeNow();
        });
    }

    @Test
    void addKeypair(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.ADMINISTRATOR);

        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("8901234567", new ClientSideKeypair("8901234567", new byte [] {3, 4, 5}, new byte [] {7, 8, 9}, 124, "test-two@example.com", Instant.now(), true, "UID2-X-T-", "UID2-Y-T-"));
            put("9012345678", new ClientSideKeypair("9012345678", new byte [] {6, 7, 8}, new byte [] {10, 11, 12}, 123, "test@example.com", Instant.now(), true, "UID2-X-T-", "UID2-Y-T-"));
            put("7890123456", new ClientSideKeypair("7890123456", new byte [] {9, 10, 11}, new byte [] {13, 14, 15}, 125, "test-two@example.com", Instant.now(), false, "UID2-X-T-", "UID2-Y-T-"));
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
    void addKeypairDisabled(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.ADMINISTRATOR);

        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("8901234567", new ClientSideKeypair("8901234567", new byte [] {3, 4, 5}, new byte [] {7, 8, 9}, 124, "test-two@example.com", Instant.now(), true, "UID2-X-T-", "UID2-Y-T"));
            put("9012345678", new ClientSideKeypair("9012345678", new byte [] {6, 7, 8}, new byte [] {10, 11, 12}, 123, "test@example.com", Instant.now(), true, "UID2-X-T-", "UID2-Y-T"));
            put("7890123456", new ClientSideKeypair("7890123456", new byte [] {9, 10, 11}, new byte [] {13, 14, 15}, 125, "test-two@example.com", Instant.now(), false, "UID2-X-T-", "UID2-Y-T"));
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
            put("8901234567", new ClientSideKeypair("8901234567", new byte [] {3, 4, 5}, new byte [] {7, 8, 9}, 124, "test-two@example.com", Instant.now(), true, "UID2-X-T-", "UID2-Y-T"));
            put("9012345678", new ClientSideKeypair("9012345678", new byte [] {6, 7, 8}, new byte [] {10, 11, 12}, 123, "test@example.com", Instant.now(), true, "UID2-X-T-", "UID2-Y-T"));
            put("7890123456", new ClientSideKeypair("7890123456", new byte [] {9, 10, 11}, new byte [] {13, 14, 15}, 125, "test-two@example.com", Instant.now(), false, "UID2-X-T-", "UID2-Y-T"));
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
            put("8901234567", new ClientSideKeypair("8901234567", new byte [] {3, 4, 5}, new byte [] {7, 8, 9}, 124, "test-two@example.com", Instant.now(), true, "UID2-X-T-", "UID2-Y-T"));
            put("9012345678", new ClientSideKeypair("9012345678", new byte [] {6, 7, 8}, new byte [] {10, 11, 12}, 123, "test@example.com", Instant.now(), true, "UID2-X-T-", "UID2-Y-T"));
            put("7890123456", new ClientSideKeypair("7890123456", new byte [] {9, 10, 11}, new byte [] {13, 14, 15}, 125, "test-two@example.com", Instant.now(), false, "UID2-X-T-", "UID2-Y-T"));
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
            put("8901234567", new ClientSideKeypair("8901234567", new byte [] {3, 4, 5}, new byte [] {7, 8, 9}, 124, "test-two@example.com", Instant.now(), true, "UID2-X-T-", "UID2-Y-T"));
            put("9012345678", new ClientSideKeypair("9012345678", new byte [] {6, 7, 8}, new byte [] {10, 11, 12}, 123, "test@example.com", Instant.now(), true, "UID2-X-T-", "UID2-Y-T"));
            put("7890123456", new ClientSideKeypair("7890123456", new byte [] {9, 10, 11}, new byte [] {13, 14, 15}, 125, "test-two@example.com", Instant.now(), false, "UID2-X-T-", "UID2-Y-T"));
        }};

        setKeypairs(new ArrayList<>(expectedKeypairs.values()));
        setSites(new Site(123, "test", true));

        JsonObject jo = new JsonObject();
        jo.put("subscription_id", "8901234567");

        post(vertx, "api/client_side_keypairs/update", jo.encode(), ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            assertEquals("Updatable parameters: contact, disabled", response.bodyAsJsonObject().getString("message"));
            testContext.completeNow();
        });
    }

    @Test
    void updateKeypairBadContactEmail(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.ADMINISTRATOR);

        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("8901234567", new ClientSideKeypair("8901234567", new byte [] {3, 4, 5}, new byte [] {7, 8, 9}, 124, "test-two@example.com", Instant.now(), true, "UID2-X-T-", "UID2-Y-T"));
            put("9012345678", new ClientSideKeypair("9012345678", new byte [] {6, 7, 8}, new byte [] {10, 11, 12}, 123, "test@example.com", Instant.now(), true, "UID2-X-T-", "UID2-Y-T"));
            put("7890123456", new ClientSideKeypair("7890123456", new byte [] {9, 10, 11}, new byte [] {13, 14, 15}, 125, "test-two@example.com", Instant.now(), false, "UID2-X-T-", "UID2-Y-T"));
        }};

        setKeypairs(new ArrayList<>(expectedKeypairs.values()));
        setSites(new Site(123, "test", true));

        JsonObject jo = new JsonObject();
        jo.put("subscription_id", "8901234567");
        jo.put("contact", "not-an-email");

        post(vertx, "api/client_side_keypairs/update", jo.encode(), ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            assertEquals("contact email: not-an-email not valid", response.bodyAsJsonObject().getString("message"));
            testContext.completeNow();
        });
    }

    @Test
    void updateKeypairContactOnly(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.ADMINISTRATOR);

        Instant time = Instant.now();
        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("8901234567", new ClientSideKeypair("8901234567", new byte [] {3, 4, 5}, new byte [] {7, 8, 9}, 124, "test-two@example.com", time, true, "UID2-X-T-", "UID2-Y-T-"));
            put("9012345678", new ClientSideKeypair("9012345678", new byte [] {6, 7, 8}, new byte [] {10, 11, 12}, 123, "test@example.com", Instant.now(), true, "UID2-X-T-", "UID2-Y-T-"));
            put("7890123456", new ClientSideKeypair("7890123456", new byte [] {9, 10, 11}, new byte [] {13, 14, 15}, 125, "test-two@example.com", Instant.now(), false, "UID2-X-T-", "UID2-Y-T-"));
        }};

        setKeypairs(new ArrayList<>(expectedKeypairs.values()));
        setSites(new Site(123, "test", true));

        JsonObject jo = new JsonObject();
        jo.put("subscription_id", "8901234567");
        jo.put("contact", "updated@email.com");

        post(vertx, "api/client_side_keypairs/update", jo.encode(), ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            ClientSideKeypair expected = new ClientSideKeypair("8901234567", new byte [] {3, 4, 5}, new byte [] {7, 8, 9}, 124, "updated@email.com", time, true, "UID2-X-T-", "UID2-Y-T-");
            validateKeypair(expected, response.bodyAsJsonObject());
            testContext.completeNow();
        });
    }

    @Test
    void updateKeypairDisabledOnly(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.ADMINISTRATOR);

        Instant time = Instant.now();
        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("8901234567", new ClientSideKeypair("8901234567", new byte [] {3, 4, 5}, new byte [] {7, 8, 9}, 124, "test-two@example.com", time, true, "UID2-X-T-", "UID2-Y-T-"));
            put("9012345678", new ClientSideKeypair("9012345678", new byte [] {6, 7, 8}, new byte [] {10, 11, 12}, 123, "test@example.com", Instant.now(), true, "UID2-X-T-", "UID2-Y-T-"));
            put("7890123456", new ClientSideKeypair("7890123456", new byte [] {9, 10, 11}, new byte [] {13, 14, 15}, 125, "test-two@example.com", Instant.now(), false, "UID2-X-T-", "UID2-Y-T-"));
        }};

        setKeypairs(new ArrayList<>(expectedKeypairs.values()));
        setSites(new Site(123, "test", true));

        JsonObject jo = new JsonObject();
        jo.put("subscription_id", "8901234567");
        jo.put("disabled", false);

        post(vertx, "api/client_side_keypairs/update", jo.encode(), ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            ClientSideKeypair expected = new ClientSideKeypair("8901234567", new byte [] {3, 4, 5}, new byte [] {7, 8, 9}, 124, "test-two@example.com", time, false, "UID2-X-T-", "UID2-Y-T-");
            validateKeypair(expected, response.bodyAsJsonObject());
            testContext.completeNow();
        });
    }

    @Test
    void updateKeypairDisabledAndContact(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.ADMINISTRATOR);

        Instant time = Instant.now();
        Map<String, ClientSideKeypair> expectedKeypairs = new HashMap<>() {{
            put("8901234567", new ClientSideKeypair("8901234567", new byte [] {3, 4, 5}, new byte [] {7, 8, 9}, 124, "test-two@example.com", time, true, "UID2-X-T-", "UID2-Y-T-"));
            put("9012345678", new ClientSideKeypair("9012345678", new byte [] {6, 7, 8}, new byte [] {10, 11, 12}, 123, "test@example.com", Instant.now(), true, "UID2-X-T-", "UID2-Y-T-"));
            put("7890123456", new ClientSideKeypair("7890123456", new byte [] {9, 10, 11}, new byte [] {13, 14, 15}, 125, "test-two@example.com", Instant.now(), false, "UID2-X-T-", "UID2-Y-T-"));
        }};

        setKeypairs(new ArrayList<>(expectedKeypairs.values()));
        setSites(new Site(123, "test", true));

        JsonObject jo = new JsonObject();
        jo.put("subscription_id", "8901234567");
        jo.put("contact", "updated@email.com");
        jo.put("disabled", false);

        post(vertx, "api/client_side_keypairs/update", jo.encode(), ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            ClientSideKeypair expected = new ClientSideKeypair("8901234567", new byte [] {3, 4, 5}, new byte [] {7, 8, 9}, 124, "updated@email.com", time, false, "UID2-X-T-", "UID2-Y-T-");
            validateKeypair(expected, response.bodyAsJsonObject());
            testContext.completeNow();
        });
    }

    @Test
    void testKeyPrefix(){
        SecureKeypairGenerator uid2L = new SecureKeypairGenerator(IdentityScope.UID2, Environment.Local);
        assertEquals("UID2-X-L-", uid2L.getPublicKeyPrefix());
        assertEquals("UID2-Y-L-", uid2L.getPrivateKeyPrefix());
        SecureKeypairGenerator uid2T = new SecureKeypairGenerator(IdentityScope.UID2, Environment.Test);
        assertEquals("UID2-X-T-", uid2T.getPublicKeyPrefix());
        assertEquals("UID2-Y-T-", uid2T.getPrivateKeyPrefix());
        SecureKeypairGenerator uid2I = new SecureKeypairGenerator(IdentityScope.UID2, Environment.Integration);
        assertEquals("UID2-X-I-", uid2I.getPublicKeyPrefix());
        assertEquals("UID2-Y-I-", uid2I.getPrivateKeyPrefix());
        SecureKeypairGenerator uid2P = new SecureKeypairGenerator(IdentityScope.UID2, Environment.Production);
        assertEquals("UID2-X-P-", uid2P.getPublicKeyPrefix());
        assertEquals("UID2-Y-P-", uid2P.getPrivateKeyPrefix());

        SecureKeypairGenerator euidL = new SecureKeypairGenerator(IdentityScope.EUID, Environment.Local);
        assertEquals("EUID-X-L-", euidL.getPublicKeyPrefix());
        assertEquals("EUID-Y-L-", euidL.getPrivateKeyPrefix());
        SecureKeypairGenerator euidT = new SecureKeypairGenerator(IdentityScope.EUID, Environment.Test);
        assertEquals("EUID-X-T-", euidT.getPublicKeyPrefix());
        assertEquals("EUID-Y-T-", euidT.getPrivateKeyPrefix());
        SecureKeypairGenerator euidI = new SecureKeypairGenerator(IdentityScope.EUID, Environment.Integration);
        assertEquals("EUID-X-I-", euidI.getPublicKeyPrefix());
        assertEquals("EUID-Y-I-", euidI.getPrivateKeyPrefix());
        SecureKeypairGenerator euidP = new SecureKeypairGenerator(IdentityScope.EUID, Environment.Production);
        assertEquals("EUID-X-P-", euidP.getPublicKeyPrefix());
        assertEquals("EUID-Y-P-", euidP.getPrivateKeyPrefix());
    }
}


