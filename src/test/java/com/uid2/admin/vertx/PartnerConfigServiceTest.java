package com.uid2.admin.vertx;

import com.uid2.admin.store.reader.RotatingPartnerStore;
import com.uid2.admin.store.writer.PartnerStoreWriter;
import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.service.PartnerConfigService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.admin.vertx.test.TestHandler;
import com.uid2.shared.Const;
import com.uid2.shared.Utils;
import com.uid2.shared.auth.Role;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class PartnerConfigServiceTest extends ServiceTestBase {
    @Mock
    protected RotatingPartnerStore partnerConfigProvider;

    @Override
    protected IService createService() {
        return new PartnerConfigService(auth, writeLock, partnerStoreWriter, partnerConfigProvider);
    }

    private void setPartnerConfigs(JsonObject... configs) {
        JsonArray partnerConfigs = new JsonArray();
        for (JsonObject config : configs) {
            partnerConfigs.add(config);
        }
        when(partnerConfigProvider.getConfig()).thenReturn(partnerConfigs.encode());
    }

    private JsonObject createPartnerConfig(String name, String url) {
        JsonObject config = new JsonObject();
        config.put("name", name);
        config.put("url", url);
        config.put("method", "GET");
        config.put("retry_count", 600);
        config.put("retry_backoff_ms", 6000);
        return config;
    }

    // LIST endpoint tests
    @Test
    void listPartnerConfigsWithConfigs(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        JsonObject config1 = createPartnerConfig("partner1", "https://example.com/webhook1");
        JsonObject config2 = createPartnerConfig("partner2", "https://example.com/webhook2");
        setPartnerConfigs(config1, config2);

        get(vertx, testContext, "api/partner_config/list", response -> {
            JsonArray result = response.bodyAsJsonArray();
            assertAll(
                () -> assertEquals(200, response.statusCode()),
                () -> assertEquals(2, result.size()),
                () -> assertEquals("partner1", result.getJsonObject(0).getString("name")),
                () -> assertEquals("partner2", result.getJsonObject(1).getString("name"))
            );
            testContext.completeNow();
        });
    }

    @Test
    void listPartnerConfigsUnauthorized(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAPPER);

        get(vertx, testContext, "api/partner_config/list", response -> {
            assertEquals(401, response.statusCode());
            testContext.completeNow();
        });
    }

    // GET by name endpoint tests
    @Test
    void getPartnerConfigByNameSuccess(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        JsonObject config1 = createPartnerConfig("partner1", "https://example.com/webhook1");
        JsonObject config2 = createPartnerConfig("partner2", "https://example.com/webhook2");
        setPartnerConfigs(config1, config2);

        get(vertx, testContext, "api/partner_config/get/partner1", response -> {
            JsonObject result = response.bodyAsJsonObject();
            assertAll(
                () -> assertEquals(200, response.statusCode()),
                () -> assertEquals("partner1", result.getString("name")),
                () -> assertEquals("https://example.com/webhook1", result.getString("url"))
            );
            testContext.completeNow();
        });
    }

    @Test
    void getPartnerConfigByNameCaseInsensitive(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        JsonObject config = createPartnerConfig("Partner1", "https://example.com/webhook1");
        setPartnerConfigs(config);

        get(vertx, testContext, "api/partner_config/get/PARTNER1", response -> {
            assertEquals(200, response.statusCode());
            testContext.completeNow();
        });
    }

    @Test
    void getPartnerConfigByNameNotFound(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        JsonObject config = createPartnerConfig("partner1", "https://example.com/webhook1");
        setPartnerConfigs(config);

        get(vertx, testContext, "api/partner_config/get/nonexistent", response -> {
            assertEquals(404, response.statusCode());
            testContext.completeNow();
        });
    }

    // ADD endpoint tests
    @Test
    void addPartnerConfigSuccess(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        JsonObject existingConfig = createPartnerConfig("existing-partner", "https://example.com/webhook");
        setPartnerConfigs(existingConfig);

        JsonObject newConfig = createPartnerConfig("new-partner", "https://new.com/webhook");

        post(vertx, testContext, "api/partner_config/add", newConfig.encode(), response -> {
            assertEquals(200, response.statusCode());
            verify(partnerStoreWriter).upload(argThat(array -> {
                JsonArray arr = (JsonArray) array;
                if (arr.size() != 2) return false;
                JsonObject addedConfig = arr.getJsonObject(1);
                return "new-partner".equals(addedConfig.getString("name")) &&
                       "https://new.com/webhook".equals(addedConfig.getString("url"));
            }));
            testContext.completeNow();
        });
    }

    @Test
    void addPartnerConfigAlreadyExists(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        JsonObject existingConfig = createPartnerConfig("existing-partner", "https://example.com/webhook");
        setPartnerConfigs(existingConfig);

        JsonObject duplicateConfig = createPartnerConfig("existing-partner", "https://new.com/webhook");

        post(vertx, testContext, "api/partner_config/add", duplicateConfig.encode(), response -> {
            assertEquals(409, response.statusCode());
            verify(partnerStoreWriter, never()).upload(any());
            testContext.completeNow();
        });
    }

    @Test
    void addPartnerConfigAlreadyExistsCaseInsensitive(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        JsonObject existingConfig = createPartnerConfig("Existing-Partner", "https://example.com/webhook");
        setPartnerConfigs(existingConfig);

        JsonObject duplicateConfig = createPartnerConfig("EXISTING-PARTNER", "https://new.com/webhook");

        post(vertx, testContext, "api/partner_config/add", duplicateConfig.encode(), response -> {
            assertEquals(409, response.statusCode());
            verify(partnerStoreWriter, never()).upload(any());
            testContext.completeNow();
        });
    }

    @ParameterizedTest
    @CsvSource(value = {
            "''",
            "'{\"url\":\"https://example.com\",\"method\":\"GET\",\"retry_count\":600,\"retry_backoff_ms\":6000}'",
            "'{\"name\":\"test\",\"method\":\"GET\",\"retry_count\":600,\"retry_backoff_ms\":6000}'",
            "'{\"name\":\"test\",\"url\":\"https://example.com\",\"retry_count\":600,\"retry_backoff_ms\":6000}'",
            "'{\"name\":\"test\",\"url\":\"https://example.com\",\"method\":\"GET\",\"retry_backoff_ms\":6000}'",
            "'{\"name\":\"test\",\"url\":\"https://example.com\",\"method\":\"GET\",\"retry_count\":600}'"
    })
    void addPartnerConfigMissingRequiredFields(String body, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);
        setPartnerConfigs();

        post(vertx, testContext, "api/partner_config/add", body, response -> {
            assertEquals(400, response.statusCode());
            verify(partnerStoreWriter, never()).upload(any());
            testContext.completeNow();
        });
    }

    @Test
    void addPartnerConfigUnauthorized(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAPPER);

        JsonObject newConfig = createPartnerConfig("new-partner", "https://new.com/webhook");

        post(vertx, testContext, "api/partner_config/add", newConfig.encode(), response -> {
            assertEquals(401, response.statusCode());
            verify(partnerStoreWriter, never()).upload(any());
            testContext.completeNow();
        });
    }

    // UPDATE endpoint tests
    @Test
    void updatePartnerConfigSuccess(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        JsonObject config1 = createPartnerConfig("partner1", "https://p1.com/webhook");
        JsonObject config2 = createPartnerConfig("partner2", "https://p2.com/webhook");
        setPartnerConfigs(config1, config2);

        JsonObject updatedConfig = createPartnerConfig("partner2", "https://updated.com/webhook");

        put(vertx, testContext, "api/partner_config/update", updatedConfig.encode(), response -> {
            assertEquals(200, response.statusCode());
            verify(partnerStoreWriter).upload(argThat(array -> {
                JsonArray arr = (JsonArray) array;
                if (arr.size() != 2) return false;
                JsonObject updated = arr.getJsonObject(1);
                return "partner2".equals(updated.getString("name")) &&
                       "https://updated.com/webhook".equals(updated.getString("url"));
            }));
            testContext.completeNow();
        });
    }

    @Test
    void updatePartnerConfigNotFound(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        JsonObject existingConfig = createPartnerConfig("partner1", "https://example.com/webhook");
        setPartnerConfigs(existingConfig);

        JsonObject updateConfig = createPartnerConfig("nonexistent", "https://new.com/webhook");

        put(vertx, testContext, "api/partner_config/update", updateConfig.encode(), response -> {
            assertEquals(404, response.statusCode());
            verify(partnerStoreWriter, never()).upload(any());
            testContext.completeNow();
        });
    }

    @Test
    void updatePartnerConfigCaseInsensitive(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        JsonObject existingConfig = createPartnerConfig("Partner1", "https://old.com/webhook");
        setPartnerConfigs(existingConfig);

        JsonObject updatedConfig = createPartnerConfig("PARTNER1", "https://new.com/webhook");

        put(vertx, testContext, "api/partner_config/update", updatedConfig.encode(), response -> {
            assertEquals(200, response.statusCode());
            verify(partnerStoreWriter).upload(any(JsonArray.class));
            testContext.completeNow();
        });
    }

    @ParameterizedTest
    @CsvSource(value = {
            "''",
            "'{\"name\":\"partner1\",\"url\":\"https://new.com\"}'"
    })
    void updatePartnerConfigMissingRequiredFields(String body, Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        JsonObject existingConfig = createPartnerConfig("partner1", "https://old.com/webhook");
        setPartnerConfigs(existingConfig);

        put(vertx, testContext, "api/partner_config/update", body, response -> {
            assertEquals(400, response.statusCode());
            verify(partnerStoreWriter, never()).upload(any());
            testContext.completeNow();
        });
    }

    // DELETE endpoint tests
    @Test
    void deletePartnerConfigSuccess(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        JsonObject config1 = createPartnerConfig("partner1", "https://p1.com/webhook");
        JsonObject config2 = createPartnerConfig("partner2", "https://p2.com/webhook");
        setPartnerConfigs(config1, config2);

        delete(vertx, testContext, "api/partner_config/delete?partner_name=partner1", response -> {
            assertEquals(200, response.statusCode());
            verify(partnerStoreWriter).upload(argThat(array -> {
                JsonArray arr = (JsonArray) array;
                if (arr.size() != 1) return false;
                return "partner2".equals(arr.getJsonObject(0).getString("name"));
            }));
            testContext.completeNow();
        });
    }

    @Test
    void deletePartnerConfigNotFound(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        JsonObject config = createPartnerConfig("partner1", "https://example.com/webhook");
        setPartnerConfigs(config);

        delete(vertx, testContext, "api/partner_config/delete?partner_name=nonexistent", response -> {
            assertEquals(404, response.statusCode());
            verify(partnerStoreWriter, never()).upload(any());
            testContext.completeNow();
        });
    }

    @Test
    void deletePartnerConfigCaseInsensitive(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        JsonObject config = createPartnerConfig("Partner1", "https://example.com/webhook");
        setPartnerConfigs(config);

        delete(vertx, testContext, "api/partner_config/delete?partner_name=PARTNER1", response -> {
            assertEquals(200, response.statusCode());
            verify(partnerStoreWriter).upload(any(JsonArray.class));
            testContext.completeNow();
        });
    }

    @Test
    void deletePartnerConfigNoPartnerName(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);
        setPartnerConfigs();

        delete(vertx, testContext, "api/partner_config/delete", response -> {
            assertEquals(400, response.statusCode());
            verify(partnerStoreWriter, never()).upload(any());
            testContext.completeNow();
        });
    }

    // BULK_REPLACE endpoint tests
    @Test
    void bulkReplacePartnerConfigsSuccess(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);

        JsonObject existingConfig = createPartnerConfig("old-partner", "https://old.com/webhook");
        setPartnerConfigs(existingConfig);

        JsonArray newConfigs = new JsonArray();
        newConfigs.add(createPartnerConfig("partner1", "https://p1.com/webhook"));
        newConfigs.add(createPartnerConfig("partner2", "https://p2.com/webhook"));

        post(vertx, testContext, "api/partner_config/bulk_replace", newConfigs.encode(), response -> {
            assertEquals(200, response.statusCode());
            verify(partnerStoreWriter).upload(argThat(array -> {
                JsonArray arr = (JsonArray) array;
                if (arr.size() != 2) return false;
                return "partner1".equals(arr.getJsonObject(0).getString("name")) &&
                       "partner2".equals(arr.getJsonObject(1).getString("name"));
            }));
            testContext.completeNow();
        });
    }

    @Test
    void bulkReplacePartnerConfigsEmptyArray(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);
        setPartnerConfigs();

        JsonArray emptyConfigs = new JsonArray();

        post(vertx, testContext, "api/partner_config/bulk_replace", emptyConfigs.encode(), response -> {
            assertEquals(200, response.statusCode());
            verify(partnerStoreWriter).upload(argThat(array -> {
                JsonArray arr = (JsonArray) array;
                return arr.size() == 0;
            }));
            testContext.completeNow();
        });
    }

    @Test
    void bulkReplacePartnerConfigsNullBody(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);
        setPartnerConfigs();

        post(vertx, testContext, "api/partner_config/bulk_replace", "", response -> {
            assertEquals(400, response.statusCode());
            verify(partnerStoreWriter, never()).upload(any());
            testContext.completeNow();
        });
    }

    @Test
    void bulkReplacePartnerConfigsInvalidConfig(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.PRIVILEGED);
        setPartnerConfigs();

        JsonArray configs = new JsonArray();
        configs.add(createPartnerConfig("partner1", "https://p1.com/webhook"));
        configs.add(new JsonObject().put("name", "partner2")); // Missing required fields

        post(vertx, testContext, "api/partner_config/bulk_replace", configs.encode(), response -> {
            assertEquals(400, response.statusCode());
            verify(partnerStoreWriter, never()).upload(any());
            testContext.completeNow();
        });
    }

    @Test
    void bulkReplacePartnerConfigsUnauthorized(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER); // Bulk replace requires PRIVILEGED

        JsonArray newConfigs = new JsonArray();
        newConfigs.add(createPartnerConfig("partner1", "https://p1.com/webhook"));

        post(vertx, testContext, "api/partner_config/bulk_replace", newConfigs.encode(), response -> {
            assertEquals(401, response.statusCode());
            verify(partnerStoreWriter, never()).upload(any());
            testContext.completeNow();
        });
    }
}
