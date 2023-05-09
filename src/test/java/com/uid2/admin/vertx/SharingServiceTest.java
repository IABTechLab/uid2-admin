package com.uid2.admin.vertx;

import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.service.SharingService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.auth.EncryptionKeyAcl;
import com.uid2.shared.auth.Role;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static javax.swing.UIManager.put;
import static org.junit.jupiter.api.Assertions.*;

public class SharingServiceTest extends ServiceTestBase {
    @Override
    protected IService createService() {
        return new SharingService(auth, writeLock, keyAclStoreWriter, keyAclProvider, siteProvider);
    }

    private void compareAclToResult(EncryptionKeyAcl expectedAcl, JsonArray actualAcl) {
        assertNotNull(actualAcl);
        Set<Integer> actualSet = actualAcl.stream()
                .map(s -> (Integer) s)
                .collect(Collectors.toSet());
        assertEquals(expectedAcl.getAccessList(), actualSet);
    }

    @Test
    void listSiteGet(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SHARING_PORTAL);

        Map<Integer, EncryptionKeyAcl> acls = new HashMap<Integer, EncryptionKeyAcl>() {{
            put(5, makeKeyAcl(true, 4, 6, 7));
            put(7, makeKeyAcl(true));
            put(4, makeKeyAcl(true, 5));
        }};

        setEncryptionKeyAcls(acls);
        get(vertx, "api/sharing/list/5/get", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            compareAclToResult(acls.get(5), response.bodyAsJsonObject().getJsonArray("whitelist"));

            Integer expectedHash = Objects.hash(acls.get(5).getAccessList());
            assertEquals(expectedHash, response.bodyAsJsonObject().getInteger("whitelist_hash"));

            testContext.completeNow();
        });
    }

    @Test
    void listSiteGetNotFound(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SHARING_PORTAL);

        Map<Integer, EncryptionKeyAcl> acls = new HashMap<Integer, EncryptionKeyAcl>() {{
            put(5, makeKeyAcl(true, 4, 6, 7));
            put(7, makeKeyAcl(true));
            put(4, makeKeyAcl(true, 5));
        }};

        setEncryptionKeyAcls(acls);
        get(vertx, "api/sharing/list/42/get", ar -> {
            HttpResponse response = ar.result();
            assertEquals(404, response.statusCode());

            testContext.completeNow();
        });
    }

    @Test
    void listSiteSet(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SHARING_PORTAL);

        Map<Integer, EncryptionKeyAcl> acls = new HashMap<Integer, EncryptionKeyAcl>() {{
            put(5, makeKeyAcl(true, 4, 6, 7));
            put(7, makeKeyAcl(true));
            put(4, makeKeyAcl(true, 5));
        }};

        setEncryptionKeyAcls(acls);

        String body = "  {\n" +
                "    \"whitelist\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"whitelist_hash\": 48\n" +
                "  }";

        post(vertx, "api/sharing/list/5/set", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            EncryptionKeyAcl expected = makeKeyAcl(true, 22, 25, 6);
            compareAclToResult(expected, response.bodyAsJsonObject().getJsonArray("whitelist"));

            assertEquals(expected.getAccessList(), acls.get(5).getAccessList());
            testContext.completeNow();
        });
    }

    @Test
    void listSiteSetNew(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SHARING_PORTAL);

        Map<Integer, EncryptionKeyAcl> acls = new HashMap<Integer, EncryptionKeyAcl>() {{
            put(5, makeKeyAcl(true, 4, 6, 7));
            put(7, makeKeyAcl(true));
            put(4, makeKeyAcl(true, 5));
        }};

        setEncryptionKeyAcls(acls);

        String body = "  {\n" +
                "    \"whitelist\": [\n" +
                "      22,\n" +
                "      25,\n" +
                "      6\n" +
                "    ],\n" +
                "    \"whitelist_hash\": 0\n" +
                "  }";

        post(vertx, "api/sharing/list/8/set", body, ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            EncryptionKeyAcl expected = makeKeyAcl(true, 22, 25, 6);
            compareAclToResult(expected, response.bodyAsJsonObject().getJsonArray("whitelist"));

            assertEquals(expected.getAccessList(), acls.get(8).getAccessList());
            testContext.completeNow();
        });
    }
}
