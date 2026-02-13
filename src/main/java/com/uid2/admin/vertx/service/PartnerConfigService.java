package com.uid2.admin.vertx.service;

import com.uid2.admin.auth.AdminAuthMiddleware;
import com.uid2.admin.store.reader.RotatingPartnerStore;
import com.uid2.admin.store.writer.PartnerStoreWriter;
import com.uid2.admin.vertx.ResponseUtil;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.audit.AuditParams;
import com.uid2.shared.auth.Role;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.uid2.admin.vertx.Endpoints.API_PARTNER_CONFIG_LIST;
import static com.uid2.admin.vertx.Endpoints.API_PARTNER_CONFIG_GET;
import static com.uid2.admin.vertx.Endpoints.API_PARTNER_CONFIG_ADD;
import static com.uid2.admin.vertx.Endpoints.API_PARTNER_CONFIG_UPDATE;
import static com.uid2.admin.vertx.Endpoints.API_PARTNER_CONFIG_DELETE;
import static com.uid2.admin.vertx.Endpoints.API_PARTNER_CONFIG_BULK_REPLACE;

public class PartnerConfigService implements IService {
    private final AdminAuthMiddleware auth;
    private final WriteLock writeLock;
    private final PartnerStoreWriter storageManager;
    private final RotatingPartnerStore partnerConfigProvider;

    public PartnerConfigService(AdminAuthMiddleware auth,
                                WriteLock writeLock,
                                PartnerStoreWriter storageManager,
                                RotatingPartnerStore partnerConfigProvider) {
        this.auth = auth;
        this.writeLock = writeLock;
        this.storageManager = storageManager;
        this.partnerConfigProvider = partnerConfigProvider;
    }

