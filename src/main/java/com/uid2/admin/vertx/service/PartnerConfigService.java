// Copyright (c) 2021 The Trade Desk, Inc
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice,
//    this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice,
//    this list of conditions and the following disclaimer in the documentation
//    and/or other materials provided with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
// POSSIBILITY OF SUCH DAMAGE.

package com.uid2.admin.vertx.service;

import com.uid2.admin.audit.Actions;
import com.uid2.admin.audit.AuditMiddleware;
import com.uid2.admin.audit.OperationModel;
import com.uid2.admin.audit.Type;
import com.uid2.admin.store.IStorageManager;
import com.uid2.admin.store.RotatingPartnerStore;
import com.uid2.admin.vertx.ResponseUtil;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.auth.Role;
import com.uid2.shared.middleware.AuthMiddleware;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.*;

public class PartnerConfigService implements IService {
    private final AuditMiddleware audit;
    private final AuthMiddleware auth;
    private final WriteLock writeLock;
    private final IStorageManager storageManager;
    private final RotatingPartnerStore partnerConfigProvider;

    public PartnerConfigService(AuditMiddleware audit,
                                AuthMiddleware auth,
                                WriteLock writeLock,
                                IStorageManager storageManager,
                                RotatingPartnerStore partnerConfigProvider) {
        this.audit = audit;
        this.auth = auth;
        this.writeLock = writeLock;
        this.storageManager = storageManager;
        this.partnerConfigProvider = partnerConfigProvider;
    }

    @Override
    public void setupRoutes(Router router) {
        router.get("/api/partner_config/get").handler(
                auth.handle(audit.handle(this::handlePartnerConfigGet), Role.ADMINISTRATOR));
        router.post("/api/partner_config/update").blockingHandler(auth.handle(audit.handle((ctx) -> {
            synchronized (writeLock) {
                return this.handlePartnerConfigUpdate(ctx);
            }
        }), Role.ADMINISTRATOR));
    }

    @Override
    public Collection<OperationModel> backfill(){
        try{
            String config = this.partnerConfigProvider.getConfig();
            return Collections.singletonList(new OperationModel(Type.PARTNER, "singleton", null,
                    DigestUtils.sha256Hex(config), null));
        }
        catch(Exception e){
            e.printStackTrace();
            return new HashSet<>();
        }
    }

    private List<OperationModel> handlePartnerConfigGet(RoutingContext rc) {
        try {
            String config = this.partnerConfigProvider.getConfig();
            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(config);
            return Collections.singletonList(new OperationModel(Type.PARTNER, "singleton", Actions.GET,
                    DigestUtils.sha256Hex(config), "get partner config"));
        } catch (Exception e) {
            rc.fail(500, e);
            return null;
        }
    }

    private List<OperationModel> handlePartnerConfigUpdate(RoutingContext rc) {
        try {
            // refresh manually
            this.partnerConfigProvider.loadContent();
            JsonArray partners = rc.getBodyAsJsonArray();
            if (partners == null) {
                ResponseUtil.error(rc, 400, "Body must be none empty");
                return null;
            }

            storageManager.uploadPartners(this.partnerConfigProvider, partners);

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end("\"success\"");
            return Collections.singletonList(new OperationModel(Type.PARTNER, "singleton", Actions.UPDATE,
                    DigestUtils.sha256Hex(partnerConfigProvider.getConfig()), "updated partner config"));
        } catch (Exception e) {
            rc.fail(500, e);
            return null;
        }
    }
}
