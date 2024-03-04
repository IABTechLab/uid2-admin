package com.uid2.admin.vertx.service;

import com.uid2.admin.auth.AdminAuthMiddleware;
import com.uid2.admin.store.reader.RotatingPartnerStore;
import com.uid2.admin.store.writer.PartnerStoreWriter;
import com.uid2.admin.vertx.ResponseUtil;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.auth.Role;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

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
        router.get("/api/partner_config/get").handler(
            auth.handle(this::handlePartnerConfigGet, Role.ALL));
        router.post("/api/partner_config/update").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handlePartnerConfigUpdate(ctx);
            }
        }, Role.PRIVILEGED));
    }

    private void handlePartnerConfigGet(RoutingContext rc) {
        try {
            String config = this.partnerConfigProvider.getConfig();
            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(config);
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handlePartnerConfigUpdate(RoutingContext rc) {
        try {
            // refresh manually
            this.partnerConfigProvider.loadContent();
            JsonArray partners = rc.body().asJsonArray();
            if (partners == null) {
                ResponseUtil.error(rc, 400, "Body must be none empty");
                return;
            }

            storageManager.upload(partners);

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end("\"success\"");
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }
}
