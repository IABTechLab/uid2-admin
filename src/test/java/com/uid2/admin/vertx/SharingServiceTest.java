package com.uid2.admin.vertx;

import com.uid2.admin.auth.AdminKeyset;
import com.uid2.admin.model.ClientType;
import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.service.SharingService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.auth.Role;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

public class SharingServiceTest extends ServiceTestBase {
    @Override
    protected IService createService() {
        return new SharingService(auth, writeLock, adminKeysetWriter, adminKeysetProvider, keysetKeyManager);
    }

    private void compareKeysetListToResult(AdminKeyset keyset, JsonArray actualList) {
        assertNotNull(actualList);
        Set<Integer> actualSet = actualList.stream()
                .map(s -> (Integer) s)
                .collect(Collectors.toSet());
        assertEquals(keyset.getAllowedSites(), actualSet);
    }

    private void compareKeysetTypeListToResult(AdminKeyset keyset, JsonArray actualList) {
        assertNotNull(actualList);
        Set<ClientType> actualSet = actualList.stream()
                .map(s -> Enum.valueOf(ClientType.class, s.toString()))
                .collect(Collectors.toSet());
        assertEquals(keyset.getAllowedTypes(), actualSet);
    }


    @Test
    void listSiteGet(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SHARING_PORTAL);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(2, new AdminKeyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(3, new AdminKeyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true, Set.of(ClientType.DSP)));
        }};

        setAdminKeysets(keysets);
        get(vertx, "api/sharing/list/5", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            compareKeysetListToResult(keysets.get(1), response.bodyAsJsonObject().getJsonArray("allowlist"));

            Integer expectedHash = keysets.get(1).hashCode();
            assertEquals(expectedHash, response.bodyAsJsonObject().getInteger("hash"));

            testContext.completeNow();
        });
        get(vertx, "api/sharing/list/4", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            compareKeysetListToResult(keysets.get(3), response.bodyAsJsonObject().getJsonArray("allowlist"));
            compareKeysetTypeListToResult(keysets.get(3), response.bodyAsJsonObject().getJsonArray("allowed_types"));

            Integer expectedHash = keysets.get(3).hashCode();
            assertEquals(expectedHash, response.bodyAsJsonObject().getInteger("hash"));

            testContext.completeNow();
        });
    }

    @Test
    void listSiteGetNotFound(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SHARING_PORTAL);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(2, new AdminKeyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(3, new AdminKeyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);
        get(vertx, "api/sharing/list/42", ar -> {
            HttpResponse response = ar.result();
            assertEquals(404, response.statusCode());

            testContext.completeNow();
        });
    }

    @Test
    void listSiteSet(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SHARING_PORTAL);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(2, new AdminKeyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(3, new AdminKeyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);

        String body = "  {\n" +
                "    \"allowlist\": [\n" +
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

            AdminKeyset expected = new AdminKeyset(1, 5, "test", Set.of(22, 25, 6), Instant.now().getEpochSecond(), true, true, new HashSet<>());
            compareKeysetListToResult(expected, response.bodyAsJsonObject().getJsonArray("allowlist"));

            assertEquals(expected.getAllowedSites(), keysets.get(1).getAllowedSites());
            testContext.completeNow();
        });
    }

    @Test
    void listSiteSetWithTypeList(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SHARING_PORTAL);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(2, new AdminKeyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(3, new AdminKeyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);

        String body = "  {\n" +
                "    \"allowlist\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"allowed_types\": [ \n" +
                "    \"DSP\",\n"+
                "    \"ADVERTISER\"\n"+
                "    ],\n" +
                "    \"hash\": " + keysets.get(1).hashCode() + "\n" +
                "  }";

        post(vertx, "api/sharing/list/5", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            AdminKeyset expected = new AdminKeyset(1, 5, "test", Set.of(22, 25, 6), Instant.now().getEpochSecond(), true, true, Set.of(ClientType.DSP, ClientType.ADVERTISER));
            compareKeysetListToResult(expected, response.bodyAsJsonObject().getJsonArray("allowlist"));
            compareKeysetTypeListToResult(expected, response.bodyAsJsonObject().getJsonArray("allowed_types"));

            assertEquals(expected.getAllowedSites(), keysets.get(1).getAllowedSites());
            assertEquals(expected.getAllowedTypes(), keysets.get(1).getAllowedTypes());
            testContext.completeNow();
        });
    }

    @Test
    void listSiteSetNew(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SHARING_PORTAL);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(2, new AdminKeyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(3, new AdminKeyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);

        String body = "  {\n" +
                "    \"allowlist\": [\n" +
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

            AdminKeyset expected = new AdminKeyset(4, 8, "test", Set.of(22, 25, 6), Instant.now().getEpochSecond(), true, true, new HashSet<>());
            compareKeysetListToResult(expected, response.bodyAsJsonObject().getJsonArray("allowlist"));

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
    void listSiteSetNewWithType(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SHARING_PORTAL);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(2, new AdminKeyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(3, new AdminKeyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);

        String body = "  {\n" +
                "    \"allowlist\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"allowed_types\": [ \n" +
                "    \"DSP\",\n"+
                "    \"ADVERTISER\"\n"+
                "    ],\n" +
                "    \"hash\": 0\n" +
                "  }";

        post(vertx, "api/sharing/list/8", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            AdminKeyset expected = new AdminKeyset(4, 8, "test", Set.of(22, 25, 6), Instant.now().getEpochSecond(), true, true, Set.of(ClientType.DSP, ClientType.ADVERTISER));
            compareKeysetListToResult(expected, response.bodyAsJsonObject().getJsonArray("allowlist"));
            compareKeysetTypeListToResult(expected, response.bodyAsJsonObject().getJsonArray("allowed_types"));

            assertEquals(expected.getAllowedSites(), keysets.get(4).getAllowedSites());
            assertEquals(expected.getAllowedTypes(), keysets.get(4).getAllowedTypes());

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
    void listSiteSetConcurrency(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SHARING_PORTAL);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(2, new AdminKeyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);

        String body1 = "  {\n" +
                "    \"allowlist\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"hash\": " + keysets.get(1).hashCode() + "\n" +
                "  }";

        String body2 = "  {\n" +
                "    \"allowlist\": [\n" +
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

            AdminKeyset expected = new AdminKeyset(1, 5, "test", Set.of(22, 25, 6), Instant.now().getEpochSecond(), true, true, new HashSet<>());
            compareKeysetListToResult(expected, response.bodyAsJsonObject().getJsonArray("whitelist"));

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

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(2, new AdminKeyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(3, new AdminKeyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true, Set.of(ClientType.DSP)));
        }};

        setAdminKeysets(keysets);
        get(vertx, "api/sharing/lists", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            JsonArray respArray = response.bodyAsJsonArray();

            for (int i = 0; i < keysets.size(); i++) {
                JsonObject resp = respArray.getJsonObject(i);
                int keyset_id = resp.getInteger("keyset_id");
                compareKeysetListToResult(keysets.get(keyset_id), resp.getJsonArray("allowlist"));
                compareKeysetTypeListToResult(keysets.get(keyset_id), resp.getJsonArray("allowed_types"));

                Integer expectedHash = keysets.get(keyset_id).hashCode();
                assertEquals(expectedHash, resp.getInteger("hash"));
            }

            testContext.completeNow();
        });
    }

    @Test
    void KeysetList(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, Set.of(ClientType.DSP)));
            put(2, new AdminKeyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(3, new AdminKeyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);
        get(vertx, "api/sharing/keyset/1", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            compareKeysetListToResult(keysets.get(1), response.bodyAsJsonObject().getJsonArray("allowlist"));
            compareKeysetTypeListToResult(keysets.get(1), response.bodyAsJsonObject().getJsonArray("allowed_types"));

            testContext.completeNow();
        });
    }

    @Test
    void listAllKeysets(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(2, new AdminKeyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(3, new AdminKeyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true, Set.of(ClientType.DSP, ClientType.ADVERTISER, ClientType.PUBLISHER, ClientType.DATA_PROVIDER)));
        }};

        setAdminKeysets(keysets);
        get(vertx, "api/sharing/keysets", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            JsonArray respArray = response.bodyAsJsonArray();

            for (int i = 0; i < keysets.size(); i++) {
                JsonObject resp = respArray.getJsonObject(i);
                int keyset_id = resp.getInteger("keyset_id");
                compareKeysetListToResult(keysets.get(keyset_id), resp.getJsonArray("allowlist"));
                compareKeysetTypeListToResult(keysets.get(keyset_id), resp.getJsonArray("allowed_types"));
            }

            testContext.completeNow();
        });
    }

    @Test
    void KeysetSet(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(2, new AdminKeyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(3, new AdminKeyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);

        String body = "  {\n" +
                "    \"allowlist\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"keyset_id\": 1," +
                "    \"site_id\": 5" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            AdminKeyset expected = new AdminKeyset(1, 5, "test", Set.of(22, 25, 6), Instant.now().getEpochSecond(), true, true, new HashSet<>());
            compareKeysetListToResult(expected, response.bodyAsJsonObject().getJsonArray("allowlist"));

            assertEquals(expected.getAllowedSites(), keysets.get(1).getAllowedSites());
            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetWithType(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(2, new AdminKeyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(3, new AdminKeyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);

        String body = "  {\n" +
                "    \"allowlist\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"allowed_types\": [ \n" +
                "    \"DSP\",\n"+
                "    \"ADVERTISER\"\n"+
                "    ],\n" +
                "    \"keyset_id\": 1," +
                "    \"site_id\": 5" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            AdminKeyset expected = new AdminKeyset(1, 5, "test", Set.of(22, 25, 6), Instant.now().getEpochSecond(), true, true, Set.of(ClientType.DSP, ClientType.ADVERTISER));
            compareKeysetListToResult(expected, response.bodyAsJsonObject().getJsonArray("allowlist"));
            compareKeysetTypeListToResult(expected, response.bodyAsJsonObject().getJsonArray("allowed_types"));

            assertEquals(expected.getAllowedSites(), keysets.get(1).getAllowedSites());
            assertEquals(expected.getAllowedTypes(), keysets.get(1).getAllowedTypes());
            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetNew(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(2, new AdminKeyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(3, new AdminKeyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);

        String body = "  {\n" +
                "    \"allowlist\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"site_id\": 8" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            AdminKeyset expected = new AdminKeyset(4, 8, "test", Set.of(22, 25, 6), Instant.now().getEpochSecond(), true, true, new HashSet<>());
            compareKeysetListToResult(expected, response.bodyAsJsonObject().getJsonArray("allowlist"));

            assertEquals(expected.getAllowedSites(), keysets.get(4).getAllowedSites());
            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetNewWithType(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(2, new AdminKeyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(3, new AdminKeyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);

        String body = "  {\n" +
                "    \"allowlist\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"allowed_types\": [ \n" +
                "    \"DSP\",\n"+
                "    \"ADVERTISER\"\n"+
                "    ],\n" +
                "    \"site_id\": 8" +
                "  }";

        post(vertx, "api/sharing/keyset", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            AdminKeyset expected = new AdminKeyset(4, 8, "test", Set.of(22, 25, 6), Instant.now().getEpochSecond(), true, true, Set.of(ClientType.DSP, ClientType.ADVERTISER));
            compareKeysetListToResult(expected, response.bodyAsJsonObject().getJsonArray("allowlist"));
            compareKeysetTypeListToResult(expected, response.bodyAsJsonObject().getJsonArray("allowed_types"));

            assertEquals(expected.getAllowedSites(), keysets.get(4).getAllowedSites());
            assertEquals(expected.getAllowedTypes(), keysets.get(4).getAllowedTypes());
            testContext.completeNow();
        });
    }
}
