package com.uid2.admin.vertx;

import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.service.KeyAclService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.auth.EncryptionKeyAcl;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.Site;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


public class KeyAclServiceTest extends ServiceTestBase {
    @Override
    protected IService createService() {
        return new KeyAclService(auth, writeLock, keyAclStoreWriter, keyAclProvider, siteProvider, keyManager);
    }

    private void checkEncryptionKeyAclsResponse(Map<Integer, EncryptionKeyAcl> expectedAcls, Object[] actualAcls) {
        assertEquals(expectedAcls.size(), actualAcls.length);
        for (Map.Entry<Integer, EncryptionKeyAcl> expectedAcl : expectedAcls.entrySet()) {
            JsonObject actualAcl = Arrays.stream(actualAcls).map(a -> (JsonObject) a)
                    .filter(a -> a.getInteger("site_id").equals(expectedAcl.getKey()))
                    .findFirst().orElse(null);
            assertNotNull(actualAcl);
            String type = expectedAcl.getValue().getIsWhitelist() ? "whitelist" : "blacklist";
            Set<Integer> actualSet = actualAcl.getJsonArray(type).stream()
                    .map(s -> (Integer) s)
                    .collect(Collectors.toSet());
            assertEquals(expectedAcl.getValue().getAccessList(), actualSet);
        }
    }

    @Test
    void listKeyAclsNoAcls(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);

