// Copyright (c) 2021 The Trade Desk, Inc
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice,
//    this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice,
//    this list of conditions and the following disclaimer in the documentation
//    and/or other materials provided with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
// POSSIBILITY OF SUCH DAMAGE.

package com.uid2.admin.vertx;

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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ClientKeyServiceTest extends ServiceTestBase {
    @Override
    protected IService createService() {
        return new ClientKeyService(config, auth, writeLock, storageManager, clientKeyProvider, siteProvider, keyGenerator);
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
    void clientAdd(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites(new Site(5, "test_site", true));
        ClientKey[] expectedClients = {
                new ClientKey("", "", "test_client").withRoles(Role.GENERATOR).withSiteId(5)
        };

        post(vertx, "api/client/add?name=test_client&roles=generator&site_id=5", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkClientKeyResponse(expectedClients, new Object[]{response.bodyAsJsonObject()});

            try {
                verify(storageManager).uploadClientKeys(any(), collectionOfSize(1));
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
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
        setSites(new Site(5, "test_site", true));
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
                verify(storageManager).uploadClientKeys(any(), collectionOfSize(1));
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
    }

    @Test
    void clientUpdateUnknownClientName(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites(new Site(5, "test_site", true));
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
}
