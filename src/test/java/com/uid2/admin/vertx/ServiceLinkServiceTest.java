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
import org.junit.jupiter.params.provider.MethodSource;
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
    void listLinks_noLinks_returnsEmptyArray(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

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
    void listLinks_multipleLinks_returnsAllLinks(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

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
    void addServiceLink_missingPayload_returnsError(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);
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
    @ValueSource(strings = {"link_id", "service_id", "site_id", "name", "roles"})
    void addServiceLink_missingParameters_returnsError(String parameter, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        JsonObject jo = new JsonObject();
        jo.put("link_id", "link1");
        jo.put("service_id", 1);
        jo.put("site_id", 123);
        jo.put("name", "name1");
        jo.put("roles", Set.of(Role.MAINTAINER));

        jo.remove(parameter);

        post(vertx, testContext, "api/service_link/add", jo.encode(), response -> {
            assertEquals(400, response.statusCode());
            assertEquals("required parameters: link_id, service_id, site_id, name, roles", response.bodyAsJsonObject().getString("message"));
            verifyNoInteractions(serviceStoreWriter);
            verify(serviceLinkStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void addServiceLink_invalidServiceId_returnsError(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        JsonObject jo = new JsonObject();
        jo.put("link_id", "link1");
        jo.put("service_id", 1);
        jo.put("site_id", 124);
        jo.put("name", "name1");
        jo.put("roles", Set.of(Role.MAINTAINER));

        post(vertx, testContext, "api/service_link/add", jo.encode(), response -> {
            assertEquals(404, response.statusCode());
            assertEquals("service_id 1 not valid", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void addServiceLink_invalidSiteId_returnsError(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        setServices(new Service(1, 123, "name1", Set.of(Role.MAINTAINER)));

        JsonObject jo = new JsonObject();
        jo.put("link_id", "link1");
        jo.put("service_id", 1);
        jo.put("site_id", 124);
        jo.put("name", "name1");
        jo.put("roles", Set.of(Role.MAINTAINER));

        post(vertx, testContext, "api/service_link/add", jo.encode(), response -> {
            assertEquals(404, response.statusCode());
            assertEquals("site_id 124 not valid", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void addServiceLink_duplicateServiceLink_returnsError(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.MAINTAINER)));
        setServiceLinks(new ServiceLink("link1", 1, 123, "name1", null));

        JsonObject jo = new JsonObject();
        jo.put("link_id", "link1");
        jo.put("service_id", 1);
        jo.put("site_id", 123);
        jo.put("name", "name1");
        jo.put("roles", Set.of(Role.MAINTAINER));

        post(vertx, testContext, "api/service_link/add", jo.encode(), response -> {
            assertEquals(400, response.statusCode());
            assertEquals("service link already exists", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void addServiceLink_allFieldsValid_succeeds(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.MAINTAINER)));

        ServiceLink expected = new ServiceLink("link1", 1, 123, "name1", Set.of(Role.MAINTAINER));

        JsonObject jo = new JsonObject();
        jo.put("link_id", "link1");
        jo.put("service_id", 1);
        jo.put("site_id", 123);
        jo.put("name", "name1");
        jo.put("roles", JsonArray.of(Role.MAINTAINER));

        post(vertx, testContext, "api/service_link/add", jo.encode(), response -> {
            assertEquals(200, response.statusCode());
            checkServiceLinkJson(expected, response.bodyAsJsonObject());
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, times(1)).upload(List.of(expected), null);
            testContext.completeNow();
        });
    }

    @Test
    void addServiceLink_emptyRole_returnsError(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

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
            assertEquals("required parameters: link_id, service_id, site_id, name, roles", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void addServiceLink_rolesSubsetOfAllowedRoles_succeeds(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.MAPPER, Role.MAINTAINER, Role.SHARER, Role.OPTOUT)));

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
    void addServiceLink_roleDoesNotExist_returnsError(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.MAPPER, Role.MAINTAINER, Role.SHARER, Role.OPTOUT)));

        JsonObject jo = new JsonObject();
        jo.put("link_id", "link1");
        jo.put("service_id", 1);
        jo.put("site_id", 123);
        jo.put("name", "name1");
        jo.put("roles", JsonArray.of("IllegalRole"));

        post(vertx, testContext, "api/service_link/add", jo.encode(), response -> {
            assertEquals(400, response.statusCode());
            assertTrue(response.bodyAsJsonObject().getString("message").startsWith("invalid parameter: roles. Roles allowed: "));
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void addServiceLink_roleNotInSubset_returnsError(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.MAPPER, Role.SHARER)));

        JsonObject jo = new JsonObject();
        jo.put("link_id", "link1");
        jo.put("service_id", 1);
        jo.put("site_id", 123);
        jo.put("name", "name1");
        jo.put("roles", JsonArray.of(Role.MAINTAINER));

        post(vertx, testContext, "api/service_link/add", jo.encode(), response -> {
            assertEquals(400, response.statusCode());
            assertTrue(response.bodyAsJsonObject().getString("message").startsWith("roles allowed: "));
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void addServiceLink_addToExistingList_succeeds(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.MAINTAINER)));
        ServiceLink existingLink = new ServiceLink("link1", 1, 123, "name1", null);
        setServiceLinks(existingLink);

        ServiceLink expected = new ServiceLink("link2", 1, 123, "name1", Set.of(Role.MAINTAINER));

        JsonObject jo = new JsonObject();
        jo.put("link_id", "link2");
        jo.put("service_id", 1);
        jo.put("site_id", 123);
        jo.put("name", "name1");
        jo.put("roles", Set.of(Role.MAINTAINER));

        post(vertx, testContext, "api/service_link/add", jo.encode(), response -> {
            assertEquals(200, response.statusCode());
            checkServiceLinkJson(expected, response.bodyAsJsonObject());
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, times(1)).upload(List.of(existingLink, expected), null);
            testContext.completeNow();
        });
    }

    @Test
    void updateServiceLink_nameAndLinkIdDoesNotExist_returnsError(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.MAINTAINER)));
        ServiceLink existingLink = new ServiceLink("link1", 1, 123, "name1", null);
        setServiceLinks(existingLink);

        JsonObject jo = new JsonObject();
        jo.put("link_id", "newLink");
        jo.put("service_id", 1);
        jo.put("site_id", 123);
        jo.put("name", "newName");

        post(vertx, testContext, "api/service_link/update", jo.encode(), response -> {
            assertEquals(404, response.statusCode());
            assertEquals("failed to find a service_link for serviceId: 1, site_id: 123 and link_id: newLink", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void updateServiceLink_updateNameOnly_succeeds(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.MAINTAINER, Role.MAPPER)));
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
    void updateServiceLink_updateRoleOnly_succeeds(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

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
    void updateServiceLink_updateRoleAndName_succeeds(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.MAINTAINER, Role.MAPPER, Role.SHARER)));
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
    void updateServiceLink_roleDoesNotExist_returnsError(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.MAINTAINER, Role.MAPPER, Role.SHARER)));
        ServiceLink existingLink = new ServiceLink("link1", 1, 123, "name1", Set.of(Role.MAPPER));
        setServiceLinks(existingLink);

        JsonObject jo = new JsonObject();
        jo.put("link_id", "link1");
        jo.put("service_id", 1);
        jo.put("site_id", 123);
        jo.put("name", "newname");
        jo.put("roles", JsonArray.of("IllegalRole"));

        post(vertx, testContext, "api/service_link/update", jo.encode(), response -> {
            assertEquals(400, response.statusCode());
            assertTrue(response.bodyAsJsonObject().getString("message").startsWith("invalid parameter: roles. Roles allowed: "));
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, never()).upload(null, null);

            testContext.completeNow();
        });
    }

    @Test
    void updateServiceLink_roleNotAllowedInServiceRoles_returnsError(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.MAPPER, Role.GENERATOR)));
        ServiceLink existingLink = new ServiceLink("link1", 1, 123, "name1", Set.of(Role.MAPPER));
        setServiceLinks(existingLink);

        JsonObject jo = new JsonObject();
        jo.put("link_id", "link1");
        jo.put("service_id", 1);
        jo.put("site_id", 123);
        jo.put("name", "newname");
        jo.put("roles", JsonArray.of(Role.SHARER));

        post(vertx, testContext, "api/service_link/update", jo.encode(), response -> {
            assertEquals(400, response.statusCode());
            assertTrue(response.bodyAsJsonObject().getString("message").startsWith("roles allowed: "));
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, never()).upload(null, null);

            testContext.completeNow();
        });
    }

    @Test
    void deleteServiceLink_oneServiceLinkExists_succeeds(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.PRIVILEGED)));
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
    void deleteServiceLink_multipleServiceLinksExist_correctServiceLinkDeleted(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.MAINTAINER)));
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
    void deleteServiceLink_invalidLinkId_returnsError(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.MAINTAINER)));
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
    void deleteServiceLink_invalidServiceId_returnsError(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.MAINTAINER)));
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

    @ParameterizedTest
    @MethodSource("linkIdRegexCases")
    void addServiceLink_linkIdRegex_validation(String linkIdRegex, String linkId, boolean expectSuccess,
                                               Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.MAINTAINER), linkIdRegex));

        JsonObject jo = new JsonObject();
        jo.put("link_id", linkId);
        jo.put("service_id", 1);
        jo.put("site_id", 123);
        jo.put("name", "name1");
        jo.put("roles", JsonArray.of(Role.MAINTAINER));

        post(vertx, testContext, "api/service_link/add", jo.encode(), response -> {
            if (expectSuccess) {
                assertEquals(200, response.statusCode());
                ServiceLink expected = new ServiceLink(linkId, 1, 123, "name1", Set.of(Role.MAINTAINER));
                checkServiceLinkJson(expected, response.bodyAsJsonObject());
                verify(serviceLinkStoreWriter, times(1)).upload(List.of(expected), null);
            } else {
                assertEquals(400, response.statusCode());
                String expectedMsg = "link_id " + linkId + " does not match service_id 1 link_id_regex: " + linkIdRegex;
                assertEquals(expectedMsg, response.bodyAsJsonObject().getString("message"));
                verify(serviceLinkStoreWriter, never()).upload(any(), any());
            }

            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    private static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> linkIdRegexCases() {
        return java.util.stream.Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("link[0-9]+", "invalidLink", false),
                org.junit.jupiter.params.provider.Arguments.of("link[0-9]+", "link42", true),
                org.junit.jupiter.params.provider.Arguments.of("^[A-Z0-9_]{1,256}$", "XY12345", true), // snowflake valid
                org.junit.jupiter.params.provider.Arguments.of("^[A-Z0-9_]{1,256}$", "UID2_ENVIRONMENT", true), // snowflake valid
                org.junit.jupiter.params.provider.Arguments.of("^[A-Z0-9_]{1,256}$", "xy12345", false), // snowflake invalid, lowercase
                org.junit.jupiter.params.provider.Arguments.of("^[A-Z0-9_]{1,256}$", "X", true), // snowflake valid, minimum length
                org.junit.jupiter.params.provider.Arguments.of("^[A-Z0-9_]{1,256}$", "X".repeat(256), true), // snowflake valid, maximum length
                org.junit.jupiter.params.provider.Arguments.of("^[A-Z0-9_]{1,256}$", "X".repeat(257), false), // snowflake invalid, exceeds maximum length
                org.junit.jupiter.params.provider.Arguments.of("^[A-Z0-9_]{1,256}$", " XY12345", false), // snowflake invalid, leading whitespace
                org.junit.jupiter.params.provider.Arguments.of("^[A-Z0-9_]{1,256}$", "XY12345 ", false), // snowflake invalid, trailing whitespace
                org.junit.jupiter.params.provider.Arguments.of("^[A-Z0-9_]{1,256}$", "XY 12345", false), // snowflake invalid, whitespace in the middle
                org.junit.jupiter.params.provider.Arguments.of("^[A-Z0-9_]{1,256}$", "XY_12345", true) // snowflake valid, used underscore
        );
    }
}
