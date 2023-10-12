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

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

        get(vertx, testContext,  "api/service_link/list", response -> {
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
                new ServiceLink("link1", 1, 123, "name1"),
                new ServiceLink("link2", 1, 123, "name2"),
                new ServiceLink("link3", 2, 124, "name3"),
                new ServiceLink("link4", 3, 125, "name4"),
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

        post(vertx, "api/service_link/add", jo.encode(), testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(400, response.statusCode());
            assertEquals("required parameters: link_id, service_id, site_id, name", response.bodyAsJsonObject().getString("message"));
            verifyNoInteractions(serviceStoreWriter);
            verify(serviceLinkStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        })));
    }

    @Test
    void addServiceLinkBadServiceId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        JsonObject jo = new JsonObject();
        jo.put("link_id", "link1");
        jo.put("service_id", 1);
        jo.put("site_id", 124);
        jo.put("name", "name1");

        post(vertx, "api/service_link/add", jo.encode(), testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(404, response.statusCode());
            assertEquals("service_id 1 not valid", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        })));
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

        post(vertx, "api/service_link/add", jo.encode(), testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(404, response.statusCode());
            assertEquals("site_id 124 not valid", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        })));
    }

    @Test
    void addDuplicateServiceLinkFails(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.CLIENTKEY_ISSUER)));
        setServiceLinks(new ServiceLink("link1", 1, 123, "name1"));

        JsonObject jo = new JsonObject();
        jo.put("link_id", "link1");
        jo.put("service_id", 1);
        jo.put("site_id", 123);
        jo.put("name", "name1");

        post(vertx, "api/service_link/add", jo.encode(), testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(400, response.statusCode());
            assertEquals("service link already exists", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        })));
    }

    @Test
    void addServiceLink(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.CLIENTKEY_ISSUER)));

        ServiceLink expected = new ServiceLink("link1", 1, 123, "name1");

        JsonObject jo = new JsonObject();
        jo.put("link_id", "link1");
        jo.put("service_id", 1);
        jo.put("site_id", 123);
        jo.put("name", "name1");

        post(vertx, "api/service_link/add", jo.encode(), testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(200, response.statusCode());
            checkServiceLinkJson(expected, response.bodyAsJsonObject());
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, times(1)).upload(List.of(expected), null);
            testContext.completeNow();
        })));
    }

    @Test
    void addServiceLinkToExistingList(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "name1", Set.of(Role.CLIENTKEY_ISSUER)));
        ServiceLink existingLink = new ServiceLink("link1", 1, 123, "name1");
        setServiceLinks(existingLink);

        ServiceLink expected = new ServiceLink("link2", 1, 123, "name1");

        JsonObject jo = new JsonObject();
        jo.put("link_id", "link2");
        jo.put("service_id", 1);
        jo.put("site_id", 123);
        jo.put("name", "name1");

        post(vertx, "api/service_link/add", jo.encode(), testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(200, response.statusCode());
            checkServiceLinkJson(expected, response.bodyAsJsonObject());
            verify(serviceStoreWriter, never()).upload(null, null);
            verify(serviceLinkStoreWriter, times(1)).upload(List.of(existingLink, expected), null);
            testContext.completeNow();
        })));
    }
}
