package com.uid2.admin.vertx;

import com.uid2.admin.auth.AdminKeyset;
import com.uid2.admin.managers.KeysetManager;
import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.service.SharingService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.Const;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.ClientType;
import com.uid2.shared.model.Site;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

public class SharingServiceTest extends ServiceTestBase {
    @Override
    protected IService createService() {
        KeysetManager keysetManager = new KeysetManager(adminKeysetProvider, adminKeysetWriter, keysetKeyManager, true);
        return new SharingService(auth, writeLock, adminKeysetProvider, keysetManager, siteProvider, true);
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
    
    private void mockSiteExistence(Integer... sites){
        for(Integer site : sites) {
            doReturn(new Site(site, "test-name", true, null)).when(siteProvider).getSite(site);
        }
    }

    private static Stream<Arguments> listSiteGet() {
        return Stream.of(
                Arguments.of(1, 5),
                Arguments.of(3, 4)
        );
    }

    @ParameterizedTest
    @MethodSource("listSiteGet")
    void listSiteGet(int keySetId, int siteId, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SHARING_PORTAL);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(2, new AdminKeyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(3, new AdminKeyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true, Set.of(ClientType.DSP)));
        }};

        setAdminKeysets(keysets);
        get(vertx, testContext, "api/sharing/list/" + siteId, response -> {
            assertEquals(200, response.statusCode());

            compareKeysetListToResult(keysets.get(keySetId), response.bodyAsJsonObject().getJsonArray("allowed_sites"));
            compareKeysetTypeListToResult(keysets.get(keySetId), response.bodyAsJsonObject().getJsonArray("allowed_types"));

            Integer expectedHash = keysets.get(keySetId).hashCode();
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
        get(vertx, testContext, "api/sharing/list/42", response -> {
            assertEquals(404, response.statusCode());

            testContext.completeNow();
        });
    }

    @Test
    void listSiteSet(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SHARING_PORTAL);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(3, new AdminKeyset(3, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(4, new AdminKeyset(4, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(5, new AdminKeyset(5, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};
        mockSiteExistence(5,7,4,22,25,6);

        setAdminKeysets(keysets);
        mockSiteExistence(5,7,4);

        String body = "  {\n" +
                "    \"allowed_sites\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"hash\": " + keysets.get(3).hashCode() + "\n" +
                "  }";

        post(vertx, testContext, "api/sharing/list/5", body, response -> {
            assertEquals(200, response.statusCode());

            AdminKeyset expected = new AdminKeyset(3, 5, "test", Set.of(22, 25, 6), Instant.now().getEpochSecond(), true, true, new HashSet<>());
            compareKeysetListToResult(expected, response.bodyAsJsonObject().getJsonArray("allowed_sites"));

            assertEquals(expected.getAllowedSites(), keysets.get(3).getAllowedSites());
            testContext.completeNow();
        });
    }

    @Test
    void listSiteSetWithNullExistingAllowedSites(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SHARING_PORTAL);

        final int myKeysetId = 3;
        final int mySiteId = 5;
        final int anotherSiteId = 22;

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(myKeysetId, new AdminKeyset(myKeysetId, mySiteId, "test", null, Instant.now().getEpochSecond(), true, true, new HashSet<>()));
        }};
        mockSiteExistence(mySiteId, anotherSiteId);

        setAdminKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [\n" +
                "      " + anotherSiteId + "\n" +
                "    ],\n" +
                "    \"hash\": " + keysets.get(myKeysetId).hashCode() + "\n" +
                "  }";

        post(vertx, testContext, "api/sharing/list/" + mySiteId, body, response -> {
            assertEquals(200, response.statusCode());

            AdminKeyset expected = new AdminKeyset(myKeysetId, mySiteId, "test", Set.of(anotherSiteId), Instant.now().getEpochSecond(), true, true, new HashSet<>());
            compareKeysetListToResult(expected, response.bodyAsJsonObject().getJsonArray("allowed_sites"));

            assertEquals(expected.getAllowedSites(), keysets.get(myKeysetId).getAllowedSites());
            testContext.completeNow();
        });
    }

    @Test
    void listSiteSetWithTypeList(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SHARING_PORTAL);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(3, new AdminKeyset(3, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(4, new AdminKeyset(4, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(5, new AdminKeyset(5, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);
        mockSiteExistence(5, 7, 4, 22, 25, 6);
        String body = "  {\n" +
                "    \"allowed_sites\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"allowed_types\": [ \n" +
                "    \"DSP\",\n"+
                "    \"ADVERTISER\"\n"+
                "    ],\n" +
                "    \"hash\": " + keysets.get(3).hashCode() + "\n" +
                "  }";

        post(vertx, testContext, "api/sharing/list/5", body, response -> {
            assertEquals(200, response.statusCode());

            AdminKeyset expected = new AdminKeyset(3, 5, "test", Set.of(22, 25, 6), Instant.now().getEpochSecond(), true, true, Set.of(ClientType.DSP, ClientType.ADVERTISER));
            compareKeysetListToResult(expected, response.bodyAsJsonObject().getJsonArray("allowed_sites"));
            compareKeysetTypeListToResult(expected, response.bodyAsJsonObject().getJsonArray("allowed_types"));

            assertEquals(expected.getAllowedSites(), keysets.get(3).getAllowedSites());
            assertEquals(expected.getAllowedTypes(), keysets.get(3).getAllowedTypes());
            testContext.completeNow();
        });
    }

    @Test
    void listSiteSetNew(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SHARING_PORTAL);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(3, new AdminKeyset(3, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(4, new AdminKeyset(4, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(5, new AdminKeyset(5, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);
        mockSiteExistence(5,7,4,8,22,25,6);

        String body = "  {\n" +
                "    \"allowed_sites\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"hash\": 0\n" +
                "  }";

        post(vertx, testContext, "api/sharing/list/8", body, response -> {
            assertEquals(200, response.statusCode());

            AdminKeyset expected = new AdminKeyset(6, 8, "test", Set.of(22, 25, 6), Instant.now().getEpochSecond(), true, true, new HashSet<>());
            compareKeysetListToResult(expected, response.bodyAsJsonObject().getJsonArray("allowed_sites"));

            assertEquals(expected.getAllowedSites(), keysets.get(6).getAllowedSites());

            //Ensure new key was created
            verify(keysetKeyManager).addKeysetKey(6);
            testContext.completeNow();
        });
    }

    @Test
    void listSiteSetNewWithType(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SHARING_PORTAL);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(3, new AdminKeyset(3, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(4, new AdminKeyset(4, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(5, new AdminKeyset(5, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);
        mockSiteExistence(5, 7, 4, 8, 22, 25, 6);

        String body = "  {\n" +
                "    \"allowed_sites\": [\n" +
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

        post(vertx, testContext, "api/sharing/list/8", body, response -> {
            assertEquals(200, response.statusCode());

            AdminKeyset expected = new AdminKeyset(6, 8, "test", Set.of(22, 25, 6), Instant.now().getEpochSecond(), true, true, Set.of(ClientType.DSP, ClientType.ADVERTISER));
            compareKeysetListToResult(expected, response.bodyAsJsonObject().getJsonArray("allowed_sites"));
            compareKeysetTypeListToResult(expected, response.bodyAsJsonObject().getJsonArray("allowed_types"));

            assertEquals(expected.getAllowedSites(), keysets.get(6).getAllowedSites());
            assertEquals(expected.getAllowedTypes(), keysets.get(6).getAllowedTypes());

            //Ensure new key was created
            verify(keysetKeyManager).addKeysetKey(6);
            testContext.completeNow();
        });
    }

    @Test
    void listSiteSetNotAllowed(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SHARING_PORTAL);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(-1, -1, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(2, new AdminKeyset(4, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(3, new AdminKeyset(5, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"hash\": 0\n" +
                "  }";

        post(vertx, testContext, "api/sharing/list/-1", body, response -> {
            assertEquals(400, response.statusCode());
            testContext.completeNow();
        });
    }

    @Test
    void listSiteSetConcurrency(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SHARING_PORTAL);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(3, new AdminKeyset(3, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(4, new AdminKeyset(4, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);
        mockSiteExistence(5,7,22,25,6,2);

        String body1 = "  {\n" +
                "    \"allowed_sites\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"hash\": " + keysets.get(3).hashCode() + "\n" +
                "  }";

        String body2 = "  {\n" +
                "    \"allowed_sites\": [\n" +
                "      2,\n" +
                "      5,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"hash\": " + keysets.get(3).hashCode() + "\n" +
                "  }";

        post(vertx, testContext, "api/sharing/list/5", body1, response -> {
            assertEquals(200, response.statusCode());

            AdminKeyset expected = new AdminKeyset(3, 5, "test", Set.of(22, 25, 6), Instant.now().getEpochSecond(), true, true, new HashSet<>());
            compareKeysetListToResult(expected, response.bodyAsJsonObject().getJsonArray("allowed_sites"));

            assertEquals(expected.getAllowedSites(), keysets.get(3).getAllowedSites());

            post(vertx, testContext, "api/sharing/list/5", body2, response2 -> {
                assertEquals(409, response2.statusCode());

                testContext.completeNow();
            });
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
        get(vertx, testContext, "api/sharing/lists", response -> {
            assertEquals(200, response.statusCode());

            JsonArray respArray = response.bodyAsJsonArray();

            for (int i = 0; i < keysets.size(); i++) {
                JsonObject resp = respArray.getJsonObject(i);
                int keyset_id = resp.getInteger("keyset_id");
                compareKeysetListToResult(keysets.get(keyset_id), resp.getJsonArray("allowed_sites"));
                compareKeysetTypeListToResult(keysets.get(keyset_id), resp.getJsonArray("allowed_types"));

                Integer expectedHash = keysets.get(keyset_id).hashCode();
                assertEquals(expectedHash, resp.getInteger("hash"));
            }

            testContext.completeNow();
        });
    }

    @Test
    void KeysetList(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, Set.of(ClientType.DSP)));
            put(2, new AdminKeyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(3, new AdminKeyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);
        get(vertx, testContext, "api/sharing/keyset/1", response -> {
            assertEquals(200, response.statusCode());

            compareKeysetListToResult(keysets.get(1), response.bodyAsJsonObject().getJsonArray("allowed_sites"));
            compareKeysetTypeListToResult(keysets.get(1), response.bodyAsJsonObject().getJsonArray("allowed_types"));

            testContext.completeNow();
        });
    }

    @Test
    void KeysetListNotFound(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(2, new AdminKeyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(3, new AdminKeyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);
        get(vertx, testContext, "api/sharing/keyset/1", response -> {
            assertEquals(404, response.statusCode());
            assertEquals("Failed to find keyset for keyset_id: 1", response.bodyAsJsonObject().getString("message"));

            testContext.completeNow();
        });
    }

    @Test
    void listAllKeysets(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(2, new AdminKeyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(3, new AdminKeyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true, Set.of(ClientType.DSP, ClientType.ADVERTISER, ClientType.PUBLISHER, ClientType.DATA_PROVIDER)));
        }};

        setAdminKeysets(keysets);
        get(vertx, testContext, "api/sharing/keysets", response -> {
            assertEquals(200, response.statusCode());

            JsonArray respArray = response.bodyAsJsonArray();

            for (int i = 0; i < keysets.size(); i++) {
                JsonObject resp = respArray.getJsonObject(i);
                int keyset_id = resp.getInteger("keyset_id");
                compareKeysetListToResult(keysets.get(keyset_id), resp.getJsonArray("allowed_sites"));
                compareKeysetTypeListToResult(keysets.get(keyset_id), resp.getJsonArray("allowed_types"));
            }

            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetNoSiteIdOrKeysetId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(2, new AdminKeyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(3, new AdminKeyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "     \"name\": \"test-name\"" +
                "  }";

        post(vertx, testContext, "api/sharing/keyset", body, response -> {
            assertEquals(400, response.statusCode());
            assertEquals("You must specify exactly one of: keyset_id, site_id", response.bodyAsJsonObject().getString("message"));

            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetBothSiteIdAndKeysetId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(2, new AdminKeyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(3, new AdminKeyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);

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

        post(vertx, testContext, "api/sharing/keyset", body, response -> {
            assertEquals(400, response.statusCode());
            assertEquals("You must specify exactly one of: keyset_id, site_id", response.bodyAsJsonObject().getString("message"));

            testContext.completeNow();
        });
    }

    @Test
    void KeysetCanUpdateAllowedSitesAndName(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        mockSiteExistence(22, 25, 6);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(2, new AdminKeyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(3, new AdminKeyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"keyset_id\": 1," +
                "     \"name\": \"test-name\"" +
                "  }";

        post(vertx, testContext, "api/sharing/keyset", body, response -> {
            assertEquals(200, response.statusCode());

            AdminKeyset expected = new AdminKeyset(1, 5, "test", Set.of(22, 25, 6), Instant.now().getEpochSecond(), true, true, new HashSet<>());
            compareKeysetListToResult(expected, response.bodyAsJsonObject().getJsonArray("allowed_sites"));

            assertEquals(expected.getAllowedSites(), keysets.get(1).getAllowedSites());
            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetWithType(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(3, new AdminKeyset(3, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(4, new AdminKeyset(4, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(5, new AdminKeyset(5, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);
        mockSiteExistence(5, 7, 4, 22, 25, 6);

        String body = "  {\n" +
                "    \"allowed_sites\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"allowed_types\": [ \n" +
                "    \"DSP\",\n"+
                "    \"ADVERTISER\"\n"+
                "    ],\n" +
                "    \"keyset_id\": 3" +
                "  }";

        post(vertx, testContext, "api/sharing/keyset", body, response -> {
            assertEquals(200, response.statusCode());

            AdminKeyset expected = new AdminKeyset(3, 5, "test", Set.of(22, 25, 6), Instant.now().getEpochSecond(), true, true, Set.of(ClientType.DSP, ClientType.ADVERTISER));
            compareKeysetListToResult(expected, response.bodyAsJsonObject().getJsonArray("allowed_sites"));
            compareKeysetTypeListToResult(expected, response.bodyAsJsonObject().getJsonArray("allowed_types"));

            assertEquals(expected.getAllowedSites(), keysets.get(3).getAllowedSites());
            assertEquals(expected.getAllowedTypes(), keysets.get(3).getAllowedTypes());
            testContext.completeNow();
        });
    }

/*    @Test
    void KeysetSetNew(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {
            {
                put(1, new AdminKeyset(1, 5, "test", Set.of(4, 6, 7), Instant.now().getEpochSecond(), true, true, new HashSet<>()));
                put(2, new AdminKeyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(), true, true, new HashSet<>()));
                put(3, new AdminKeyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(), true, true, new HashSet<>()));
            }
        };
    }*/

    @Test
    void KeysetCanUpdateAllowedSites(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        mockSiteExistence(5, 22, 25, 6);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 5, "test-name", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(2, new AdminKeyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(3, new AdminKeyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"keyset_id\": 1" +
                "  }";

        post(vertx, testContext, "api/sharing/keyset", body, response -> {
            assertEquals(200, response.statusCode());

            AdminKeyset expected = new AdminKeyset(1, 5, "test-name", Set.of(22, 25, 6), Instant.now().getEpochSecond(), true, true, new HashSet<>());
            compareKeysetListToResult(expected, response.bodyAsJsonObject().getJsonArray("allowed_sites"));

            assertEquals(expected.getAllowedSites(), keysets.get(1).getAllowedSites());
            testContext.completeNow();
        });
    }

    @Test
    void KeysetCanMakeNew(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        mockSiteExistence(8, 22, 25, 6);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 123, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(2, new AdminKeyset(2, 124, "test", Set.of(12), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(3, new AdminKeyset(3, 125, "test", Set.of(5), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"site_id\": 8," +
                "    \"name\": \"test-name\"" +
                "  }";

        post(vertx, testContext, "api/sharing/keyset", body, response -> {
            assertEquals(200, response.statusCode());

            AdminKeyset expected = new AdminKeyset(4, 8, "test-name", Set.of(22, 25, 6), Instant.now().getEpochSecond(), true, true, new HashSet<>());
            compareKeysetListToResult(expected, response.bodyAsJsonObject().getJsonArray("allowed_sites"));

            assertEquals(expected.getAllowedSites(), keysets.get(4).getAllowedSites());
            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetBadSiteId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        doReturn(null).when(siteProvider).getSite(5);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(2, new AdminKeyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(3, new AdminKeyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"site_id\": 5," +
                "     \"name\": \"test-name\"" +
                "  }";

        post(vertx, testContext, "api/sharing/keyset", body, response -> {
            assertEquals(400, response.statusCode());
            assertEquals("Site id 5 not valid", response.bodyAsJsonObject().getString("message"));

            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetBadWhitelistSiteId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        mockSiteExistence(5, 22);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(2, new AdminKeyset(2, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(3, new AdminKeyset(3, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"site_id\": 5," +
                "     \"name\": \"test-name\"" +
                "  }";

        post(vertx, testContext, "api/sharing/keyset", body, response -> {
            assertEquals(400, response.statusCode());
            assertEquals("Site id 25 not valid", response.bodyAsJsonObject().getString("message"));

            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetIgnoresAlreadySetSitesWhenChecking(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        // 25 is not an existing site
        mockSiteExistence(5, 22, 6);

        // But 25 is already in the list here
        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(3, new AdminKeyset(3, 7, "test", Set.of(12, 25), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(4, new AdminKeyset(4, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);

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

        post(vertx, testContext, "api/sharing/keyset", body, response -> {
            assertEquals(200, response.statusCode());

            testContext.completeNow();
        });
    }

    // This test should be enabled when multiple keysets is enabled
    @Test
    @Disabled
    void KeysetSetNewIdenticalNameAndSiteId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        mockSiteExistence(8, 22, 25, 6);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 8, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"site_id\": 8," +
                "    \"name\": \"TEST\"" +
                "  }";

        post(vertx, testContext, "api/sharing/keyset", body, response -> {
            assertEquals(400, response.statusCode());
            assertEquals("AdminKeyset with same site_id and name already exists", response.bodyAsJsonObject().getString("message"));

            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetNewSameNameDifferentSite(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        mockSiteExistence(8, 22, 25, 6);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(3, new AdminKeyset(3, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"site_id\": 8," +
                "    \"name\": \"test\"" +
                "  }";

        post(vertx, testContext, "api/sharing/keyset", body, response -> {
            assertEquals(200, response.statusCode());

            AdminKeyset expected = new AdminKeyset(4, 8, "test", Set.of(22, 25, 6), Instant.now().getEpochSecond(), true, true, new HashSet<>());
            compareKeysetListToResult(expected, response.bodyAsJsonObject().getJsonArray("allowed_sites"));

            assertEquals(expected.getAllowedSites(), keysets.get(4).getAllowedSites());
            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetNewEmptyAllowedSites(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        doReturn(new Site(8, "test", true)).when(siteProvider).getSite(8);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(2, new AdminKeyset(2, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [],\n" +
                "    \"site_id\": 8," +
                "    \"name\": \"test-name\"" +
                "  }";

        post(vertx, testContext, "api/sharing/keyset", body, response -> {
            assertEquals(200, response.statusCode());

            AdminKeyset expected = new AdminKeyset(4, 8, "test", Set.of(), Instant.now().getEpochSecond(), true, true, new HashSet<>());
            compareKeysetListToResult(expected, response.bodyAsJsonObject().getJsonArray("allowed_sites"));

            assertEquals(expected.getAllowedSites(), keysets.get(4).getAllowedSites());
            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetUpdateEmptyAllowedSites(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        doReturn(new Site(5, "test", true)).when(siteProvider).getSite(5);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [],\n" +
                "    \"name\": \"test-name\"," +
                "    \"keyset_id\": 1" +
                "  }";

        post(vertx, testContext, "api/sharing/keyset", body, response -> {
            assertEquals(200, response.statusCode());

            AdminKeyset expected = new AdminKeyset(1, 5, "test", Set.of(), Instant.now().getEpochSecond(), true, true, new HashSet<>());
            compareKeysetListToResult(expected, response.bodyAsJsonObject().getJsonArray("allowed_sites"));

            assertEquals(expected.getAllowedSites(), keysets.get(1).getAllowedSites());
            testContext.completeNow();
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {Const.Data.AdvertisingTokenSiteId, Const.Data.RefreshKeySiteId, Const.Data.MasterKeySiteId})
    void KeysetSetNewReservedSite(int input, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        doReturn(new Site(input, "test", true)).when(siteProvider).getSite(input);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [],\n" +
                String.format("    \"site_id\": %d,", input) +
                "    \"name\": \"test-name\"" +
                "  }";

        post(vertx, testContext, "api/sharing/keyset", body, response -> {
            assertEquals(400, response.statusCode());
            assertEquals("Site id " + input + " not valid", response.bodyAsJsonObject().getString("message"));

            testContext.completeNow();
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {Const.Data.MasterKeysetId, Const.Data.RefreshKeysetId, Const.Data.FallbackPublisherKeysetId})
    void KeysetSetReservedKeyset(int input, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        doReturn(new Site(input, "test", true)).when(siteProvider).getSite(input);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer,AdminKeyset >() {{
            put(input, new AdminKeyset(input, input, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [],\n" +
                String.format("    \"keyset_id\": %d,", input) +
                "    \"name\": \"test-name\"" +
                "  }";

        post(vertx, testContext, "api/sharing/keyset", body, response -> {
            assertEquals(400, response.statusCode());
            assertEquals("Keyset id: " + input + " is not valid", response.bodyAsJsonObject().getString("message"));

            testContext.completeNow();
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {Const.Data.AdvertisingTokenSiteId, Const.Data.RefreshKeySiteId, Const.Data.MasterKeySiteId})
    void KeysetSetUpdateReservedSite(int input, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        doReturn(new Site(input, "test", true)).when(siteProvider).getSite(input);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 5, "test", Set.of(4, 6, 7), Instant.now().getEpochSecond(), true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [],\n" +
                String.format("    \"site_id\": %d,", input) +
                "    \"name\": \"test-name\"" +
                "  }";

        post(vertx, testContext, "api/sharing/keyset", body, response -> {
            assertEquals(400, response.statusCode());
            assertEquals("Site id " + input + " not valid", response.bodyAsJsonObject().getString("message"));

            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetNewDisallowDuplicates(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        doReturn(new Site(8, "test", true)).when(siteProvider).getSite(8);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [8, 8],\n" +
                "    \"site_id\": 8," +
                "    \"name\": \"test-name\"" +
                "  }";

        post(vertx, testContext, "api/sharing/keyset", body, response -> {
            assertEquals(400, response.statusCode());
            assertEquals("Duplicate site_ids not permitted", response.bodyAsJsonObject().getString("message"));

            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetUpdateDisallowDuplicates(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        doReturn(new Site(5, "test", true)).when(siteProvider).getSite(8);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [8, 8],\n" +
                "    \"keyset_id\": 1," +
                "    \"name\": \"test-name\"" +
                "  }";

        post(vertx, testContext, "api/sharing/keyset", body, response -> {
            assertEquals(400, response.statusCode());
            assertEquals("Duplicate site_ids not permitted", response.bodyAsJsonObject().getString("message"));

            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetNewDiscardSelf(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        mockSiteExistence(8, 5);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(3, new AdminKeyset(3, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);
        mockSiteExistence(5, 8);

        String body = "  {\n" +
                "    \"allowed_sites\": [8, 5],\n" +
                "    \"site_id\": 8," +
                "    \"name\": \"test-name\"" +
                "  }";

        post(vertx, testContext, "api/sharing/keyset", body, response -> {
            assertEquals(200, response.statusCode());

            AdminKeyset expected = new AdminKeyset(4, 8, "test", Set.of(5), Instant.now().getEpochSecond(), true, true, new HashSet<>());
            compareKeysetListToResult(expected, response.bodyAsJsonObject().getJsonArray("allowed_sites"));

            assertEquals(expected.getAllowedSites(), keysets.get(4).getAllowedSites());
            testContext.completeNow();
        });
    }

    @Test
    void xKeysetSetUpdateDiscardSelf(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        mockSiteExistence(5, 8);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [8, 5],\n" +
                "    \"keyset_id\": 1," +
                "    \"name\": \"test-name\"" +
                "  }";

        post(vertx, testContext, "api/sharing/keyset", body, response -> {
            assertEquals(200, response.statusCode());

            AdminKeyset expected = new AdminKeyset(1, 5, "test", Set.of(8), Instant.now().getEpochSecond(), true, true, new HashSet<>());
            compareKeysetListToResult(expected, response.bodyAsJsonObject().getJsonArray("allowed_sites"));

            assertEquals(expected.getAllowedSites(), keysets.get(1).getAllowedSites());
            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetNewDisallowMultipleForSite(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        mockSiteExistence(5, 8);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);

        String body = "  {\n" +
                "    \"allowed_sites\": [8],\n" +
                "    \"site_id\": 5" +
                "  }";

        post(vertx, testContext, "api/sharing/keyset", body, response -> {
            assertEquals(400, response.statusCode());
            assertEquals("Keyset already exists for site: 5", response.bodyAsJsonObject().getString("message"));

            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetNewNullAllowedSites(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        mockSiteExistence(5, 3);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);

        String body = "  {\n" +
                "    \"site_id\": 3" +
                "  }";

        post(vertx, testContext, "api/sharing/keyset", body, response -> {
            assertEquals(200, response.statusCode());

            AdminKeyset expected = new AdminKeyset(2, 1, "test", null, Instant.now().getEpochSecond(), true, true, new HashSet<>());
            assertEquals(null, response.bodyAsJsonObject().getJsonArray("allowed_sites"));

            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetNewExplicitlyNullAllowedSites(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        mockSiteExistence(5, 3);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);

        String body = "  {\n" +
                "    \"site_id\": 3," +
                "    \"allowed_sites\": null" +
                "  }";

        post(vertx, testContext, "api/sharing/keyset", body, response -> {
            assertEquals(200, response.statusCode());

            AdminKeyset expected = new AdminKeyset(2, 1, "test", null, Instant.now().getEpochSecond(), true, true, new HashSet<>());
            assertEquals(null, response.bodyAsJsonObject().getJsonArray("allowed_sites"));

            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetUpdateNullAllowedSites(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        mockSiteExistence(5);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);

        String body = "  {\n" +
                "    \"keyset_id\": 1" +
                "  }";

        post(vertx, testContext, "api/sharing/keyset", body, response -> {
            assertEquals(200, response.statusCode());

            AdminKeyset expected = new AdminKeyset(1, 5, "test", null, Instant.now().getEpochSecond(), true, true, new HashSet<>());
            assertEquals(null, response.bodyAsJsonObject().getJsonArray("allowed_sites"));

            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetUpdateExplicitlyNullAllowedSites(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        mockSiteExistence(5);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, new AdminKeyset(1, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);

        String body = "  {\n" +
                "    \"keyset_id\": 1," +
                "    \"allowed_sites\": null" +
                "  }";

        post(vertx, testContext, "api/sharing/keyset", body, response -> {
            assertEquals(200, response.statusCode());

            AdminKeyset expected = new AdminKeyset(1, 5, "test", null, Instant.now().getEpochSecond(), true, true, new HashSet<>());
            assertEquals(null, response.bodyAsJsonObject().getJsonArray("allowed_sites"));

            testContext.completeNow();
        });
    }

    @Test
    void KeysetSetNewWithType(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(3, new AdminKeyset(3, 5, "test", Set.of(4,6,7), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(4, new AdminKeyset(4, 7, "test", Set.of(12), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
            put(5, new AdminKeyset(5, 4, "test", Set.of(5), Instant.now().getEpochSecond(),true, true, new HashSet<>()));
        }};

        setAdminKeysets(keysets);
        mockSiteExistence(5, 7, 4, 8, 22, 25, 6);

        String body = "  {\n" +
                "    \"allowed_sites\": [\n" +
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

        post(vertx, testContext, "api/sharing/keyset", body, response -> {
            assertEquals(200, response.statusCode());

            AdminKeyset expected = new AdminKeyset(6, 8, "test", Set.of(22, 25, 6), Instant.now().getEpochSecond(), true, true, Set.of(ClientType.DSP, ClientType.ADVERTISER));
            compareKeysetListToResult(expected, response.bodyAsJsonObject().getJsonArray("allowed_sites"));
            compareKeysetTypeListToResult(expected, response.bodyAsJsonObject().getJsonArray("allowed_types"));

            assertEquals(expected.getAllowedSites(), keysets.get(6).getAllowedSites());
            assertEquals(expected.getAllowedTypes(), keysets.get(6).getAllowedTypes());
            testContext.completeNow();
        });
    }
}