    @Override
    public void setupRoutes(Router router) {
        router.get(API_PARTNER_CONFIG_LIST.toString()).handler(
            auth.handle(this::handlePartnerConfigList, Role.MAINTAINER));
        router.get(API_PARTNER_CONFIG_GET.toString()).handler(
            auth.handle(this::handlePartnerConfigGet, Role.MAINTAINER));

        router.post(API_PARTNER_CONFIG_ADD.toString()).blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handlePartnerConfigAdd(ctx);
            }
        }, new AuditParams(Collections.emptyList(), List.of("name")), Role.MAINTAINER));
        router.put(API_PARTNER_CONFIG_UPDATE.toString()).blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handlePartnerConfigUpdate(ctx);
            }
        }, new AuditParams(Collections.emptyList(), List.of("name")), Role.MAINTAINER));
        router.delete(API_PARTNER_CONFIG_DELETE.toString()).blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handlePartnerConfigDelete(ctx);
            }
        }, new AuditParams(List.of("partner_name"), Collections.emptyList()), Role.MAINTAINER));
        router.post(API_PARTNER_CONFIG_BULK_REPLACE.toString()).blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handlePartnerConfigBulkReplace(ctx);
            }
        }, new AuditParams(Collections.emptyList(), List.of("name")), Role.PRIVILEGED));
    }

    private void handlePartnerConfigList(RoutingContext rc) {
        try {
            this.partnerConfigProvider.loadContent();
            String config = this.partnerConfigProvider.getConfig();
            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(config);
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handlePartnerConfigGet(RoutingContext rc) {
        try {
            final String partnerName = rc.pathParam("partner_name");
            if (partnerName == null || partnerName.isEmpty()) {
                ResponseUtil.error(rc, 400, "Partner name is required");
                return;
            }

            this.partnerConfigProvider.loadContent();
            JsonArray allPartnerConfigs = new JsonArray(this.partnerConfigProvider.getConfig());
            int index = findPartnerIndex(allPartnerConfigs, partnerName);

            if (index == -1) {
                ResponseUtil.error(rc, 404, "Partner '" + partnerName + "' not found");
                return;
            }

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(allPartnerConfigs.getJsonObject(index).encode());
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handlePartnerConfigAdd(RoutingContext rc) {
        try {
            JsonObject newConfig = rc.body().asJsonObject();
            if (newConfig == null) {
                ResponseUtil.error(rc, 400, "Body must include Partner config");
                return;
            }

            // Validate required fields
            if (!validatePartnerConfig(rc, newConfig)) {
                return;
            }

            String newPartnerName = newConfig.getString("name");
            this.partnerConfigProvider.loadContent();
            JsonArray allPartnerConfigs = new JsonArray(this.partnerConfigProvider.getConfig());

            // Validate partner doesn't exist
            if (findPartnerIndex(allPartnerConfigs, newPartnerName) != -1) {
                ResponseUtil.error(rc, 409, "Partner '" + newPartnerName + "' already exists");
                return;
            }

            // Upload
            allPartnerConfigs.add(newConfig);
            storageManager.upload(allPartnerConfigs);

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end("\"success\"");
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handlePartnerConfigUpdate(RoutingContext rc) {
        try {
            JsonObject newConfig = rc.body().asJsonObject();
            if (newConfig == null) {
                ResponseUtil.error(rc, 400, "Body must include Partner config");
                return;
            }

            // Validate required fields
            if (!validatePartnerConfig(rc, newConfig)) {
                return;
            }

            String newPartnerName = newConfig.getString("name");
            this.partnerConfigProvider.loadContent();
            JsonArray allPartnerConfigs = new JsonArray(this.partnerConfigProvider.getConfig());

            // Validate partner exists
            int existingPartnerIdx = findPartnerIndex(allPartnerConfigs, newPartnerName);
            if (existingPartnerIdx == -1) {
                ResponseUtil.error(rc, 404, "Partner '" + newPartnerName + "' not found");
                return;
            }

            // Upload
            allPartnerConfigs.set(existingPartnerIdx, newConfig);
            storageManager.upload(allPartnerConfigs);

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end("\"success\"");
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handlePartnerConfigDelete(RoutingContext rc) {
        try {
            final List<String> partnerNames = rc.queryParam("partner_name");
            if (partnerNames.isEmpty()) {
                ResponseUtil.error(rc, 400, "Partner name is required");
                return;
            }
            final String partnerName = partnerNames.getFirst();

            this.partnerConfigProvider.loadContent();
            JsonArray allPartnerConfigs = new JsonArray(this.partnerConfigProvider.getConfig());

            // Find partner config
            int existingPartnerIdx = findPartnerIndex(allPartnerConfigs, partnerName);
            if (existingPartnerIdx == -1) {
                ResponseUtil.error(rc, 404, "Partner '" + partnerName + "' not found");
                return;
            }

            // Remove
            allPartnerConfigs.remove(existingPartnerIdx);
            storageManager.upload(allPartnerConfigs);
            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end("\"success\"");
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handlePartnerConfigBulkReplace(RoutingContext rc) {
        try {
            // refresh manually
            this.partnerConfigProvider.loadContent();
            JsonArray partners = rc.body().asJsonArray();

            if (partners == null) {
                ResponseUtil.error(rc, 400, "Body must be non-empty");
                return;
            }

            // Keep track of names to check for duplicates
            Set<String> partnerNames = new HashSet<>();

            // Validate each config
            for (int i = 0; i < partners.size(); i++) {
                JsonObject config = partners.getJsonObject(i);
                if (config == null) {
                    ResponseUtil.error(rc, 400, "Could not parse config at index " + i);
                    return;
                }

                if (!validatePartnerConfig(rc, config)) {
                    return;
                }

                String name = partners.getJsonObject(i).getString("name");
                if (name != null && !partnerNames.add(name.toLowerCase())) {
                    ResponseUtil.error(rc, 400, "Duplicate partner name: " + name);
                    return;
                }
            }

            storageManager.upload(partners);

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end("\"success\"");
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private boolean validatePartnerConfig(RoutingContext rc, JsonObject config) {
        String name = config.getString("name");
        String url = config.getString("url");
        String method = config.getString("method");
        Integer retryCount = config.getInteger("retry_count");
        Integer retryBackoffMs = config.getInteger("retry_backoff_ms");

        if (name == null || name.trim().isEmpty()) {
            ResponseUtil.error(rc, 400, "Partner config 'name' is required");
            return false;
        }
        if (url == null || url.trim().isEmpty()) {
            ResponseUtil.error(rc, 400, "Partner config 'url' is required");
            return false;
        }
        if (method == null || method.trim().isEmpty()) {
            ResponseUtil.error(rc, 400, "Partner config 'method' is required");
            return false;
        }
        if (retryCount == null || retryCount < 0) {
            ResponseUtil.error(rc, 400, "Partner config 'retry_count' is required and must be >= 0");
            return false;
        }
        if (retryBackoffMs == null || retryBackoffMs < 0) {
            ResponseUtil.error(rc, 400, "Partner config 'retry_backoff_ms' is required and must be >= 0");
            return false;
        }

        return true;
    }

    private int findPartnerIndex(JsonArray configs, String partnerName) {
        if (partnerName == null) return -1;
        for (int i = 0; i < configs.size(); i++) {
            JsonObject config = configs.getJsonObject(i);
            String name = config.getString("name");
            if (partnerName.equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }
}