        get(vertx, testContext, "api/keys_acl/list", response -> {
            assertEquals(200, response.statusCode());
            assertEquals(0, response.bodyAsJsonArray().size());
            testContext.completeNow();
        });
    }

    @Test
    void listKeyAclsHaveAcls(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);

        Map<Integer, EncryptionKeyAcl> acls = new HashMap<Integer, EncryptionKeyAcl>() {{
            put(5, makeKeyAcl(false, 4, 6, 7));
            put(7, makeKeyAcl(true));
            put(4, makeKeyAcl(true, 5));
        }};

        setEncryptionKeyAcls(acls);

        get(vertx, testContext, "api/keys_acl/list", response -> {
            assertEquals(200, response.statusCode());
            checkEncryptionKeyAclsResponse(acls, response.bodyAsJsonArray().stream().toArray());
            testContext.completeNow();
        });
    }

    @Test
    void keyAclResetNoAclToWhitelist(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites(new Site(5, "test_site", true));
        Map<Integer, EncryptionKeyAcl> initialAcls = new HashMap<>();
        Map<Integer, EncryptionKeyAcl> expectedAcl = new HashMap<Integer, EncryptionKeyAcl>() {{
            put(5, makeKeyAcl(true));
        }};
        setEncryptionKeyAcls(initialAcls);
        setEncryptionKeys(123);

        post(vertx, testContext, "api/keys_acl/reset?site_id=5&type=whitelist", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkEncryptionKeyAclsResponse(expectedAcl, new Object[]{response.bodyAsJsonObject()});

            try {
                verify(keyManager).addSiteKey(eq(5));
                verify(keyAclStoreWriter).upload(mapOfSize(1), isNull());
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
    }

    @Test
    void keyAclResetNoAclToBlacklist(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites(new Site(5, "test_site", true));
        Map<Integer, EncryptionKeyAcl> initialAcls = new HashMap<>();
        Map<Integer, EncryptionKeyAcl> expectedAcl = new HashMap<Integer, EncryptionKeyAcl>() {{
            put(5, makeKeyAcl(false));
        }};
        setEncryptionKeyAcls(initialAcls);
        setEncryptionKeys(123);

        post(vertx, testContext, "api/keys_acl/reset?site_id=5&type=blacklist", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkEncryptionKeyAclsResponse(expectedAcl, new Object[]{response.bodyAsJsonObject()});

            try {
                verify(keyManager).addSiteKey(eq(5));
                verify(keyAclStoreWriter).upload(mapOfSize(1), isNull());
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
    }

    @Test
    void keyAclResetWhitelistToBlacklist(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites(new Site(5, "test_site", true));
        Map<Integer, EncryptionKeyAcl> initialAcls = new HashMap<Integer, EncryptionKeyAcl>() {{
            put(5, makeKeyAcl(true));
        }};
        Map<Integer, EncryptionKeyAcl> expectedAcl = new HashMap<Integer, EncryptionKeyAcl>() {{
            put(5, makeKeyAcl(false));
        }};
        setEncryptionKeyAcls(initialAcls);
        setEncryptionKeys(123);

        post(vertx, testContext, "api/keys_acl/reset?site_id=5&type=blacklist", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkEncryptionKeyAclsResponse(expectedAcl, new Object[]{response.bodyAsJsonObject()});

            try {
                verify(keyManager).addSiteKey(eq(5));
                verify(keyAclStoreWriter).upload(mapOfSize(1), isNull());
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
    }

    @Test
    void keyAclResetBlacklistToBlacklist(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites(new Site(5, "test_site", true));
        Map<Integer, EncryptionKeyAcl> initialAcls = new HashMap<Integer, EncryptionKeyAcl>() {{
            put(5, makeKeyAcl(false));
        }};
        Map<Integer, EncryptionKeyAcl> expectedAcl = new HashMap<Integer, EncryptionKeyAcl>() {{
            put(5, makeKeyAcl(false));
        }};
        setEncryptionKeyAcls(initialAcls);
        setEncryptionKeys(123);

        post(vertx, testContext, "api/keys_acl/reset?site_id=5&type=blacklist", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkEncryptionKeyAclsResponse(expectedAcl, new Object[]{response.bodyAsJsonObject()});

            try {
                verify(keyManager).addSiteKey(eq(5));
                verify(keyAclStoreWriter).upload(mapOfSize(1), isNull());
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
    }

    @Test
    void keyAclResetUnknownSite(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites();
        post(vertx, "api/keys_acl/reset?site_id=5&type=blacklist", "", expectHttpError(testContext, 404));
    }

    @Test
    void keyAclResetSpecialSite1(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites();
        post(vertx, "api/keys_acl/reset?site_id=1&type=blacklist", "", expectHttpError(testContext, 400));
    }

    @Test
    void keyAclResetSpecialSite2(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites();
        post(vertx, "api/keys_acl/reset?site_id=2&type=blacklist", "", expectHttpError(testContext, 400));
    }

    @Test
    void keyAclResetUnknownListType(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites(new Site(5, "test_site", true));
        post(vertx, "api/keys_acl/reset?site_id=2&type=unknown_list", "", expectHttpError(testContext, 400));
    }

    @Test
    void keyAclUpdateNoChanges(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites(new Site(5, "test_site", true));
        Map<Integer, EncryptionKeyAcl> initialAcls = new HashMap<Integer, EncryptionKeyAcl>() {{
            put(5, makeKeyAcl(true, 6, 7, 8));
        }};
        Map<Integer, EncryptionKeyAcl> expectedAcl = new HashMap<Integer, EncryptionKeyAcl>() {{
            put(5, makeKeyAcl(true, 6, 7, 8));
        }};
        setEncryptionKeyAcls(initialAcls);
        setEncryptionKeys(123);

        post(vertx, testContext, "api/keys_acl/update?site_id=5", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkEncryptionKeyAclsResponse(expectedAcl, new Object[]{response.bodyAsJsonObject()});

            try {
                verify(keyManager, times(0)).addSiteKey(anyInt());
                verify(keyAclStoreWriter, times(0)).upload(any(), isNull());
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
    }

    @Test
    void keyAclUpdateAddToWhitelist(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites(
                new Site(5, "test_site", true),
                new Site(11, "test_site11", true));
        Map<Integer, EncryptionKeyAcl> initialAcls = new HashMap<Integer, EncryptionKeyAcl>() {{
            put(5, makeKeyAcl(true, 6, 7, 8));
        }};
        Map<Integer, EncryptionKeyAcl> expectedAcl = new HashMap<Integer, EncryptionKeyAcl>() {{
            put(5, makeKeyAcl(true, 6, 7, 8, 11));
        }};
        setEncryptionKeyAcls(initialAcls);
        setEncryptionKeys(123);

        post(vertx, testContext, "api/keys_acl/update?site_id=5&add=11", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkEncryptionKeyAclsResponse(expectedAcl, new Object[]{response.bodyAsJsonObject()});

            try {
                verify(keyManager, times(0)).addSiteKey(anyInt());
                verify(keyAclStoreWriter).upload(mapOfSize(1), isNull());
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
    }

    @Test
    void keyAclUpdateAddToBlacklist(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites(
                new Site(5, "test_site", true),
                new Site(11, "test_site11", true));
        Map<Integer, EncryptionKeyAcl> initialAcls = new HashMap<Integer, EncryptionKeyAcl>() {{
            put(5, makeKeyAcl(false, 6, 7, 8));
        }};
        Map<Integer, EncryptionKeyAcl> expectedAcl = new HashMap<Integer, EncryptionKeyAcl>() {{
            put(5, makeKeyAcl(false, 6, 7, 8, 11));
        }};
        setEncryptionKeyAcls(initialAcls);
        setEncryptionKeys(123);

        post(vertx, testContext, "api/keys_acl/update?site_id=5&add=11", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkEncryptionKeyAclsResponse(expectedAcl, new Object[]{response.bodyAsJsonObject()});

            try {
                verify(keyManager).addSiteKey(eq(5));
                verify(keyAclStoreWriter).upload(mapOfSize(1), isNull());
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
    }

    @Test
    void keyAclUpdateRemoveFromWhitelist(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites(new Site(5, "test_site", true));
        Map<Integer, EncryptionKeyAcl> initialAcls = new HashMap<Integer, EncryptionKeyAcl>() {{
            put(5, makeKeyAcl(true, 6, 7, 8));
        }};
        Map<Integer, EncryptionKeyAcl> expectedAcl = new HashMap<Integer, EncryptionKeyAcl>() {{
            put(5, makeKeyAcl(true, 6, 8));
        }};
        setEncryptionKeyAcls(initialAcls);
        setEncryptionKeys(123);

        post(vertx, testContext, "api/keys_acl/update?site_id=5&remove=7", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkEncryptionKeyAclsResponse(expectedAcl, new Object[]{response.bodyAsJsonObject()});

            try {
                verify(keyManager).addSiteKey(eq(5));
                verify(keyAclStoreWriter).upload(mapOfSize(1), isNull());
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
    }

    @Test
    void keyAclUpdateRemoveFromBlacklist(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites(new Site(5, "test_site", true));
        Map<Integer, EncryptionKeyAcl> initialAcls = new HashMap<Integer, EncryptionKeyAcl>() {{
            put(5, makeKeyAcl(false, 6, 7, 8));
        }};
        Map<Integer, EncryptionKeyAcl> expectedAcl = new HashMap<Integer, EncryptionKeyAcl>() {{
            put(5, makeKeyAcl(false, 6, 8));
        }};
        setEncryptionKeyAcls(initialAcls);
        setEncryptionKeys(123);

        post(vertx, testContext, "api/keys_acl/update?site_id=5&remove=7", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkEncryptionKeyAclsResponse(expectedAcl, new Object[]{response.bodyAsJsonObject()});

            try {
                verify(keyManager, times(0)).addSiteKey(anyInt());
                verify(keyAclStoreWriter).upload(mapOfSize(1), isNull());
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
    }

    @Test
    void keyAclUpdateAddRemoveMultiple(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites(
                new Site(5, "test_site", true),
                new Site(11, "test_site11", true),
                new Site(12, "test_site12", false));
        Map<Integer, EncryptionKeyAcl> initialAcls = new HashMap<Integer, EncryptionKeyAcl>() {{
            put(5, makeKeyAcl(false, 6, 7, 8));
        }};
        Map<Integer, EncryptionKeyAcl> expectedAcl = new HashMap<Integer, EncryptionKeyAcl>() {{
            put(5, makeKeyAcl(false, 6, 11, 12));
        }};
        setEncryptionKeyAcls(initialAcls);
        setEncryptionKeys(123);

        post(vertx, testContext, "api/keys_acl/update?site_id=5&add=11,12&remove=7,8", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkEncryptionKeyAclsResponse(expectedAcl, new Object[]{response.bodyAsJsonObject()});

            try {
                verify(keyManager).addSiteKey(eq(5));
                verify(keyAclStoreWriter).upload(mapOfSize(1), isNull());
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
    }

    @Test
    void keyAclUpdateSiteWithoutAcl(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites(
                new Site(5, "test_site", true),
                new Site(11, "test_site11", true));
        post(vertx, "api/keys_acl/update?site_id=5&add=11", "", expectHttpError(testContext, 404));
    }

    @Test
    void keyAclUpdateAddSpecialSite1(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites(new Site(5, "test_site", true));
        Map<Integer, EncryptionKeyAcl> initialAcls = new HashMap<Integer, EncryptionKeyAcl>() {{
            put(5, makeKeyAcl(false, 6, 7, 8));
        }};
        setEncryptionKeyAcls(initialAcls);
        post(vertx, "api/keys_acl/update?site_id=5&add=1", "", expectHttpError(testContext, 400));
    }

    @Test
    void keyAclUpdateAddSpecialSite2(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites(new Site(5, "test_site", true));
        Map<Integer, EncryptionKeyAcl> initialAcls = new HashMap<Integer, EncryptionKeyAcl>() {{
            put(5, makeKeyAcl(false, 6, 7, 8));
        }};
        setEncryptionKeyAcls(initialAcls);
        post(vertx, "api/keys_acl/update?site_id=5&add=2", "", expectHttpError(testContext, 400));
    }

    @Test
    void keyAclUpdateAddUnknownSite(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites(new Site(5, "test_site", true));
        Map<Integer, EncryptionKeyAcl> initialAcls = new HashMap<Integer, EncryptionKeyAcl>() {{
            put(5, makeKeyAcl(false, 6, 7, 8));
        }};
        setEncryptionKeyAcls(initialAcls);
        post(vertx, "api/keys_acl/update?site_id=5&add=11", "", expectHttpError(testContext, 404));
    }

    @Test
    void keyAclUpdateAddAlreadyListedSite(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites(
                new Site(5, "test_site", true),
                new Site(7, "test_site7", true));
        Map<Integer, EncryptionKeyAcl> initialAcls = new HashMap<Integer, EncryptionKeyAcl>() {{
            put(5, makeKeyAcl(true, 6, 7, 8));
        }};
        Map<Integer, EncryptionKeyAcl> expectedAcl = new HashMap<Integer, EncryptionKeyAcl>() {{
            put(5, makeKeyAcl(true, 6, 7, 8));
        }};
        setEncryptionKeyAcls(initialAcls);
        setEncryptionKeys(123);

        post(vertx, testContext, "api/keys_acl/update?site_id=5&add=7", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkEncryptionKeyAclsResponse(expectedAcl, new Object[]{response.bodyAsJsonObject()});

            try {
                verify(keyManager, times(0)).addSiteKey(anyInt());
                verify(keyAclStoreWriter, times(0)).upload(any(), isNull());
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
    }

    @Test
    void keyAclUpdateWhitelistOwnerSite(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites(new Site(5, "test_site", true));
        Map<Integer, EncryptionKeyAcl> initialAcls = new HashMap<Integer, EncryptionKeyAcl>() {{
            put(5, makeKeyAcl(true, 6, 7, 8));
        }};
        Map<Integer, EncryptionKeyAcl> expectedAcl = new HashMap<Integer, EncryptionKeyAcl>() {{
            put(5, makeKeyAcl(true, 6, 7, 8));
        }};
        setEncryptionKeyAcls(initialAcls);
        setEncryptionKeys(123);

        post(vertx, testContext, "api/keys_acl/update?site_id=5&add=5", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkEncryptionKeyAclsResponse(expectedAcl, new Object[]{response.bodyAsJsonObject()});

            try {
                verify(keyManager, times(0)).addSiteKey(anyInt());
                verify(keyAclStoreWriter, times(0)).upload(any(), isNull());
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
    }

    @Test
    void keyAclUpdateBlacklistOwnerSite(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites(new Site(5, "test_site", true));
        Map<Integer, EncryptionKeyAcl> initialAcls = new HashMap<Integer, EncryptionKeyAcl>() {{
            put(5, makeKeyAcl(false, 6, 7, 8));
        }};
        Map<Integer, EncryptionKeyAcl> expectedAcl = new HashMap<Integer, EncryptionKeyAcl>() {{
            put(5, makeKeyAcl(false, 6, 7, 8));
        }};
        setEncryptionKeyAcls(initialAcls);
        setEncryptionKeys(123);

        post(vertx, testContext, "api/keys_acl/update?site_id=5&add=5", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkEncryptionKeyAclsResponse(expectedAcl, new Object[]{response.bodyAsJsonObject()});

            try {
                verify(keyManager, times(0)).addSiteKey(anyInt());
                verify(keyAclStoreWriter, times(0)).upload(any(), isNull());
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
    }

    @Test
    void keyAclUpdateRemoveUnlistedSite(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites(new Site(5, "test_site", true));
        Map<Integer, EncryptionKeyAcl> initialAcls = new HashMap<Integer, EncryptionKeyAcl>() {{
            put(5, makeKeyAcl(true, 6, 7, 8));
        }};
        Map<Integer, EncryptionKeyAcl> expectedAcl = new HashMap<Integer, EncryptionKeyAcl>() {{
            put(5, makeKeyAcl(true, 6, 7, 8));
        }};
        setEncryptionKeyAcls(initialAcls);
        setEncryptionKeys(123);

        post(vertx, testContext, "api/keys_acl/update?site_id=5&remove=1,5,9,11", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkEncryptionKeyAclsResponse(expectedAcl, new Object[]{response.bodyAsJsonObject()});

            try {
                verify(keyManager, times(0)).addSiteKey(anyInt());
                verify(keyAclStoreWriter, times(0)).upload(any(), isNull());
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
    }
}
