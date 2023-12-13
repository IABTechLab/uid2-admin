package com.uid2.admin.vertx;

import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.service.ServiceLinkService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.Service;
import com.uid2.shared.model.ServiceLink;
import com.uid2.shared.model.Site;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ServiceLinkServiceTest extends ServiceTestBase {

    @Override
    protected IService createService() {
        return new ServiceLinkService(auth, writeLock, serviceLinkStoreWriter, serviceLinkProvider, serviceProvider, siteProvider);
    }

    private void checkServiceLinkResponse(ServiceLink[] expectedServiceLinks, JsonArray actualServiceLinks) {
        assertAll(
                "checkServiceLinkResponse",
                () -> assertEquals(expectedServiceLinks.length, actualServiceLinks.size()),
                () -> {
                    for (int i = 0; i < expectedServiceLinks.length; i++) {
                        checkServiceLinkJson(expectedServiceLinks[i], actualServiceLinks.getJsonObject(i));
                    }
                });
    }

    private void checkServiceLinkJson(ServiceLink expectedServiceLink, JsonObject actualServiceLink) {
        assertAll(
                "checkServiceLinkJson",
                () -> assertEquals(expectedServiceLink.getLinkId(), actualServiceLink.getString("link_id")),
                () -> assertEquals(expectedServiceLink.getServiceId(), actualServiceLink.getInteger("service_id")),
                () -> assertEquals(expectedServiceLink.getSiteId(), actualServiceLink.getInteger("site_id")),
                () -> assertEquals(expectedServiceLink.getName(), actualServiceLink.getString("name"))
        );
    }

    @Test
    void listLinksNoLinks(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        get(vertx, testContext, "api/service_link/list", response -> {
            assertEquals(200, response.statusCode());
            JsonArray respArray = response.bodyAsJsonArray();
            assertEquals(0, respArray.size());
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void listLinksMultipleLinks(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        ServiceLink[] expectedServiceLinks = {
                new ServiceLink("link1", 1, 123, "name1", null),
                new ServiceLink("link2", 1, 123, "name2", null),
                new ServiceLink("link3", 2, 124, "name3", null),
                new ServiceLink("link4", 3, 125, "name4", null),
        };

        setServiceLinks(expectedServiceLinks);

        get(vertx, testContext, "api/service_link/list", response -> {
            assertEquals(200, response.statusCode());
            checkServiceLinkResponse(expectedServiceLinks, response.bodyAsJsonArray());
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void addLinkMissingPayload(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);
        postWithoutBody(vertx, testContext, "api/service_link/add", response -> {
                    assertEquals(400, response.statusCode());
                    assertEquals("json payload required but not provided", response.bodyAsJsonObject().getString("message"));
                    verify(serviceStoreWriter, never()).upload(null, null);
                    verify(serviceLinkStoreWriter, never()).upload(null, null);
                    testContext.completeNow();
                }
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"link_id", "service_id", "site_id", "name"})
    void addServiceLinkMissingParameters(String parameter, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        JsonObject jo = new JsonObject();
        jo.put("link_id", "link1");
        jo.put("service_id", 1);
        jo.put("site_id", 123);
        jo.put("name", "name1");

        jo.remove(parameter);

        post(vertx, testContext, "api/service_link/add", jo.encode(), response -> {
            assertEquals(400, response.statusCode());
            assertEquals("required parameters: link_id, service_id, site_id, name", response.bodyAsJsonObject().getString("message"));
            verifyNoInteractions(serviceStoreWriter);
            verify(serviceLinkStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void addServiceLinkBadServiceId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        JsonObject jo = new JsonObject();
        jo.put("link_id", "link1");
        jo.put("service_id", 1);
        jo.put("site_id", 124);
        jo.put("name", "name1");

        post(vertx, testContext, "api/service_link/add", jo.encode(), response -> {
            assertEquals(404, response.statusCode());
            assertEquals("service_id 1 not valid", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void addServiceLinkBadSiteId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setServices(new Service(1, 123, "name1", Set.of(Role.CLIENTKEY_ISSUER)));

        JsonObject jo = new JsonObject();
        jo.put("link_id", "link1");
        jo.put("service_id", 1);
        jo.put("site_id", 124);
        jo.put("name", "name1");

        post(vertx, testContext, "api/service_link/add", jo.encode(), response -> {
            assertEquals(404, response.statusCode());
            assertEquals("site_id 124 not valid", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void addDuplicateServiceLinkFails(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.CLIENTKEY_ISSUER)));
        setServiceLinks(new ServiceLink("link1", 1, 123, "name1", null));

        JsonObject jo = new JsonObject();
        jo.put("link_id", "link1");
        jo.put("service_id", 1);
        jo.put("site_id", 123);
        jo.put("name", "name1");

        post(vertx, testContext, "api/service_link/add", jo.encode(), response -> {
            assertEquals(400, response.statusCode());
            assertEquals("service link already exists", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void addServiceLinkOneRole(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.CLIENTKEY_ISSUER)));

        ServiceLink expected = new ServiceLink("link1", 1, 123, "name1", Set.of(Role.CLIENTKEY_ISSUER));

        JsonObject jo = new JsonObject();
        jo.put("link_id", "link1");
        jo.put("service_id", 1);
        jo.put("site_id", 123);
        jo.put("name", "name1");
        jo.put("roles", JsonArray.of(Role.CLIENTKEY_ISSUER));

        post(vertx, testContext, "api/service_link/add", jo.encode(), response -> {
            assertEquals(200, response.statusCode());
            checkServiceLinkJson(expected, response.bodyAsJsonObject());
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, times(1)).upload(List.of(expected), null);
            testContext.completeNow();
        });
    }

    @Test
    void addServiceLinkMissingRole(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.MAPPER, Role.SHARER)));

        ServiceLink expected = new ServiceLink("link1", 1, 123, "name1", null);

        JsonObject jo = new JsonObject();
        jo.put("link_id", "link1");
        jo.put("service_id", 1);
        jo.put("site_id", 123);
        jo.put("name", "name1");
        jo.put("roles", new JsonArray());

        post(vertx, testContext, "api/service_link/add", jo.encode(), response -> {
            assertEquals(400, response.statusCode());
            assertEquals("required parameter: roles", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void addServiceLinkEmptyRoleWithSingleAllowed(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.MAPPER)));

        ServiceLink expected = new ServiceLink("link1", 1, 123, "name1", Set.of(Role.MAPPER));

        JsonObject jo = new JsonObject();
        jo.put("link_id", "link1");
        jo.put("service_id", 1);
        jo.put("site_id", 123);
        jo.put("name", "name1");
        jo.put("roles", null);

        post(vertx, testContext, "api/service_link/add", jo.encode(), response -> {
            assertEquals(200, response.statusCode());
            checkServiceLinkJson(expected, response.bodyAsJsonObject());
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, times(1)).upload(List.of(expected), null);
            testContext.completeNow();
        });
    }

    @Test
    void addServiceLinkSubsetRoles(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.MAPPER, Role.CLIENTKEY_ISSUER, Role.SHARER, Role.OPTOUT)));

        ServiceLink expected = new ServiceLink("link1", 1, 123, "name1", Set.of(Role.MAPPER, Role.SHARER));

        JsonObject jo = new JsonObject();
        jo.put("link_id", "link1");
        jo.put("service_id", 1);
        jo.put("site_id", 123);
        jo.put("name", "name1");
        jo.put("roles", JsonArray.of(Role.MAPPER, Role.SHARER));

        post(vertx, testContext, "api/service_link/add", jo.encode(), response -> {
            assertEquals(200, response.statusCode());
            checkServiceLinkJson(expected, response.bodyAsJsonObject());
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, times(1)).upload(List.of(expected), null);
            testContext.completeNow();
        });
    }
    @Test
    void addServiceLinkIllegalRoles(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.MAPPER, Role.CLIENTKEY_ISSUER, Role.SHARER, Role.OPTOUT)));

        JsonObject jo = new JsonObject();
        jo.put("link_id", "link1");
        jo.put("service_id", 1);
        jo.put("site_id", 123);
        jo.put("name", "name1");
        jo.put("roles", JsonArray.of("IllegalRole"));

        post(vertx, testContext, "api/service_link/add", jo.encode(), response -> {
            assertEquals(400, response.statusCode());
            assertEquals("invalid parameter: roles", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void addServiceLinkEmptyRoleWithMultipleAllowed(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.MAPPER, Role.SHARER)));

        JsonObject jo = new JsonObject();
        jo.put("link_id", "link1");
        jo.put("service_id", 1);
        jo.put("site_id", 123);
        jo.put("name", "name1");
        jo.put("roles", null);

        post(vertx, testContext, "api/service_link/add", jo.encode(), response -> {
            assertEquals(400, response.statusCode());
            assertEquals("required parameter: roles", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void addServiceLinkRoleNotInSubset(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.MAPPER, Role.SHARER)));

        JsonObject jo = new JsonObject();
        jo.put("link_id", "link1");
        jo.put("service_id", 1);
        jo.put("site_id", 123);
        jo.put("name", "name1");
        jo.put("roles", JsonArray.of(Role.ADMINISTRATOR));

        post(vertx, testContext, "api/service_link/add", jo.encode(), response -> {
            assertEquals(400, response.statusCode());
            assertTrue(response.bodyAsJsonObject().getString("message").startsWith("roles allowed: "));
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void addServiceLinkToExistingList(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.CLIENTKEY_ISSUER)));
        ServiceLink existingLink = new ServiceLink("link1", 1, 123, "name1", null);
        setServiceLinks(existingLink);

        ServiceLink expected = new ServiceLink("link2", 1, 123, "name1", Set.of(Role.CLIENTKEY_ISSUER));

        JsonObject jo = new JsonObject();
        jo.put("link_id", "link2");
        jo.put("service_id", 1);
        jo.put("site_id", 123);
        jo.put("name", "name1");

        post(vertx, testContext, "api/service_link/add", jo.encode(), response -> {
            assertEquals(200, response.statusCode());
            checkServiceLinkJson(expected, response.bodyAsJsonObject());
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, times(1)).upload(List.of(existingLink, expected), null);
            testContext.completeNow();
        });
    }

    @Test
    void updateNameAndLinkIdFails(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.CLIENTKEY_ISSUER)));
        ServiceLink existingLink = new ServiceLink("link1", 1, 123, "name1", null);
        setServiceLinks(existingLink);

        JsonObject jo = new JsonObject();
        jo.put("link_id", "newLink");
        jo.put("service_id", 1);
        jo.put("site_id", 123);
        jo.put("name", "newName");

        ServiceLink expected = new ServiceLink("newLink", 1, 123, "newName", null);

        post(vertx, testContext, "api/service_link/update", jo.encode(), response -> {
            assertEquals(404, response.statusCode());
            assertEquals("failed to find a service_link for serviceId: 1, site_id: 123 and link_id: newLink", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, never()).upload(List.of(expected), null);
            testContext.completeNow();
        });
    }

    @Test
    void updateNameSucceedsNoRole(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.CLIENTKEY_ISSUER, Role.MAPPER)));
        ServiceLink existingLink = new ServiceLink("link1", 1, 123, "name1", Set.of(Role.MAPPER));
        setServiceLinks(existingLink);

        JsonObject jo = new JsonObject();
        jo.put("link_id", "link1");
        jo.put("service_id", 1);
        jo.put("site_id", 123);
        jo.put("name", "newname");

        ServiceLink expected = new ServiceLink("link1", 1, 123, "newname", Set.of(Role.MAPPER));

        post(vertx, testContext, "api/service_link/update", jo.encode(), response -> {
            assertEquals(200, response.statusCode());
            checkServiceLinkJson(expected, response.bodyAsJsonObject());
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, times(1)).upload(List.of(expected), null);

            testContext.completeNow();
        });
    }

    @Test
    void updateRoleSucceeds(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.MAPPER, Role.SHARER)));
        ServiceLink existingLink = new ServiceLink("link1", 1, 123, "name1", Set.of(Role.MAPPER));
        setServiceLinks(existingLink);

        JsonObject jo = new JsonObject();
        jo.put("link_id", "link1");
        jo.put("service_id", 1);
        jo.put("site_id", 123);
        jo.put("roles", JsonArray.of(Role.SHARER));

        ServiceLink expected = new ServiceLink("link1", 1, 123, "name1", Set.of(Role.SHARER));

        post(vertx, testContext, "api/service_link/update", jo.encode(), response -> {
            assertEquals(200, response.statusCode());
            checkServiceLinkJson(expected, response.bodyAsJsonObject());
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, times(1)).upload(List.of(expected), null);

            testContext.completeNow();
        });
    }

    @Test
    void updateRoleAndName(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.CLIENTKEY_ISSUER, Role.MAPPER, Role.SHARER)));
        ServiceLink existingLink = new ServiceLink("link1", 1, 123, "name1", Set.of(Role.MAPPER));
        setServiceLinks(existingLink);

        JsonObject jo = new JsonObject();
        jo.put("link_id", "link1");
        jo.put("service_id", 1);
        jo.put("site_id", 123);
        jo.put("name", "newname");
        jo.put("roles", JsonArray.of(Role.MAPPER, Role.SHARER));

        ServiceLink expected = new ServiceLink("link1", 1, 123, "newname", Set.of(Role.MAPPER, Role.SHARER));

        post(vertx, testContext, "api/service_link/update", jo.encode(), response -> {
            assertEquals(200, response.statusCode());
            checkServiceLinkJson(expected, response.bodyAsJsonObject());
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, times(1)).upload(List.of(expected), null);

            testContext.completeNow();
        });
    }

    @Test
    void updateIllegalRole(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.CLIENTKEY_ISSUER, Role.MAPPER, Role.SHARER)));
        ServiceLink existingLink = new ServiceLink("link1", 1, 123, "name1", Set.of(Role.MAPPER));
        setServiceLinks(existingLink);

        JsonObject jo = new JsonObject();
        jo.put("link_id", "link1");
        jo.put("service_id", 1);
        jo.put("site_id", 123);
        jo.put("name", "newname");
        jo.put("roles", JsonArray.of("IllegalRole"));

        ServiceLink expected = new ServiceLink("link1", 1, 123, "newname", null);

        post(vertx, testContext, "api/service_link/update", jo.encode(), response -> {
            assertEquals(400, response.statusCode());
            assertEquals("invalid parameter: roles", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, never()).upload(List.of(expected), null);

            testContext.completeNow();
        });
    }

    @Test
    void updateRoleNotInServiceRoles(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.CLIENTKEY_ISSUER, Role.MAPPER, Role.SHARER)));
        ServiceLink existingLink = new ServiceLink("link1", 1, 123, "name1", Set.of(Role.MAPPER));
        setServiceLinks(existingLink);

        JsonObject jo = new JsonObject();
        jo.put("link_id", "link1");
        jo.put("service_id", 1);
        jo.put("site_id", 123);
        jo.put("name", "newname");
        jo.put("roles", JsonArray.of(Role.ADMINISTRATOR));

        ServiceLink expected = new ServiceLink("link1", 1, 123, "newname", Set.of(Role.ADMINISTRATOR));

        post(vertx, testContext, "api/service_link/update", jo.encode(), response -> {
            assertEquals(400, response.statusCode());
            assertTrue(response.bodyAsJsonObject().getString("message").startsWith("roles allowed: "));
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, never()).upload(List.of(expected), null);

            testContext.completeNow();
        });
    }

    @Test
    void deleteLinkId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.CLIENTKEY_ISSUER)));
        ServiceLink existingLink = new ServiceLink("link1", 1, 123, "name1", null);
        setServiceLinks(existingLink);

        JsonObject jo = new JsonObject();
        jo.put("link_id", "link1");
        jo.put("service_id", 1);

        post(vertx, testContext, "api/service_link/delete", jo.encode(), response -> {
            assertEquals(200, response.statusCode());
            checkServiceLinkJson(existingLink, response.bodyAsJsonObject());
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, times(1)).upload(new ArrayList<ServiceLink>(), null);
            testContext.completeNow();
        });
    }

    @Test
    void deleteOneLinkId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.CLIENTKEY_ISSUER)));
        ServiceLink existingLink = new ServiceLink("link1", 1, 123, "name1", null);
        ServiceLink existingLink2 = new ServiceLink("link2", 1, 123, "name2", null);
        ServiceLink existingLink3 = new ServiceLink("link3", 1, 123, "name3", null);
        setServiceLinks(existingLink, existingLink2, existingLink3);

        JsonObject jo = new JsonObject();
        jo.put("link_id", "link2");
        jo.put("service_id", 1);

        post(vertx, testContext, "api/service_link/delete", jo.encode(), response -> {
            assertEquals(200, response.statusCode());
            checkServiceLinkJson(existingLink2, response.bodyAsJsonObject());
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, times(1)).upload(List.of(existingLink, existingLink3), null);
            testContext.completeNow();
        });
    }

    @Test
    void deleteWrongLinkId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.CLIENTKEY_ISSUER)));
        ServiceLink existingLink = new ServiceLink("link1", 1, 123, "name1", null);
        setServiceLinks(existingLink);

        JsonObject jo = new JsonObject();
        jo.put("link_id", "missinglink1");
        jo.put("service_id", 1);

        post(vertx, testContext, "api/service_link/delete", jo.encode(), response -> {
            assertEquals(404, response.statusCode());
            assertEquals("failed to find a service_link for serviceId: 1 and link_id: missinglink1", response.bodyAsJsonObject().getString("message"));

            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, never()).upload(List.of(existingLink), null);
            testContext.completeNow();
        });
    }

    @Test
    void deleteWrongServiceId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.CLIENTKEY_ISSUER)));
        ServiceLink existingLink = new ServiceLink("link1", 1, 123, "name1", null);
        setServiceLinks(existingLink);

        JsonObject jo = new JsonObject();
        jo.put("link_id", "link1");
        jo.put("service_id", 12);

        post(vertx, testContext, "api/service_link/delete", jo.encode(), response -> {
            assertEquals(404, response.statusCode());
            assertEquals("failed to find a service_link for serviceId: 12 and link_id: link1", response.bodyAsJsonObject().getString("message"));

            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, never()).upload(List.of(existingLink), null);
            testContext.completeNow();
        });
    }
}
