package com.uid2.admin.vertx;

import com.uid2.admin.managers.KeysetManager;
import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.service.SharingService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.Const;
import com.uid2.shared.auth.EncryptionKeyAcl;
import com.uid2.shared.auth.Keyset;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.Site;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class SharingServiceTest extends ServiceTestBase {
    @Override
    protected IService createService() {
        KeysetManager keysetManager = new KeysetManager(keysetProvider, keysetStoreWriter, keysetKeyManager, true);
        return new SharingService(auth, writeLock, keysetProvider, keysetManager, siteProvider, true);
    }

    private void compareKeysetToResult(Keyset keyset, JsonArray actualKeyset) {
        assertNotNull(actualKeyset);
        Set<Integer> actualSet = actualKeyset.stream()
                .map(s -> (Integer) s)
                .collect(Collectors.toSet());
        assertEquals(keyset.getAllowedSites(), actualSet);
    }

    private void mockSiteExistence(Integer... sites){
        for(Integer site : sites) {
            doReturn(new Site(site, "test-name", true)).when(siteProvider).getSite(site);
        }
    }


    @Test
    void listSiteGet(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SHARING_PORTAL);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
            put(2, new Keyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true));
            put(3, new Keyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);
        get(vertx, "api/sharing/list/5", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            compareKeysetToResult(keysets.get(1), response.bodyAsJsonObject().getJsonArray("allowed_sites"));

            Integer expectedHash = keysets.get(1).hashCode();
            assertEquals(expectedHash, response.bodyAsJsonObject().getInteger("hash"));

            testContext.completeNow();
        });
    }

    @Test
    void listSiteGetNotFound(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SHARING_PORTAL);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
            put(2, new Keyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true));
            put(3, new Keyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);
        get(vertx, "api/sharing/list/42", ar -> {
            HttpResponse response = ar.result();
            assertEquals(404, response.statusCode());

            testContext.completeNow();
        });
    }

    @Test
    void listSiteSet(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SHARING_PORTAL);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(3, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
            put(2, new Keyset(4, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true));
            put(3, new Keyset(5, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);
        mockSiteExistence(5,7,4);

        String body = "  {\n" +
                "    \"allowed_sites\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"hash\": " + keysets.get(1).hashCode() + "\n" +
                "  }";

        post(vertx, "api/sharing/list/5", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            Keyset expected = new Keyset(3, 5, "test", Set.of(22, 25, 6), Instant.now().getEpochSecond(), true, true);
            compareKeysetToResult(expected, response.bodyAsJsonObject().getJsonArray("allowed_sites"));

            assertEquals(expected.getAllowedSites(), keysets.get(3).getAllowedSites());
            testContext.completeNow();
        });
    }

    @Test
    void listSiteSetNew(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SHARING_PORTAL);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(3, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
            put(2, new Keyset(4, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true));
            put(3, new Keyset(5, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);
        mockSiteExistence(5,7,4,8);

        String body = "  {\n" +
                "    \"allowed_sites\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"hash\": 0\n" +
                "  }";

        post(vertx, "api/sharing/list/8", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            Keyset expected = new Keyset(4, 8, "test", Set.of(22, 25, 6), Instant.now().getEpochSecond(), true, true);
            compareKeysetToResult(expected, response.bodyAsJsonObject().getJsonArray("allowed_sites"));

            assertEquals(expected.getAllowedSites(), keysets.get(4).getAllowedSites());

            //Ensure new key was created
            try {
                verify(keysetKeyManager).addKeysetKey(4);
            } catch (Exception ex) {
                fail(ex);
            }
            testContext.completeNow();
        });
    }

    @Test
    void listSiteSetNotAllowed(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SHARING_PORTAL);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(-1, -1, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
            put(2, new Keyset(4, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true));
            put(3, new Keyset(5, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"hash\": 0\n" +
                "  }";

        post(vertx, "api/sharing/list/-1", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            testContext.completeNow();
        });
    }

    @Test
    void listSiteSetConcurrency(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SHARING_PORTAL);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
            put(2, new Keyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true));
            put(3, new Keyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);
        mockSiteExistence(5,7,4);

        String body1 = "  {\n" +
                "    \"allowed_sites\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"hash\": " + keysets.get(1).hashCode() + "\n" +
                "  }";

        String body2 = "  {\n" +
                "    \"allowed_sites\": [\n" +
                "      2,\n" +
                "      5,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"hash\": " + keysets.get(1).hashCode() + "\n" +
                "  }";

        post(vertx, "api/sharing/list/5", body1, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            Keyset expected = new Keyset(1, 5, "test", Set.of(22, 25, 6), Instant.now().getEpochSecond(), true, true);
            compareKeysetToResult(expected, response.bodyAsJsonObject().getJsonArray("whitelist"));

            assertEquals(expected.getAllowedSites(), keysets.get(1).getAllowedSites());
            testContext.completeNow();
        });

        post(vertx, "api/sharing/list/5", body2, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(409, response.statusCode());

            testContext.completeNow();
        });
    }

    @Test
    void listAll(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SHARING_PORTAL);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
            put(2, new Keyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true));
            put(3, new Keyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);
        get(vertx, "api/sharing/lists", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            JsonArray respArray = response.bodyAsJsonArray();

            for (int i = 0; i < keysets.size(); i++) {
                JsonObject resp = respArray.getJsonObject(i);
                int keyset_id = resp.getInteger("keyset_id");
                compareKeysetToResult(keysets.get(keyset_id), resp.getJsonArray("allowed_sites"));

                Integer expectedHash = keysets.get(keyset_id).hashCode();
                assertEquals(expectedHash, resp.getInteger("hash"));
            }

            testContext.completeNow();
        });
    }

    @Test
    void KeysetList(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
            put(2, new Keyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true));
            put(3, new Keyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);
        get(vertx, "api/sharing/keyset/1", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            compareKeysetToResult(keysets.get(1), response.bodyAsJsonObject().getJsonArray("allowed_sites"));

            testContext.completeNow();
        });
    }

    @Test
    void KeysetListNotFound(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(2, new Keyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true));
            put(3, new Keyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);
        get(vertx, "api/sharing/keyset/1", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(404, response.statusCode());
            assertEquals("Failed to find keyset for keyset_id: 1", response.bodyAsJsonObject().getString("message"));

            testContext.completeNow();
        });
    }

    @Test
    void listAllKeysets(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
            put(2, new Keyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true));
            put(3, new Keyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);
        get(vertx, "api/sharing/keysets", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            JsonArray respArray = response.bodyAsJsonArray();

            for (int i = 0; i < keysets.size(); i++) {
                JsonObject resp = respArray.getJsonObject(i);
                int keyset_id = resp.getInteger("keyset_id");
                compareKeysetToResult(keysets.get(keyset_id), resp.getJsonArray("allowed_sites"));
            }

            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetNoSiteIdOrKeysetId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
            put(2, new Keyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true));
            put(3, new Keyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "     \"name\": \"test-name\"" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            assertEquals("You must specify exactly one of: keyset_id, site_id", response.bodyAsJsonObject().getString("message"));

            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetBothSiteIdAndKeysetId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
            put(2, new Keyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true));
            put(3, new Keyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"keyset_id\": 1," +
                "    \"site_id\": 123," +
                "     \"name\": \"test-name\"" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            assertEquals("You must specify exactly one of: keyset_id, site_id", response.bodyAsJsonObject().getString("message"));

            testContext.completeNow();
        });
    }

    @Test
    void KeysetCanUpdateAllowedSitesAndName(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        mockSiteExistence(22, 25, 6);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
            put(2, new Keyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true));
            put(3, new Keyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"keyset_id\": 1," +
                "     \"name\": \"test-name\"" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            Keyset expected = new Keyset(1, 5, "test-name", Set.of(22, 25, 6), Instant.now().getEpochSecond(), true, true);
            compareKeysetToResult(expected, response.bodyAsJsonObject().getJsonArray("allowed_sites"));

            assertEquals(expected.getAllowedSites(), keysets.get(1).getAllowedSites());
            testContext.completeNow();
        });
    }

    @Test
    void KeysetCanUpdateAllowedSites(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        mockSiteExistence(5, 22, 25, 6);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test-name", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
            put(2, new Keyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true));
            put(3, new Keyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"keyset_id\": 1" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            Keyset expected = new Keyset(1, 5, "test-name", Set.of(22, 25, 6), Instant.now().getEpochSecond(), true, true);
            compareKeysetToResult(expected, response.bodyAsJsonObject().getJsonArray("allowed_sites"));

            assertEquals(expected.getAllowedSites(), keysets.get(1).getAllowedSites());
            testContext.completeNow();
        });
    }

    @Test
    void KeysetCanMakeNew(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        mockSiteExistence(8, 22, 25, 6);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 123, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
            put(2, new Keyset(2, 124, "test", Set.of(12), Instant.now().getEpochSecond(),true, true));
            put(3, new Keyset(3, 125, "test", Set.of(5), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"site_id\": 8," +
                "    \"name\": \"test-name\"" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            Keyset expected = new Keyset(4, 8, "test-name", Set.of(22, 25, 6), Instant.now().getEpochSecond(), true, true);
            compareKeysetToResult(expected, response.bodyAsJsonObject().getJsonArray("allowed_sites"));

            assertEquals(expected.getAllowedSites(), keysets.get(4).getAllowedSites());
            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetBadSiteId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        doReturn(null).when(siteProvider).getSite(5);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
            put(2, new Keyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true));
            put(3, new Keyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"site_id\": 5," +
                "     \"name\": \"test-name\"" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            assertEquals("Site id 5 not valid", response.bodyAsJsonObject().getString("message"));

            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetBadWhitelistSiteId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        mockSiteExistence(5, 22);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(2, new Keyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true));
            put(3, new Keyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"site_id\": 5," +
                "     \"name\": \"test-name\"" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            assertEquals("Site id 25 not valid", response.bodyAsJsonObject().getString("message"));

            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetIgnoresAlreadySetSitesWhenChecking(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        // 25 is not an existing site
        mockSiteExistence(5, 22, 6);

        // But 25 is already in the list here
        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(3, new Keyset(3, 7, "test", Set.of(12, 25), Instant.now().getEpochSecond(),true, true));
            put(4, new Keyset(4, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        // Resend 25
        String body = "  {\n" +
                "    \"allowed_sites\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"keyset_id\": 3," +
                "     \"name\": \"test-name\"" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            testContext.completeNow();
        });
    }

    // This test should be enabled when multiple keysets is enabled
    @Test
    @Disabled
    void KeysetSetNewIdenticalNameAndSiteId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        mockSiteExistence(8, 22, 25, 6);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 8, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"site_id\": 8," +
                "    \"name\": \"TEST\"" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            assertEquals("Keyset with same site_id and name already exists", response.bodyAsJsonObject().getString("message"));

            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetNewSameNameDifferentSite(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        mockSiteExistence(8, 22, 25, 6);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"site_id\": 8," +
                "    \"name\": \"test\"" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            Keyset expected = new Keyset(2, 8, "test", Set.of(22, 25, 6), Instant.now().getEpochSecond(), true, true);
            compareKeysetToResult(expected, response.bodyAsJsonObject().getJsonArray("allowed_sites"));

            assertEquals(expected.getAllowedSites(), keysets.get(2).getAllowedSites());
            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetNewEmptyAllowedSites(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        doReturn(new Site(8, "test", true)).when(siteProvider).getSite(8);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [],\n" +
                "    \"site_id\": 8," +
                "    \"name\": \"test-name\"" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            Keyset expected = new Keyset(2, 8, "test", Set.of(), Instant.now().getEpochSecond(), true, true);
            compareKeysetToResult(expected, response.bodyAsJsonObject().getJsonArray("allowed_sites"));

            assertEquals(expected.getAllowedSites(), keysets.get(2).getAllowedSites());
            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetUpdateEmptyAllowedSites(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        doReturn(new Site(5, "test", true)).when(siteProvider).getSite(5);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [],\n" +
                "    \"name\": \"test-name\"," +
                "    \"keyset_id\": 1" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            Keyset expected = new Keyset(1, 5, "test", Set.of(), Instant.now().getEpochSecond(), true, true);
            compareKeysetToResult(expected, response.bodyAsJsonObject().getJsonArray("allowed_sites"));

            assertEquals(expected.getAllowedSites(), keysets.get(1).getAllowedSites());
            testContext.completeNow();
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {Const.Data.AdvertisingTokenSiteId, Const.Data.RefreshKeySiteId, Const.Data.MasterKeySiteId})
    void KeysetSetNewReservedSite(int input, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        doReturn(new Site(input, "test", true)).when(siteProvider).getSite(input);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [],\n" +
                String.format("    \"site_id\": %d,", input) +
                "    \"name\": \"test-name\"" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            assertEquals("Site id " + input + " not valid", response.bodyAsJsonObject().getString("message"));

            testContext.completeNow();
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {Const.Data.MasterKeysetId, Const.Data.RefreshKeysetId, Const.Data.FallbackPublisherKeysetId})
    void KeysetSetReservedKeyset(int input, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        doReturn(new Site(input, "test", true)).when(siteProvider).getSite(input);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(input, new Keyset(input, input, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [],\n" +
                String.format("    \"keyset_id\": %d,", input) +
                "    \"name\": \"test-name\"" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            assertEquals("Keyset id: " + input + " is not valid", response.bodyAsJsonObject().getString("message"));

            testContext.completeNow();
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {Const.Data.AdvertisingTokenSiteId, Const.Data.RefreshKeySiteId, Const.Data.MasterKeySiteId})
    void KeysetSetUpdateReservedSite(int input, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        doReturn(new Site(input, "test", true)).when(siteProvider).getSite(input);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4, 6, 7), Instant.now().getEpochSecond(), true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [],\n" +
                String.format("    \"site_id\": %d,", input) +
                "    \"name\": \"test-name\"" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            assertEquals("Site id " + input + " not valid", response.bodyAsJsonObject().getString("message"));

            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetNewDisallowDuplicates(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        doReturn(new Site(8, "test", true)).when(siteProvider).getSite(8);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [8, 8],\n" +
                "    \"site_id\": 8," +
                "    \"name\": \"test-name\"" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            assertEquals("Duplicate site_ids not permitted", response.bodyAsJsonObject().getString("message"));

            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetUpdateDisallowDuplicates(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        doReturn(new Site(5, "test", true)).when(siteProvider).getSite(8);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [8, 8],\n" +
                "    \"keyset_id\": 1," +
                "    \"name\": \"test-name\"" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            assertEquals("Duplicate site_ids not permitted", response.bodyAsJsonObject().getString("message"));

            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetNewDiscardSelf(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        mockSiteExistence(8, 5);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [8, 5],\n" +
                "    \"site_id\": 8," +
                "    \"name\": \"test-name\"" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            Keyset expected = new Keyset(2, 8, "test", Set.of(5), Instant.now().getEpochSecond(), true, true);
            compareKeysetToResult(expected, response.bodyAsJsonObject().getJsonArray("allowed_sites"));

            assertEquals(expected.getAllowedSites(), keysets.get(2).getAllowedSites());
            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetUpdateDiscardSelf(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        mockSiteExistence(5, 8);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [8, 5],\n" +
                "    \"keyset_id\": 1," +
                "    \"name\": \"test-name\"" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            Keyset expected = new Keyset(1, 5, "test", Set.of(8), Instant.now().getEpochSecond(), true, true);
            compareKeysetToResult(expected, response.bodyAsJsonObject().getJsonArray("allowed_sites"));

            assertEquals(expected.getAllowedSites(), keysets.get(1).getAllowedSites());
            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetNewDisallowMultipleForSite(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        mockSiteExistence(5, 8);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [8],\n" +
                "    \"site_id\": 5" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            assertEquals("Keyset already exists for site: 5", response.bodyAsJsonObject().getString("message"));

            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetNewNullAllowedSites(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        mockSiteExistence(5, 3);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"site_id\": 3" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            Keyset expected = new Keyset(2, 1, "test", null, Instant.now().getEpochSecond(), true, true);
            assertEquals(null, response.bodyAsJsonObject().getJsonArray("allowed_sites"));

            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetNewExplicitlyNullAllowedSites(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        mockSiteExistence(5, 3);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"site_id\": 3," +
                "    \"allowed_sites\": null" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            Keyset expected = new Keyset(2, 1, "test", null, Instant.now().getEpochSecond(), true, true);
            assertEquals(null, response.bodyAsJsonObject().getJsonArray("allowed_sites"));

            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetUpdateNullAllowedSites(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        mockSiteExistence(5);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"keyset_id\": 1" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            Keyset expected = new Keyset(1, 5, "test", null, Instant.now().getEpochSecond(), true, true);
            assertEquals(null, response.bodyAsJsonObject().getJsonArray("allowed_sites"));

            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetUpdateExplicitlyNullAllowedSites(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        mockSiteExistence(5);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, new Keyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true));
        }};

        setKeysets(keysets);

        String body = "  {\n" +
                "    \"keyset_id\": 1," +
                "    \"allowed_sites\": null" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            Keyset expected = new Keyset(1, 5, "test", null, Instant.now().getEpochSecond(), true, true);
            assertEquals(null, response.bodyAsJsonObject().getJsonArray("allowed_sites"));

            testContext.completeNow();
        });
    }
}
