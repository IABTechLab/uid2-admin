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
import com.uid2.admin.secret.ISaltRotation;
import com.uid2.admin.store.IStorageManager;
import com.uid2.admin.vertx.RequestUtil;
import com.uid2.admin.vertx.ResponseUtil;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.auth.Role;
import com.uid2.shared.middleware.AuthMiddleware;
import com.uid2.shared.model.SaltEntry;
import com.uid2.shared.store.RotatingSaltProvider;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.codec.digest.DigestUtils;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class SaltService implements IService {
    private final AuditMiddleware audit;
    private final AuthMiddleware auth;
    private final WriteLock writeLock;
    private final IStorageManager storageManager;
    private final RotatingSaltProvider saltProvider;
    private final ISaltRotation saltRotation;

    public SaltService(AuditMiddleware audit,
                       AuthMiddleware auth,
                       WriteLock writeLock,
                       IStorageManager storageManager,
                       RotatingSaltProvider saltProvider,
                       ISaltRotation saltRotation) {
        this.audit = audit;
        this.auth = auth;
        this.writeLock = writeLock;
        this.storageManager = storageManager;
        this.saltProvider = saltProvider;
        this.saltRotation = saltRotation;
    }

    @Override
    public void setupRoutes(Router router) {
        router.get("/api/salt/snapshots").handler(
                auth.handle(audit.handle(this::handleSaltSnapshots), Role.SECRET_MANAGER));

        router.post("/api/salt/rotate").blockingHandler(auth.handle(audit.handle((ctx) -> {
            synchronized (writeLock) {
                return this.handleSaltRotate(ctx);
            }
        }), Role.SECRET_MANAGER));
    }

    @Override
    public Collection<OperationModel> qldbSetup(){
        try {
            List<RotatingSaltProvider.SaltSnapshot> snapshots = saltProvider.getSnapshots();
            RotatingSaltProvider.SaltSnapshot lastSnapshot = snapshots.get(snapshots.size() - 1);

            JsonObject jo = toJson(lastSnapshot);

            Collection<OperationModel> newModels = new HashSet<>();
            newModels.add(new OperationModel(Type.SALT, "singleton", null,
                    DigestUtils.sha256Hex(jo.toString()), null));
            return newModels;
        } catch (Exception e) {
            e.printStackTrace();
            return new HashSet<>();
        }
    }

    @Override
    public Type tableType(){
        return Type.SALT;
    }

    private List<OperationModel> handleSaltSnapshots(RoutingContext rc) {
        try {
            final JsonArray ja = new JsonArray();
            this.saltProvider.getSnapshots().stream()
                    .forEachOrdered(s -> ja.add(toJson(s)));

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(ja.encode());
            return Collections.singletonList(new OperationModel(Type.SALT, null, Actions.LIST, null, "listed salt snapshots"));
        } catch (Exception e) {
            rc.fail(500, e);
            return null;
        }
    }

    private List<OperationModel> handleSaltRotate(RoutingContext rc) {
        try {
            final Optional<Double> fraction = RequestUtil.getDouble(rc, "fraction");
            if (!fraction.isPresent()) return null;
            final Duration[] minAges = RequestUtil.getDurations(rc, "min_ages_in_seconds");
            if (minAges == null) return null;

            // force refresh
            this.saltProvider.loadContent();

            final List<RotatingSaltProvider.SaltSnapshot> snapshots = this.saltProvider.getSnapshots();
            final RotatingSaltProvider.SaltSnapshot lastSnapshot = snapshots.get(snapshots.size() - 1);
            final ISaltRotation.Result result = saltRotation.rotateSalts(
                    lastSnapshot, minAges, fraction.get());
            if (!result.hasSnapshot()) {
                ResponseUtil.error(rc, 200, result.getReason());
                return null;
            }

            storageManager.uploadSalts(this.saltProvider, result.getSnapshot());

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(toJson(result.getSnapshot()).encode());
            return Collections.singletonList(new OperationModel(Type.SALT, "singleton", Actions.UPDATE,
                    DigestUtils.sha256Hex(toJson(result.getSnapshot()).toString()), "rotated indices " +
                    result.getRotationIndices().stream().map(String::valueOf).collect(Collectors.joining(","))));
        } catch (Exception e) {
            rc.fail(500, e);
            return null;
        }
    }

    private JsonObject toJson(RotatingSaltProvider.SaltSnapshot snapshot) {
        JsonObject jo = new JsonObject();
        jo.put("effective", snapshot.getEffective().toEpochMilli());
        jo.put("expires", snapshot.getExpires().toEpochMilli());
        jo.put("salts_count", snapshot.getAllRotatingSalts().length);
        jo.put("min_last_updated", Arrays.stream(snapshot.getAllRotatingSalts())
                .map(SaltEntry::getLastUpdated)
                .min(Long::compare).orElse(null));
        jo.put("max_last_updated", Arrays.stream(snapshot.getAllRotatingSalts())
                .map(SaltEntry::getLastUpdated)
                .max(Long::compare).orElse(null));
        return jo;
    }
}
