package com.uid2.admin.vertx;

import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.service.ServiceService;
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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ServiceServiceTest extends ServiceTestBase {

    @Override
    protected IService createService() {
        return new ServiceService(auth, writeLock, serviceStoreWriter, serviceProvider, siteProvider, serviceLinkProvider);
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
                },
                () -> assertEquals(expectedService.getLinkIdRegex(), actualService.getString("link_id_regex"))
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
        fakeAuth(Role.MAINTAINER);

        get(vertx, testContext, "api/service/list", response -> {
            assertEquals(200, response.statusCode());
            JsonArray respArray = response.bodyAsJsonArray();
            assertEquals(0, respArray.size());
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void listServicesMultipleServices(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        Service[] expectedServices = {
                new Service(1, 123, "name1", Set.of()),
                new Service(2, 123, "name1", Set.of(Role.MAINTAINER)),
                new Service(3, 124, "name1", Set.of(Role.GENERATOR, Role.SHARING_PORTAL)),
                new Service(4, 125, "name1", Set.of())
        };

        setServices(expectedServices);

        get(vertx, testContext, "api/service/list", response -> {
            assertEquals(200, response.statusCode());
            checkServiceResponse(expectedServices, response.bodyAsJsonArray());
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void listIndividualBadServiceId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        Service existingService = new Service(3, 124, "name1", Set.of(Role.GENERATOR, Role.SHARING_PORTAL));
        setServices(existingService);

        get(vertx, testContext, "api/service/list/asdf", response -> {
            assertEquals(400, response.statusCode());
            assertEquals("failed to parse a service_id from request", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void listIndividualServiceIdNotFound(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        Service existingService = new Service(1, 124, "name1", Set.of(Role.GENERATOR, Role.SHARING_PORTAL));
        setServices(existingService);

        get(vertx, testContext, "api/service/list/3", response -> {
            assertEquals(404, response.statusCode());
            assertEquals("failed to find a service for service_id: 3", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void listIndividualService(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        Service existingService = new Service(3, 124, "name1", Set.of(Role.GENERATOR, Role.SHARING_PORTAL));
        setServices(existingService);

        get(vertx, testContext, "api/service/list/3", response -> {
            assertEquals(200, response.statusCode());
            checkServiceJson(existingService, response.bodyAsJsonObject());
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void addServiceMissingPayload(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        postWithoutBody(vertx, testContext, "api/service/add", response -> {
            assertEquals(400, response.statusCode());
            assertEquals("json payload required but not provided", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"site_id", "name", "roles"})
    void addServiceMissingParameters(String parameter, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        JsonObject jo = new JsonObject();
        jo.put("site_id", 123);
        jo.put("name", "name1");
        jo.put("roles", new JsonArray());

        jo.remove(parameter);

        post(vertx, testContext, "api/service/add", jo.encode(), response -> {
            assertEquals(400, response.statusCode());
            assertEquals("required parameters: site_id, name, roles", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void addServiceBadSiteId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        JsonObject jo = new JsonObject();
        jo.put("site_id", 123);
        jo.put("name", "name1");
        jo.put("roles", Set.of(Role.PRIVILEGED));

        post(vertx, testContext, "api/service/add", jo.encode(), response -> {
            assertEquals(404, response.statusCode());
            assertEquals("site_id 123 not valid", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void addServiceEmptyName(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        setSites(new Site(123, "name1", false));

        JsonObject jo = new JsonObject();
        jo.put("site_id", 123);
        jo.put("name", "");
        jo.put("roles", Set.of(Role.PRIVILEGED));

        post(vertx, testContext, "api/service/add", jo.encode(), response -> {
            assertEquals(400, response.statusCode());
            assertEquals("name cannot be empty", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void addServiceAlreadyExists(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "testName1", Set.of(Role.ID_READER)));

        JsonObject jo = new JsonObject();
        jo.put("site_id", 123);
        jo.put("name", "testName1");
        jo.put("roles", Set.of(Role.PRIVILEGED));

        post(vertx, testContext, "api/service/add", jo.encode(), response -> {
            assertEquals(400, response.statusCode());
            assertEquals("site_id 123 already has service of name testName1", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void addServiceDuplicateName(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        setSites(new Site(123, "name1", false));
        setServices(new Service(1, 123, "serviceName1", Set.of(Role.ID_READER)));

        setSites(new Site(456, "name2", false));

        JsonObject jo = new JsonObject();
        jo.put("site_id", 456);
        jo.put("name", "serviceName1");
        jo.put("roles", Set.of(Role.ID_READER));

        post(vertx, testContext, "api/service/add", jo.encode(), response -> {
            assertEquals(400, response.statusCode());
            assertEquals("service name serviceName1 already exists", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void addServiceBadRoles(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        setSites(new Site(123, "name1", false));

        JsonArray ja = new JsonArray();
        ja.add("bad");

        JsonObject jo = new JsonObject();
        jo.put("site_id", 123);
        jo.put("name", "name1");
        jo.put("roles", ja);

        post(vertx, testContext, "api/service/add", jo.encode(), response -> {
            assertEquals(400, response.statusCode());
            assertEquals("invalid parameter: roles", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void addServiceBadLinkIdRegex(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        setSites(new Site(123, "name1", false));

        JsonArray ja = new JsonArray();
        ja.add("GENERATOR");

        JsonObject jo = new JsonObject();
        jo.put("site_id", 123);
        jo.put("name", "name1");
        jo.put("roles", ja);
        jo.put("link_id_regex", "[unclosed");

        post(vertx, testContext, "api/service/add", jo.encode(), response -> {
            assertEquals(400, response.statusCode());
            assertEquals("invalid parameter: link_id_regex; not a valid regex", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void addService_emptyRoles_returnsError(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        setSites(new Site(123, "name1", false));

        JsonObject jo = new JsonObject();
        jo.put("site_id", 123);
        jo.put("name", "name1");
        jo.put("roles", new JsonArray());

        post(vertx, testContext, "api/service/add", jo.encode(), response -> {
            assertEquals(400, response.statusCode());
            assertEquals("required parameters: site_id, name, roles", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void addServiceNonEmptyRoles(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        setSites(new Site(123, "name1", false));

        JsonArray ja = new JsonArray();
        ja.add("GENERATOR");
        ja.add("ID_READER");

        JsonObject jo = new JsonObject();
        jo.put("site_id", 123);
        jo.put("name", "name1");
        jo.put("roles", ja);

        post(vertx, testContext, "api/service/add", jo.encode(), response -> {
            assertEquals(200, response.statusCode());
            Service expectedService = new Service(1, 123, "name1", Set.of(Role.GENERATOR, Role.ID_READER));
            checkServiceJson(expectedService, response.bodyAsJsonObject());
            verify(serviceStoreWriter, times(1)).upload(List.of(expectedService), null);
            testContext.completeNow();
        });
    }

    @Test
    void addServiceWithRegex(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        setSites(new Site(123, "name1", false));

        JsonArray ja = new JsonArray();
        ja.add("GENERATOR");
        ja.add("ID_READER");

        JsonObject jo = new JsonObject();
        jo.put("site_id", 123);
        jo.put("name", "name1");
        jo.put("roles", ja);
        jo.put("link_id_regex", "valid");

        post(vertx, testContext, "api/service/add", jo.encode(), response -> {
            assertEquals(200, response.statusCode());
            Service expectedService = new Service(1, 123, "name1", Set.of(Role.GENERATOR, Role.ID_READER), "valid");
            checkServiceJson(expectedService, response.bodyAsJsonObject());
            verify(serviceStoreWriter, times(1)).upload(List.of(expectedService), null);
            testContext.completeNow();
        });
    }


    @Test
    void addServiceToExistingList(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

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
        jo.put("link_id_regex", "valid");

        post(vertx, testContext, "api/service/add", jo.encode(), response -> {
            assertEquals(200, response.statusCode());
            Service expectedService = new Service(2, 123, "name2", Set.of(Role.GENERATOR, Role.ID_READER), "valid");
            checkServiceJson(expectedService, response.bodyAsJsonObject());
            verify(serviceStoreWriter, times(1)).upload(List.of(existingService, expectedService), null);
            testContext.completeNow();
        });
    }

    @Test
    void updateMissingPayload(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        postWithoutBody(vertx, testContext, "api/service/update", response -> {
            assertEquals(400, response.statusCode());
            assertEquals("json payload required but not provided", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"service_id"})
    void updateMissingParameters(String parameter, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        JsonObject jo = new JsonObject();
        jo.put("service_id", 1);
        jo.put("roles", new JsonArray());
        jo.put("link_id_regex", "valid");

        jo.remove(parameter);

        post(vertx, testContext, "api/service/update", jo.encode(), response -> {
            assertEquals(400, response.statusCode());
            assertEquals("required parameters: service_id", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void updateBadServiceId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        setServices(new Service(1, 123, "name1", Set.of(Role.MAINTAINER)));

        JsonArray ja = new JsonArray();
        ja.add("bad");

        JsonObject jo = new JsonObject();
        jo.put("service_id", 2);
        jo.put("roles", ja);
        jo.put("link_id_regex", "valid");

        post(vertx, testContext, "api/service/update", jo.encode(), response -> {
            assertEquals(404, response.statusCode());
            assertEquals("failed to find a service for service_id: 2", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void updateRolesBadRoles(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        setServices(new Service(1, 123, "name1", Set.of(Role.MAINTAINER)));

        JsonArray ja = new JsonArray();
        ja.add("bad");

        JsonObject jo = new JsonObject();
        jo.put("service_id", 1);
        jo.put("roles", ja);

        post(vertx, testContext, "api/service/update", jo.encode(), response -> {
            assertEquals(400, response.statusCode());
            assertEquals("invalid parameter: roles", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void updateRoles(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        Service existingService = new Service(1, 123, "name1", Set.of(Role.MAINTAINER));
        setServices(existingService);

        JsonArray ja = new JsonArray();
        ja.add("GENERATOR");
        ja.add("PRIVILEGED");

        JsonObject jo = new JsonObject();
        jo.put("service_id", 1);
        jo.put("roles", ja);

        post(vertx, testContext, "api/service/update", jo.encode(), response -> {
            assertEquals(200, response.statusCode());
            existingService.setRoles(Set.of(Role.GENERATOR, Role.PRIVILEGED));
            checkServiceJson(existingService, response.bodyAsJsonObject());
            verify(serviceStoreWriter, times(1)).upload(List.of(existingService), null);
            testContext.completeNow();
        });
    }

    @Test
    void updateLinkIdRegex(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        Service existingService = new Service(1, 123, "name1", Set.of(Role.MAINTAINER));
        setServices(existingService);

        JsonObject jo = new JsonObject();
        jo.put("service_id", 1);
        jo.put("link_id_regex", "valid");

        post(vertx, testContext, "api/service/update", jo.encode(), response -> {
            assertEquals(200, response.statusCode());
            existingService.setLinkIdRegex("valid");
            checkServiceJson(existingService, response.bodyAsJsonObject());
            verify(serviceStoreWriter, times(1)).upload(List.of(existingService), null);
            testContext.completeNow();
        });
    }

    @Test
    void updateService_removeRolesNotUsedByServiceLinks_succeeds(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        Service existingService = new Service(1, 123, "name1", Set.of(Role.MAINTAINER, Role.MAPPER, Role.SHARER));
        setServices(existingService);
        ServiceLink sl1 = new ServiceLink("abc123", 1, 123, "link1", Set.of(Role.MAPPER));
        ServiceLink sl2 = new ServiceLink("cde", 1, 123, "link2", Set.of(Role.MAPPER, Role.SHARER));
        ServiceLink sl3 = new ServiceLink("ghi789", 1, 123, "link3", Set.of(Role.SHARER));
        setServiceLinks(sl1, sl2, sl3);

        JsonObject jo = new JsonObject();
        jo.put("service_id", 1);
        jo.put("roles", JsonArray.of("MAPPER", "SHARER"));

        post(vertx, testContext, "api/service/update", jo.encode(), response -> {
            assertEquals(200, response.statusCode());
            existingService.setRoles(Set.of(Role.MAPPER, Role.SHARER));
            checkServiceJson(existingService, response.bodyAsJsonObject());
            verify(serviceStoreWriter, times(1)).upload(List.of(existingService), null);
            testContext.completeNow();
        });
    }

    @Test
    void updateService_removeRolesInUseByServiceLinks_returnsError(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        Service existingService = new Service(1, 123, "name1", Set.of(Role.MAINTAINER, Role.MAPPER, Role.SHARER));
        setServices(existingService);
        ServiceLink sl1 = new ServiceLink("abc123", 1, 123, "link1", Set.of(Role.MAPPER));
        ServiceLink sl2 = new ServiceLink("cde", 1, 123, "link2", Set.of(Role.MAPPER, Role.SHARER));
        ServiceLink sl3 = new ServiceLink("ghi789", 1, 123, "link3", Set.of(Role.SHARER));
        setServiceLinks(sl1, sl2, sl3);

        JsonObject jo = new JsonObject();
        jo.put("service_id", 1);
        jo.put("roles", JsonArray.of(Role.MAINTAINER.name(), Role.SHARER.name()));

        post(vertx, testContext, "api/service/update", jo.encode(), response -> {
            assertEquals(400, response.statusCode());
            assertEquals("roles: MAPPER may still be in use", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void updateName(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        Service existingService = new Service(1, 123, "name1", Set.of(Role.MAINTAINER));
        setServices(existingService);

        JsonObject jo = new JsonObject();
        jo.put("service_id", 1);
        jo.put("name", "newName");

        post(vertx, testContext, "api/service/update", jo.encode(), response -> {
            assertEquals(200, response.statusCode());
            existingService.setName("newName");
            checkServiceJson(existingService, response.bodyAsJsonObject());
            verify(serviceStoreWriter, times(1)).upload(List.of(existingService), null);
            testContext.completeNow();
        });
    }

    @Test
    void updateDisabled(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        Service existingService = new Service(1, 123, "name1", Set.of(Role.MAINTAINER));
        setServices(existingService);

        JsonObject jo = new JsonObject();
        jo.put("service_id", 1);
        jo.put("disabled", true);

        post(vertx, testContext, "api/service/update", jo.encode(), response -> {
            assertEquals(200, response.statusCode());
            existingService.setDisabled(true);
            checkServiceJson(existingService, response.bodyAsJsonObject());
            verify(serviceStoreWriter, times(1)).upload(List.of(existingService), null);
            testContext.completeNow();
        });
    }

    @Test
    void updateSiteId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        Service existingService = new Service(1, 123, "name1", Set.of(Role.MAINTAINER));
        setServices(existingService);

        JsonObject jo = new JsonObject();
        jo.put("service_id", 1);
        jo.put("site_id", 456);

        post(vertx, testContext, "api/service/update", jo.encode(), response -> {
            assertEquals(200, response.statusCode());
            existingService.setSiteId(456);
            checkServiceJson(existingService, response.bodyAsJsonObject());
            verify(serviceStoreWriter, times(1)).upload(List.of(existingService), null);
            testContext.completeNow();
        });
    }

    @Test
    void updateNameDisabledSiteIdRoles(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        Service existingService = new Service(1, 123, "name1", Set.of(Role.MAINTAINER));
        setServices(existingService);

        JsonObject jo = new JsonObject();
        jo.put("service_id", 1);
        jo.put("site_id", 456);
        jo.put("name", "newname");
        jo.put("disabled", true);
        jo.put("roles", JsonArray.of(Role.MAINTAINER, Role.MAPPER, Role.SHARER));

        post(vertx, testContext, "api/service/update", jo.encode(), response -> {
            assertEquals(200, response.statusCode());
            existingService.setSiteId(456);
            existingService.setName("newname");
            existingService.setDisabled(true);
            existingService.setRoles(Set.of(Role.MAINTAINER, Role.MAPPER, Role.SHARER));
            checkServiceJson(existingService, response.bodyAsJsonObject());
            verify(serviceStoreWriter, times(1)).upload(List.of(existingService), null);
            testContext.completeNow();
        });
    }

    @Test
    void updateWithEmptyValues(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        Service existingService = new Service(1, 123, "name1", Set.of(Role.MAINTAINER));
        setServices(existingService);

        JsonObject jo = new JsonObject();
        jo.put("service_id", 1);
        jo.put("site_id", null);
        jo.put("name", "");
        jo.put("link_id_regex", "");

        post(vertx, testContext, "api/service/update", jo.encode(), response -> {
            assertEquals(200, response.statusCode());
            checkServiceJson(existingService, response.bodyAsJsonObject());
            verify(serviceStoreWriter, times(1)).upload(List.of(existingService), null);
            testContext.completeNow();
        });
    }

    @Test
    void updateWithEmptyRoleString(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        Service existingService = new Service(1, 123, "name1", Set.of(Role.MAINTAINER));
        setServices(existingService);

        JsonObject jo = new JsonObject();
        jo.put("service_id", 1);
        jo.put("roles", "");

        post(vertx, testContext, "api/service/update", jo.encode(), response -> {
            assertEquals(200, response.statusCode());
            checkServiceJson(existingService, response.bodyAsJsonObject());
            verify(serviceStoreWriter, times(1)).upload(List.of(existingService), null);
            testContext.completeNow();
        });
    }

    @Test
    void updateWithEmptyRoleArray(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        Service existingService = new Service(1, 123, "name1", Set.of(Role.MAINTAINER));
        setServices(existingService);

        Service expectedService = new Service(1, 123, "name1", Set.of());

        JsonObject jo = new JsonObject();
        jo.put("service_id", 1);
        jo.put("roles", "[]");

        post(vertx, testContext, "api/service/update", jo.encode(), response -> {
            assertEquals(400, response.statusCode());
            assertEquals("invalid parameter: roles", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void updateSiteCreateDuplicateSiteIdAndName(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        Service existingService1 = new Service(1, 123, "name1", Set.of(Role.MAINTAINER));
        Service existingService2 = new Service(2, 789, "name2", Set.of(Role.MAINTAINER));
        setServices(existingService1, existingService2);

        JsonObject jo = new JsonObject();
        jo.put("service_id", 2);
        jo.put("site_id", 123);
        jo.put("name", "name1");

        post(vertx, testContext, "api/service/update", jo.encode(), response -> {
            assertEquals(400, response.statusCode());
            assertEquals("site_id 123 already has service of name name1", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void updateServiceDuplicateName(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        Service existingService1 = new Service(1, 123, "name1", Set.of(Role.MAINTAINER));
        Service existingService2 = new Service(2, 789, "name2", Set.of(Role.MAINTAINER));
        setServices(existingService1, existingService2);

        JsonObject jo = new JsonObject();
        jo.put("service_id", 2);
        jo.put("site_id", 789);
        jo.put("name", "name1");

        post(vertx, testContext, "api/service/update", jo.encode(), response -> {
            assertEquals(400, response.statusCode());
            assertEquals("service name name1 already exists", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void removeRegex(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        Service existingService = new Service(1, 123, "name1", Set.of(Role.MAINTAINER), "valid");
        setServices(existingService);

        JsonObject jo = new JsonObject();
        jo.put("service_id", 1);

        post(vertx, testContext, "api/service/remove-link-id-regex", jo.encode(), response -> {
            assertEquals(200, response.statusCode());
            existingService.setLinkIdRegex(null);
            checkServiceJson(existingService, response.bodyAsJsonObject());
            verify(serviceStoreWriter, times(1)).upload(List.of(existingService), null);
            testContext.completeNow();
        });
    }

    @Test
    void removeRegexInvalidServiceId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        Service existingService = new Service(1, 123, "name1", Set.of(Role.MAINTAINER), "valid");
        setServices(existingService);

        JsonObject jo = new JsonObject();
        jo.put("service_id", 2);

        post(vertx, testContext, "api/service/remove-link-id-regex", jo.encode(), response -> {
            assertEquals(404, response.statusCode());
            assertEquals("failed to find a service for service_id: 2", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void removeRegexNoBody(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        Service existingService = new Service(1, 123, "name1", Set.of(Role.MAINTAINER), "valid");
        setServices(existingService);

        post(vertx, testContext, "api/service/remove-link-id-regex", "", response -> {
            assertEquals(400, response.statusCode());
            assertEquals("json payload required but not provided", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);
            testContext.completeNow();
        });
    }

    @Test
    void deleteService(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SUPER_USER);

        Service existingService = new Service(1, 123, "name1", Set.of(Role.MAINTAINER));
        setServices(existingService);

        JsonObject jo = new JsonObject();
        jo.put("service_id", 1);

        post(vertx, testContext, "api/service/delete", jo.encode(), response -> {
            assertEquals(200, response.statusCode());
            checkServiceJson(existingService, response.bodyAsJsonObject());
            verify(serviceStoreWriter, times(1)).upload(new ArrayList<Service>(), null);
            testContext.completeNow();
        });
    }

    @Test
    void deleteServiceInvalidServiceId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SUPER_USER);

        Service existingService = new Service(1, 123, "name1", Set.of(Role.MAINTAINER));
        setServices(existingService);

        JsonObject jo = new JsonObject();
        jo.put("missing_service_id", 1);

        post(vertx, testContext, "api/service/delete", jo.encode(), response -> {
            assertEquals(400, response.statusCode());

            assertEquals("required parameters: service_id", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);

            verify(serviceStoreWriter, never()).upload(new ArrayList<Service>(), null);
            verify(serviceStoreWriter, never()).upload(List.of(existingService), null);
            testContext.completeNow();
        });
    }

    @Test
    void deleteServiceNoBody(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SUPER_USER);

        Service existingService = new Service(1, 123, "name1", Set.of(Role.MAINTAINER));
        setServices(existingService);

        post(vertx, testContext, "api/service/delete", "", response -> {
            assertEquals(400, response.statusCode());

            assertEquals("json payload required but not provided", response.bodyAsJsonObject().getString("message"));
            verify(serviceStoreWriter, never()).upload(null, null);

            verify(serviceStoreWriter, never()).upload(new ArrayList<Service>(), null);
            verify(serviceStoreWriter, never()).upload(List.of(existingService), null);
            testContext.completeNow();
        });
    }

    @Test
    void deleteOneService(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SUPER_USER);

        Service existingService = new Service(1, 123, "name1", Set.of(Role.MAINTAINER));
        Service existingService2 = new Service(2, 123, "name2", Set.of(Role.MAINTAINER));
        Service existingService3 = new Service(32, 123, "name3", Set.of(Role.MAINTAINER));
        setServices(existingService, existingService2, existingService3);

        JsonObject jo = new JsonObject();
        jo.put("service_id", 2);

        post(vertx, testContext, "api/service/delete", jo.encode(), response -> {
            assertEquals(200, response.statusCode());

            checkServiceJson(existingService2, response.bodyAsJsonObject());
            verify(serviceStoreWriter, times(1)).upload(List.of(existingService, existingService3), null);
            testContext.completeNow();
        });
    }
}
