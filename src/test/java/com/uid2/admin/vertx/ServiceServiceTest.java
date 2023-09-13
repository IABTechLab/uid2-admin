package com.uid2.admin.vertx;

import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.service.ServiceService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.Service;
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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ServiceServiceTest extends ServiceTestBase {

    @Override
    protected IService createService() {
        return new ServiceService(auth, writeLock, serviceStoreWriter, serviceProvider, siteProvider);
    }

    private void checkServiceResponse(Service[] expectedServices, JsonArray actualServices) {
        assertAll(
                "checkServiceResponse",
                () -> assertEquals(expectedServices.length, actualServices.size()),
                () -> {
                    for (int i = 0; i < expectedServices.length; i++) {
                        checkServiceJson(expectedServices[i], actualServices.getJsonObject(i));
                    }
                });
    }

    private void checkServiceJson(Service expectedService, JsonObject actualService) {
        assertAll(
                "checkServiceJson",
                () -> assertEquals(expectedService.getServiceId(), actualService.getInteger("service_id")),
                () -> assertEquals(expectedService.getSiteId(), actualService.getInteger("site_id")),
                () -> assertEquals(expectedService.getName(), actualService.getString("name")),
                () -> {
                    Set<Role> actualRoles = actualService.getJsonArray("roles").stream().map(s -> Role.valueOf((String) s)).collect(Collectors.toSet());
                    assertEquals(expectedService.getRoles(), actualRoles);
                }
        );
    }

    @Test
    void serviceRolesCannotBeNull() {
        Service service = new Service(1, 123, "name1", null);
        assertEquals(Set.of(), service.getRoles());
        service.setRoles(null);
        assertEquals(Set.of(), service.getRoles());
    }

    @Test
    void listServicesNoServices(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        get(vertx, "api/service/list", testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(200, response.statusCode());
            JsonArray respArray = response.bodyAsJsonArray();
            assertEquals(0, respArray.size());
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        })));
    }

    @Test
    void listServicesMultipleServices(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        Service[] expectedServices = {
                new Service(1, 123, "name1", Set.of()),
                new Service(2, 123, "name1", Set.of(Role.CLIENTKEY_ISSUER)),
                new Service(3, 124, "name1", Set.of(Role.GENERATOR, Role.SHARING_PORTAL)),
                new Service(4, 125, "name1", Set.of())
        };

        setServices(expectedServices);

        get(vertx, "api/service/list", testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(200, response.statusCode());
            checkServiceResponse(expectedServices, response.bodyAsJsonArray());
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        })));
    }

    @Test
    void listIndividualBadServiceId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        Service existingService = new Service(3, 124, "name1", Set.of(Role.GENERATOR, Role.SHARING_PORTAL));
        setServices(existingService);

        get(vertx, "api/service/list/asdf", testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(400, response.statusCode());
            assertEquals("failed to parse a service_id from request", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        })));
    }

    @Test
    void listIndividualServiceIdNotFound(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        Service existingService = new Service(1, 124, "name1", Set.of(Role.GENERATOR, Role.SHARING_PORTAL));
        setServices(existingService);

        get(vertx, "api/service/list/3", testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(404, response.statusCode());
            assertEquals("failed to find a service for service_id: 3", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        })));
    }

    @Test
    void listIndividualService(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        Service existingService = new Service(3, 124, "name1", Set.of(Role.GENERATOR, Role.SHARING_PORTAL));
        setServices(existingService);

        get(vertx, "api/service/list/3", testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(200, response.statusCode());
            checkServiceJson(existingService, response.bodyAsJsonObject());
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        })));
    }

    @Test
    void addServiceMissingPayload(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        postWithoutBody(vertx, "api/service/add", testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(400, response.statusCode());
            assertEquals("json payload required but not provided", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        })));
    }

    @ParameterizedTest
    @ValueSource(strings = {"site_id", "name", "roles"})
    void addServiceMissingParameters(String parameter, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        JsonObject jo = new JsonObject();
        jo.put("site_id", 123);
        jo.put("name", "name1");
        jo.put("roles", new JsonArray());

        jo.remove(parameter);

        post(vertx, "api/service/add", jo.encode(), testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(400, response.statusCode());
            assertEquals("required parameters: site_id, name, roles", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        })));
    }

    @Test
    void addServiceBadSiteId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        JsonObject jo = new JsonObject();
        jo.put("site_id", 123);
        jo.put("name", "name1");
        jo.put("roles", new JsonArray());

        post(vertx, "api/service/add", jo.encode(), testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(404, response.statusCode());
            assertEquals("site_id 123 not valid", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        })));
    }

    @Test
    void addServiceEmptyName(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setSites(new Site(123, "name1", false));

        JsonObject jo = new JsonObject();
        jo.put("site_id", 123);
        jo.put("name", "");
        jo.put("roles", new JsonArray());

        post(vertx, "api/service/add", jo.encode(), testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(400, response.statusCode());
            assertEquals("name cannot be empty", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        })));
    }

    @Test
    void addServiceAlreadyExists(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "testName1", Set.of(Role.ID_READER)));

        JsonObject jo = new JsonObject();
        jo.put("site_id", 123);
        jo.put("name", "testName1");
        jo.put("roles", new JsonArray());

        post(vertx, "api/service/add", jo.encode(), testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(400, response.statusCode());
            assertEquals("site_id 123 already has service of name testName1", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        })));
    }

    @Test
    void addServiceBadRoles(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setSites(new Site(123, "name1", false));

        JsonArray ja = new JsonArray();
        ja.add("bad");

        JsonObject jo = new JsonObject();
        jo.put("site_id", 123);
        jo.put("name", "name1");
        jo.put("roles", ja);

        post(vertx, "api/service/add", jo.encode(), testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(400, response.statusCode());
            assertEquals("invalid parameter: roles", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        })));
    }

    @Test
    void addServiceEmptyRoles(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setSites(new Site(123, "name1", false));

        JsonObject jo = new JsonObject();
        jo.put("site_id", 123);
        jo.put("name", "name1");
        jo.put("roles", new JsonArray());

        post(vertx, "api/service/add", jo.encode(), testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(200, response.statusCode());
            Service expectedService = new Service(1, 123, "name1", Set.of());
            checkServiceJson(expectedService, response.bodyAsJsonObject());
            verify(serviceStoreWriter, times(1)).upload(List.of(expectedService), null);
            testContext.completeNow();
        })));
    }

    @Test
    void addServiceNonEmptyRoles(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setSites(new Site(123, "name1", false));

        JsonArray ja = new JsonArray();
        ja.add("GENERATOR");
        ja.add("ID_READER");

        JsonObject jo = new JsonObject();
        jo.put("site_id", 123);
        jo.put("name", "name1");
        jo.put("roles", ja);

        post(vertx, "api/service/add", jo.encode(), testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(200, response.statusCode());
            Service expectedService = new Service(1, 123, "name1", Set.of(Role.GENERATOR, Role.ID_READER));
            checkServiceJson(expectedService, response.bodyAsJsonObject());
            verify(serviceStoreWriter, times(1)).upload(List.of(expectedService), null);
            testContext.completeNow();
        })));
    }

    @Test
    void addServiceToExistingList(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setSites(new Site(123, "name1", false));

        Service existingService = new Service(1, 123, "name1", Set.of());
        setServices(existingService);

        JsonArray ja = new JsonArray();
        ja.add("GENERATOR");
        ja.add("ID_READER");

        JsonObject jo = new JsonObject();
        jo.put("site_id", 123);
        jo.put("name", "name2");
        jo.put("roles", ja);

        post(vertx, "api/service/add", jo.encode(), testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(200, response.statusCode());
            Service expectedService = new Service(2, 123, "name2", Set.of(Role.GENERATOR, Role.ID_READER));
            checkServiceJson(expectedService, response.bodyAsJsonObject());
            verify(serviceStoreWriter, times(1)).upload(List.of(existingService, expectedService), null);
            testContext.completeNow();
        })));
    }

    @Test
    void updateRolesMissingPayload(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        postWithoutBody(vertx, "api/service/roles", testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(400, response.statusCode());
            assertEquals("json payload required but not provided", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        })));
    }

    @ParameterizedTest
    @ValueSource(strings = {"service_id", "roles"})
    void updateRolesMissingParameters(String parameter, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        JsonObject jo = new JsonObject();
        jo.put("service_id", 1);
        jo.put("roles", new JsonArray());

        jo.remove(parameter);

        post(vertx, "api/service/roles", jo.encode(), testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(400, response.statusCode());
            assertEquals("required parameters: service_id, roles", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        })));
    }

    @Test
    void updateRolesBadServiceId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setServices(new Service(1, 123, "name1", Set.of(Role.CLIENTKEY_ISSUER)));

        JsonArray ja = new JsonArray();
        ja.add("bad");

        JsonObject jo = new JsonObject();
        jo.put("service_id", 2);
        jo.put("roles", ja);

        post(vertx, "api/service/roles", jo.encode(), testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(404, response.statusCode());
            assertEquals("failed to find a service for service_id: 2", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        })));
    }

    @Test
    void updateRolesBadRoles(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setServices(new Service(1, 123, "name1", Set.of(Role.CLIENTKEY_ISSUER)));

        JsonArray ja = new JsonArray();
        ja.add("bad");

        JsonObject jo = new JsonObject();
        jo.put("service_id", 1);
        jo.put("roles", ja);

        post(vertx, "api/service/roles", jo.encode(), testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(400, response.statusCode());
            assertEquals("invalid parameter: roles", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        })));
    }

    @Test
    void updateRoles(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        Service existingService = new Service(1, 123, "name1", Set.of(Role.CLIENTKEY_ISSUER));
        setServices(existingService);

        JsonArray ja = new JsonArray();
        ja.add("GENERATOR");
        ja.add("ADMINISTRATOR");

        JsonObject jo = new JsonObject();
        jo.put("service_id", 1);
        jo.put("roles", ja);

        post(vertx, "api/service/roles", jo.encode(), testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(200, response.statusCode());
            existingService.setRoles(Set.of(Role.GENERATOR, Role.ADMINISTRATOR));
            checkServiceJson(existingService, response.bodyAsJsonObject());
            verify(serviceStoreWriter, times(1)).upload(List.of(existingService), null);
            testContext.completeNow();
        })));
    }
}