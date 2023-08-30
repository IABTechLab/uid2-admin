package com.uid2.admin.vertx;

import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.service.ServiceService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.Service;
import com.uid2.shared.model.Site;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class ServiceServiceTest extends ServiceTestBase {

    @Override
    protected IService createService() {
        return new ServiceService(auth, writeLock, storeWriter, serviceProvider, siteProvider);
    }

    private void checkServiceResponse(Service[] expectedServices, JsonArray actualServices) {
        assertAll(
                "checkServiceResponse",
                () -> assertEquals(expectedServices.length, actualServices.size()),
                () -> {
                    for (int i = 0; i< expectedServices.length; i++) {
                        checkServiceJson(expectedServices[i], (JsonObject) actualServices.getJsonObject(i));
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
    void listServicesNoServices(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        get(vertx, "api/service/list", ar -> {
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());

            JsonArray respArray = response.bodyAsJsonArray();
            assertEquals(0, respArray.size());

            testContext.completeNow();
        });
    }

    @Test
    void listServicesMultipleServices(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        Service[] expectedServices = {
            new Service(1, 123, "test1", Set.of()),
            new Service(2, 123, "test1", Set.of(Role.CLIENTKEY_ISSUER)),
            new Service(3, 124, "test1", Set.of(Role.GENERATOR, Role.SHARING_PORTAL)),
            new Service(4, 125, "test1", Set.of())
        };

        setServices(expectedServices);

        get(vertx, "api/service/list", ar -> {
            HttpResponse<Buffer> response = ar.result();

            assertEquals(200, response.statusCode());

            checkServiceResponse(expectedServices, response.bodyAsJsonArray());
            testContext.completeNow();
        });
    }

    @Test
    void addServiceMissingPayload(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        postWithoutBody(vertx, "api/service/add", ar -> {
            HttpResponse<Buffer> response = ar.result();

            assertEquals(400, response.statusCode());
            assertEquals("json payload required but not provided", response.bodyAsJsonObject().getString("message"));

            testContext.completeNow();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"site_id", "name", "roles"})
    void addServiceMissingParameters(String parameter, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        JsonObject jo = new JsonObject();
        jo.put("site_id", 123);
        jo.put("name", "test1");
        jo.put("roles", new JsonArray());

        jo.remove(parameter);

        post(vertx, "api/service/add", jo.encode(), ar -> {
            HttpResponse<Buffer> response = ar.result();

            assertEquals(400, response.statusCode());
            assertEquals("required parameters: site_id, name, roles", response.bodyAsJsonObject().getString("message"));

            testContext.completeNow();
        });
    }

    @Test
    void addServiceBadSiteId(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        JsonObject jo = new JsonObject();
        jo.put("site_id", 123);
        jo.put("name", "test1");
        jo.put("roles", new JsonArray());


        post(vertx, "api/service/add", jo.encode(), ar -> {
            HttpResponse<Buffer> response = ar.result();

            assertEquals(404, response.statusCode());
            assertEquals("site_id 123 not valid", response.bodyAsJsonObject().getString("message"));

            testContext.completeNow();
        });
    }

    @Test
    void addServiceBadRoles(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setSites(new Site(123, "test1", false));

        JsonArray ja = new JsonArray();
        ja.add("bad");

        JsonObject jo = new JsonObject();
        jo.put("site_id", 123);
        jo.put("name", "test1");
        jo.put("roles", ja);


        post(vertx, "api/service/add", jo.encode(), ar -> {
            HttpResponse<Buffer> response = ar.result();

            assertEquals(400, response.statusCode());
            assertEquals("invalid parameter: roles", response.bodyAsJsonObject().getString("message"));

            testContext.completeNow();
        });
    }

    @Test
    void addServiceNoneExisting(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.ADMINISTRATOR);

        setSites(new Site(123, "test1", false));

        JsonArray ja = new JsonArray();
        ja.add("GENERATOR");
        ja.add("ID_READER");

        JsonObject jo = new JsonObject();
        jo.put("site_id", 123);
        jo.put("name", "test1");
        jo.put("roles", ja);


        post(vertx, "api/service/add", jo.encode(), ar -> {
            HttpResponse<Buffer> response = ar.result();

            assertEquals(200, response.statusCode());

            testContext.completeNow();
        });
    }
}
