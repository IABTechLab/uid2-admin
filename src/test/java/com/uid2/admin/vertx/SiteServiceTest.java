package com.uid2.admin.vertx;

import com.uid2.admin.legacy.LegacyClientKey;
import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.service.SiteService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.Site;
import com.uid2.shared.model.ClientType;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class SiteServiceTest extends ServiceTestBase {
    @Override
    protected IService createService() {
        return new SiteService(auth, writeLock, storeWriter, siteProvider, clientKeyProvider);
    }

    private void checkSiteResponse(Site[] expectedSites, Object[] actualSites) {
        assertAll(
                "checkSiteResponse",
                () -> assertEquals(expectedSites.length, actualSites.length),
                () -> {
                    for (int i = 0; i < expectedSites.length; ++i) {
                        checkSiteJson(expectedSites[i], (JsonObject) actualSites[i]);
                    }});
    }

    private void checkSiteJson(Site expectedSite, JsonObject actualSite) {
        assertAll(
                "checkSiteJson",
                () -> assertEquals(expectedSite.getId(), actualSite.getInteger("id")),
                () -> assertEquals(expectedSite.getName(), actualSite.getString("name")),
                () -> assertEquals(expectedSite.isEnabled(), actualSite.getBoolean("enabled")),
                () -> assertEquals(expectedSite.getCreated(), actualSite.getLong("created")));
    }

    private void checkSiteResponse(Site expectedSite, JsonObject actualSite){
        assertEquals(expectedSite.getId(), actualSite.getInteger("id"));
        assertEquals(expectedSite.getName(), actualSite.getString("name"));
        assertEquals(expectedSite.isEnabled(), actualSite.getBoolean("enabled"));
        assertEquals(expectedSite.getDomainNames(), actualSite.getJsonArray("domain_names").stream().collect(Collectors.toSet()));
        assertEquals(expectedSite.getCreated(), actualSite.getLong("created"));
    }

    private void checkSiteResponseWithKeys(Object[] actualSites, int siteId, int nkeys, Role... roles) {
        JsonObject site = Arrays.stream(actualSites)
                .map(s -> (JsonObject) s)
                .filter(s -> s.getInteger("id") == siteId)
                .findFirst().get();
        List<Role> actualRoles = site.getJsonArray("roles").stream()
                .map(r -> Role.valueOf((String) r))
                .collect(Collectors.toList());

        assertAll(
                "checkSiteResponseWithKeys",
                () -> assertEquals(nkeys, site.getInteger("client_count")),
                () -> assertEquals(roles.length, actualRoles.size()),
                () -> assertTrue(actualRoles.containsAll(List.of(roles))));
    }

    @Test
    void listSitesNoSites(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);

        get(vertx, "api/site/list", ar -> {
            HttpResponse<Buffer> response = ar.result();
            assertAll(
                    "listSitesNoSites",
                    () -> assertTrue(ar.succeeded()),
                    () -> assertEquals(200, response.statusCode()),
                    () -> assertEquals(0, response.bodyAsJsonArray().stream().count()));

            testContext.completeNow();
        });
    }

    @Test
    void listSitesHaveSites(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);

        Site[] sites = {
                new Site(11, "site1", false),
                new Site(12, "site2", true),
                new Site(13, "site3", false, Set.of("test1.com", "test2.net")),
        };
        setSites(sites);

        get(vertx, "api/site/list", ar -> {
            HttpResponse<Buffer> response = ar.result();
            assertAll(
                    "listSitesHaveSites",
                    () -> assertTrue(ar.succeeded()),
                    () -> assertEquals(200, response.statusCode()),
                    () -> checkSiteResponse(sites, response.bodyAsJsonArray().stream().toArray()));

            testContext.completeNow();
        });
    }

    @Test
    void listSitesWithKeys(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);

        Site[] sites = {
                new Site(11, "site1", false),
                new Site(12, "site2", true),
                new Site(13, "site3", false, Set.of("test1.com", "test2.net")),
                new Site(14, "site3", false),
        };
        setSites(sites);

        LegacyClientKey[] clientKeys = {
                new LegacyClientKey("ck1", "ckh1", "cks1", "cs1", "c1", Instant.MIN, Set.of(Role.GENERATOR, Role.ID_READER), 11),
                new LegacyClientKey("ck2", "ckh2", "cks2", "cs2", "c2", Instant.MIN, Set.of(Role.MAPPER), 12),
                new LegacyClientKey("ck3", "ckh3", "cks3", "cs3", "c3", Instant.MIN, Set.of(Role.GENERATOR, Role.MAPPER), 11),
                new LegacyClientKey("ck4", "ckh4", "cks4", "cs4", "c4", Instant.MIN, Set.of(Role.SHARER), 13),
        };
        setClientKeys(clientKeys);

        get(vertx, "api/site/list", ar -> {
            HttpResponse<Buffer> response = ar.result();
            assertAll(
                    "listSitesWithKeys",
                    () -> assertTrue(ar.succeeded()),
                    () -> assertEquals(200, response.statusCode()),
                    () -> checkSiteResponse(sites, response.bodyAsJsonArray().stream().toArray()),
                    () -> checkSiteResponseWithKeys(response.bodyAsJsonArray().stream().toArray(), 11, 2, Role.GENERATOR, Role.ID_READER, Role.MAPPER),
                    () -> checkSiteResponseWithKeys(response.bodyAsJsonArray().stream().toArray(), 12, 1, Role.MAPPER),
                    () -> checkSiteResponseWithKeys(response.bodyAsJsonArray().stream().toArray(), 13, 1, Role.SHARER),
                    () -> checkSiteResponseWithKeys(response.bodyAsJsonArray().stream().toArray(), 14, 0));

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
            HttpResponse<Buffer> response = ar.result();
            assertAll(
                    "addSiteNoExistingSites",
                    () -> assertTrue(ar.succeeded()),
                    () -> assertEquals(200, response.statusCode()),
                    () -> checkSiteResponse(addedSites, new Object[]{response.bodyAsJsonObject()}));

            try {
                verify(storeWriter).upload(collectionOfSize(initialSites.length + 1), isNull());
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
            HttpResponse<Buffer> response = ar.result();
            assertAll(
                    "addSiteExistingSites",
                    () -> assertTrue(ar.succeeded()),
                    () -> assertEquals(200, response.statusCode()),
                    () -> checkSiteResponse(addedSites, new Object[]{response.bodyAsJsonObject()}));

            try {
                verify(storeWriter).upload(collectionOfSize(initialSites.length + 1), isNull());
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
    }

    @Test
    void addSiteWithTypes(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);

        Site[] initialSites = {
                new Site(7, "initial_site", false),
        };
        Site[] addedSites = {
                new Site(8, "test_site", false, Set.of(ClientType.DSP, ClientType.ADVERTISER), new HashSet<>()),
        };

        setSites(initialSites);

        post(vertx, "api/site/add?name=test_site&types=DSP,ADVERTISER", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkSiteResponse(addedSites, new Object[]{response.bodyAsJsonObject()});

            try {
                verify(storeWriter).upload(collectionOfSize(initialSites.length + 1), isNull());
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
            HttpResponse<Buffer> response = ar.result();
            assertAll(
                    "addSiteEnabled",
                    () -> assertTrue(ar.succeeded()),
                    () -> assertEquals(200, response.statusCode()),
                    () -> checkSiteResponse(addedSites, new Object[]{response.bodyAsJsonObject()}));

            try {
                verify(storeWriter).upload(collectionOfSize(initialSites.length + 1), isNull());
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
            HttpResponse<Buffer> response = ar.result();
            assertAll(
                    "enableSite",
                    () -> assertTrue(ar.succeeded()),
                    () -> assertEquals(200, response.statusCode()),
                    () -> checkSiteResponse(updatedSites, new Object[]{response.bodyAsJsonObject()}));

            try {
                verify(storeWriter).upload(collectionOfSize(initialSites.length), isNull());
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
            HttpResponse<Buffer> response = ar.result();
            assertAll(
                    "disableSite",
                    () -> assertTrue(ar.succeeded()),
                    () -> assertEquals(200, response.statusCode()),
                    () -> checkSiteResponse(updatedSites, new Object[]{response.bodyAsJsonObject()}));

            try {
                verify(storeWriter).upload(collectionOfSize(initialSites.length), isNull());
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
            HttpResponse<Buffer> response = ar.result();
            assertAll(
                    "enableSiteAlreadyEnabled",
                    () -> assertTrue(ar.succeeded()),
                    () -> assertEquals(200, response.statusCode()),
                    () -> checkSiteResponse(updatedSites, new Object[]{response.bodyAsJsonObject()}));

            try {
                verify(storeWriter, times(0)).upload(any(), isNull());
            } catch (Exception ex) {
                fail(ex);
            }

            testContext.completeNow();
        });
    }

    @Test
    void setTypes(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);

        Site[] initialSites = {
                new Site(3, "test_site", true),
        };
        Site[] updatedSites = {
                new Site(3, "test_site", true, Set.of(ClientType.DSP, ClientType.ADVERTISER), new HashSet<>()),
        };

        setSites(initialSites);

        post(vertx, "api/site/set-types?id=3&types=DSP,ADVERTISER", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkSiteResponse(updatedSites, new Object[]{response.bodyAsJsonObject()});

            try {
                verify(storeWriter).upload(collectionOfSize(initialSites.length), isNull());
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

    @Test
    void domainNameNoSiteId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites();
        post(vertx, "api/site/domain_names", "", ar -> {
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            assertEquals("must specify site id", response.bodyAsJsonObject().getString("message"));
            testContext.completeNow();
        });
    }

    @Test
    void domainNameMissingSiteId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites();
        post(vertx, "api/site/domain_names?id=123", "", ar -> {
            HttpResponse response = ar.result();
            assertEquals(404, response.statusCode());
            assertEquals("site not found", response.bodyAsJsonObject().getString("message"));
            testContext.completeNow();
        });
    }

    @Test
    void domainNameInvalidSiteId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites();
        post(vertx, "api/site/domain_names?id=2", "", ar -> {
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            assertEquals("must specify a valid site id", response.bodyAsJsonObject().getString("message"));
            testContext.completeNow();
        });
    }

    @Test
    void domainNameBadSiteId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites();
        post(vertx, "api/site/domain_names?id=asdf", "", ar -> {
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            assertEquals("unable to parse site id For input string: \"asdf\"", response.bodyAsJsonObject().getString("message"));
            testContext.completeNow();
        });
    }

    @Test
    void domainNameNoDomainNames(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        setSites(new Site(123, "name", true));
        JsonObject reqBody = new JsonObject();
        post(vertx, "api/site/domain_names?id=123", reqBody.encode(), ar -> {
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            assertEquals("required parameters: domain_names", response.bodyAsJsonObject().getString("message"));
            testContext.completeNow();
        });
    }

    @Test
    void domainNameEmptyNames(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        Site s = new Site(123, "name", true);
        setSites(s);
        JsonObject reqBody = new JsonObject();
        reqBody.put("domain_names", new JsonArray());
        post(vertx, "api/site/domain_names?id=123", reqBody.encode(), ar -> {
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkSiteResponse(s, response.bodyAsJsonObject());
            testContext.completeNow();
        });
    }

    @Test
    void domainNameInvalidDomainName(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        Site s = new Site(123, "name", true);
        setSites(s);

        JsonObject reqBody = new JsonObject();
        JsonArray names = new JsonArray();
        names.add("bad");
        reqBody.put("domain_names", names);

        post(vertx, "api/site/domain_names?id=123", reqBody.encode(), ar -> {
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            assertEquals("invalid domain name: bad", response.bodyAsJsonObject().getString("message"));
            testContext.completeNow();
        });
    }

    @Test
    void domainNameInvalidTld(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        Site s = new Site(123, "name", true);
        setSites(s);

        JsonObject reqBody = new JsonObject();
        JsonArray names = new JsonArray();
        names.add("bad.doesntexist");
        reqBody.put("domain_names", names);

        post(vertx, "api/site/domain_names?id=123", reqBody.encode(), ar -> {
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            assertEquals("invalid domain name: bad.doesntexist", response.bodyAsJsonObject().getString("message"));
            testContext.completeNow();
        });
    }

    @Test
    void domainNameDuplicateDomainName(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        Site s = new Site(123, "name", true);
        setSites(s);

        JsonObject reqBody = new JsonObject();
        JsonArray names = new JsonArray();
        names.add("bad.com");
        names.add("http://bad.com");
        reqBody.put("domain_names", names);

        post(vertx, "api/site/domain_names?id=123", reqBody.encode(), ar -> {
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            assertEquals("duplicate domain_names not permitted", response.bodyAsJsonObject().getString("message"));
            testContext.completeNow();
        });
    }

    @Test
    void domainNameMultipleDomainName(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        Site s = new Site(123, "name", true);
        setSites(s);

        JsonObject reqBody = new JsonObject();
        JsonArray names = new JsonArray();
        names.add("test.com");
        names.add("http://test.net");
        names.add("https://test.org");
        names.add("https://something.something.test2.org/asdf/asdf");
        names.add("blah.test3.com");
        reqBody.put("domain_names", names);

        post(vertx, "api/site/domain_names?id=123", reqBody.encode(), ar -> {
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            s.setDomainNames(Set.of("test.com", "test.net", "test.org", "test2.org", "test3.com"));
            checkSiteResponse(s, response.bodyAsJsonObject());
            testContext.completeNow();
        });
    }

    @Test
    void domainNameOverWrite(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        Site s = new Site(123, "name", true, Set.of("qwerty.com"));
        setSites(s);

        JsonObject reqBody = new JsonObject();
        JsonArray names = new JsonArray();
        names.add("test.com");
        names.add("http://test.net");
        names.add("https://test.org");
        names.add("https://something.something.test2.org/asdf/asdf");
        names.add("blah.test3.com");
        reqBody.put("domain_names", names);

        post(vertx, "api/site/domain_names?id=123", reqBody.encode(), ar -> {
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            s.setDomainNames(Set.of("test.com", "test.net", "test.org", "test2.org", "test3.com"));
            checkSiteResponse(s, response.bodyAsJsonObject());
            testContext.completeNow();
        });
    }

    @Test
    void createdOverWrite(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);
        Site s = new Site(123, "name", true, Set.of("qwerty.com"));
        setSites(s);

        JsonObject reqBody = new JsonObject();
        reqBody.put("created", 123456);

        post(vertx, "api/site/created?id=123", reqBody.encode(), ar -> {
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            Site updatedSite = new Site(123, "name", true, Set.of(), Set.of("qwerty.com"), 123456);
            checkSiteResponse(updatedSite, response.bodyAsJsonObject());
            try {
                verify(storeWriter, times(1)).upload(List.of(updatedSite), null);
            } catch (Exception e) {
                testContext.failed();
            }
            testContext.completeNow();
        });
    }

    @Test
    void addSiteWithDomainNames(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);

        setSites(new Site(123, "name", true, Set.of("qwerty.com")));

        JsonObject reqBody = new JsonObject();
        JsonArray names = new JsonArray();
        names.add("test.com");
        names.add("http://test.net");
        names.add("https://test.org");
        names.add("https://something.something.test2.org/asdf/asdf");
        names.add("blah.test3.com");
        reqBody.put("domain_names", names);

        post(vertx, "api/site/add?name=test_name&enabled=true", reqBody.encode(), ar -> {
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            Site expected = new Site(124, "test_name", true, Set.of("test.com", "test.net", "test.org", "test2.org", "test3.com"));
            checkSiteResponse(expected, response.bodyAsJsonObject());
            testContext.completeNow();
        });
    }

    @Test
    void addSiteWithBadDomainNames(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);

        setSites(new Site(123, "name", true, Set.of("qwerty.com")));

        JsonObject reqBody = new JsonObject();
        JsonArray names = new JsonArray();
        names.add("test.com");
        names.add("bad");
        names.add("https://test.org");
        names.add("https://something.something.test2.org/asdf/asdf");
        names.add("blah.test3.com");
        reqBody.put("domain_names", names);

        post(vertx, "api/site/add?name=test_name&enabled=true", reqBody.encode(), ar -> {
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            assertEquals("invalid domain name: bad", response.bodyAsJsonObject().getString("message"));
            assertEquals("invalid domain name: bad", response.bodyAsJsonObject().getString("message"));
            testContext.completeNow();
        });
    }

    @Test
    void addSiteWithDuplicateDomainNames(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.CLIENTKEY_ISSUER);

        JsonObject reqBody = new JsonObject();
        JsonArray names = new JsonArray();
        names.add("test.com");
        names.add("http://test.com");
        names.add("https://test.org");
        names.add("https://something.something.test2.org/asdf/asdf");
        names.add("blah.test3.com");
        reqBody.put("domain_names", names);

        post(vertx, "api/site/add?name=test_name&enabled=true", reqBody.encode(), ar -> {
            HttpResponse response = ar.result();
            assertEquals(400, response.statusCode());
            assertEquals("duplicate domain_names not permitted", response.bodyAsJsonObject().getString("message"));
            testContext.completeNow();
        });
    }


}