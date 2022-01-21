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
import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.service.SiteService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.auth.ClientKey;
import com.uid2.shared.auth.Role;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class SiteServiceTest extends ServiceTestBase {
    @Override
    protected IService createService() {
        return new SiteService(auth, writeLock, storageManager, siteProvider, clientKeyProvider);
    }

    private void checkSitesResponse(Site[] expectedSites, Object[] actualSites) {
        assertEquals(expectedSites.length, actualSites.length);
        for (int i = 0; i < expectedSites.length; ++i) {
            Site expectedSite = expectedSites[i];
            JsonObject actualSite = (JsonObject) actualSites[i];
            assertEquals(expectedSite.getId(), actualSite.getInteger("id"));
            assertEquals(expectedSite.getName(), actualSite.getString("name"));
            assertEquals(expectedSite.isEnabled(), actualSite.getBoolean("enabled"));
        }
    }

    private void checkSiteResponseWithKeys(Object[] actualSites, int siteId, int nkeys, Role... roles) {
        JsonObject site = Arrays.stream(actualSites)
                .map(s -> (JsonObject) s)
                .filter(s -> s.getInteger("id") == siteId)
                .findFirst().get();
        assertEquals(nkeys, site.getInteger("client_count"));
        List<Role> actualRoles = site.getJsonArray("roles").stream()
                .map(r -> Role.valueOf((String) r))
                .collect(Collectors.toList());
        assertEquals(roles.length, actualRoles.size());
        for (Role role : roles) {
            assertTrue(actualRoles.contains(role));
        }
    }

    @Test
    void listSitesNoSites(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);

        get(vertx, "api/site/list", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            assertEquals(0, response.bodyAsJsonArray().size());
            testContext.completeNow();
        });
    }

    @Test
    void listSitesHaveSites(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);

        Site[] sites = {
                new Site(11, "site1", false),
                new Site(12, "site2", true),
                new Site(13, "site3", false),
        };
        setSites(sites);

        get(vertx, "api/site/list", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkSitesResponse(sites, response.bodyAsJsonArray().stream().toArray());
            testContext.completeNow();
        });
    }

    @Test
    void listSitesWithKeys(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);

        Site[] sites = {
                new Site(11, "site1", false),
                new Site(12, "site2", true),
                new Site(13, "site3", false),
        };
        setSites(sites);

        ClientKey[] clientKeys = {
                new ClientKey("ck1").withSiteId(11).withRoles(Role.GENERATOR, Role.ID_READER),
                new ClientKey("ck2").withSiteId(12).withRoles(Role.MAPPER),
                new ClientKey("ck3").withSiteId(11).withRoles(Role.GENERATOR, Role.MAPPER),
        };
        setClientKeys(clientKeys);

        get(vertx, "api/site/list", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkSitesResponse(sites, response.bodyAsJsonArray().stream().toArray());
            checkSiteResponseWithKeys(response.bodyAsJsonArray().stream().toArray(), 11, 2, Role.GENERATOR, Role.ID_READER, Role.MAPPER);
            checkSiteResponseWithKeys(response.bodyAsJsonArray().stream().toArray(), 12, 1, Role.MAPPER);
            checkSiteResponseWithKeys(response.bodyAsJsonArray().stream().toArray(), 13, 0);
            testContext.completeNow();
        });
    }

    @Test
    void addSiteNoExistingSites(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);

        Site[] initialSites = {
        };
        Site[] addedSites = {
                new Site(3, "test_site", false),
        };

        setSites(initialSites);

        post(vertx, "api/site/add?name=test_site", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkSitesResponse(addedSites, new Object[]{response.bodyAsJsonObject()});

            try {
                verify(storageManager).uploadSites(any(), collectionOfSize(initialSites.length + 1));
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
    }

    @Test
    void addSiteExistingSites(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);

        Site[] initialSites = {
                new Site(7, "initial_site", false),
        };
        Site[] addedSites = {
                new Site(8, "test_site", false),
        };

        setSites(initialSites);

        post(vertx, "api/site/add?name=test_site", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkSitesResponse(addedSites, new Object[]{response.bodyAsJsonObject()});

            try {
                verify(storageManager).uploadSites(any(), collectionOfSize(initialSites.length + 1));
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
    }

    @Test
    void addSiteEnabled(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);

        Site[] initialSites = {
        };
        Site[] addedSites = {
                new Site(3, "test_site", true),
        };

        setSites(initialSites);

        post(vertx, "api/site/add?name=test_site&enabled=true", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkSitesResponse(addedSites, new Object[]{response.bodyAsJsonObject()});

            try {
                verify(storageManager).uploadSites(any(), collectionOfSize(initialSites.length + 1));
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
    }

    @Test
    void addSiteExistingName(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites(new Site(3, "test_site", false));
        post(vertx, "api/site/add?name=test_site", "", expectHttpError(testContext, 400));
    }

    @Test
    void addSiteEmptyName(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites(new Site(3, "test_site", false));
        post(vertx, "api/site/add?name=", "", expectHttpError(testContext, 400));
    }

    @Test
    void addSiteWhitespaceName(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites(new Site(3, "test_site", false));
        post(vertx, "api/site/add?name=%20", "", expectHttpError(testContext, 400));
    }

    @Test
    void enableSite(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);

        Site[] initialSites = {
                new Site(3, "test_site", false),
        };
        Site[] updatedSites = {
                new Site(3, "test_site", true),
        };

        setSites(initialSites);

        post(vertx, "api/site/enable?id=3&enabled=true", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkSitesResponse(updatedSites, new Object[]{response.bodyAsJsonObject()});

            try {
                verify(storageManager).uploadSites(any(), collectionOfSize(initialSites.length));
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
    }

    @Test
    void disableSite(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);

        Site[] initialSites = {
                new Site(3, "test_site", true),
        };
        Site[] updatedSites = {
                new Site(3, "test_site", false),
        };

        setSites(initialSites);

        post(vertx, "api/site/enable?id=3&enabled=false", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkSitesResponse(updatedSites, new Object[]{response.bodyAsJsonObject()});

            try {
                verify(storageManager).uploadSites(any(), collectionOfSize(initialSites.length));
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
    }

    @Test
    void enableSiteAlreadyEnabled(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);

        Site[] initialSites = {
                new Site(3, "test_site", true),
        };
        Site[] updatedSites = {
                new Site(3, "test_site", true),
        };

        setSites(initialSites);

        post(vertx, "api/site/enable?id=3&enabled=true", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkSitesResponse(updatedSites, new Object[]{response.bodyAsJsonObject()});

            try {
                verify(storageManager, times(0)).uploadSites(any(), any());
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
    }

    @Test
    void enableSiteUnknownSite(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites(new Site(3, "test_site", false));
        post(vertx, "api/site/enable?id=5&enabled=true", "", expectHttpError(testContext, 404));
    }

    @Test
    void enableSiteSpecialSite1(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites();
        post(vertx, "api/site/enable?id=1&enabled=true", "", expectHttpError(testContext, 400));
    }

    @Test
    void enableSiteSpecialSite2(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites();
        post(vertx, "api/site/enable?id=2&enabled=true", "", expectHttpError(testContext, 400));
    }
}
