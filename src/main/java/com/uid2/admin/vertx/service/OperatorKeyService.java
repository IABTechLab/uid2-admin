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

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.Constants;
import com.uid2.admin.audit.Actions;
import com.uid2.admin.audit.IAuditMiddleware;
import com.uid2.admin.audit.OperationModel;
import com.uid2.admin.audit.Type;
import com.uid2.admin.secret.IKeyGenerator;
import com.uid2.admin.store.IStorageManager;
import com.uid2.admin.vertx.JsonUtil;
import com.uid2.admin.vertx.RequestUtil;
import com.uid2.admin.vertx.ResponseUtil;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.auth.Role;
import com.uid2.shared.auth.RotatingOperatorKeyProvider;
import com.uid2.shared.middleware.AuthMiddleware;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.codec.digest.DigestUtils;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class OperatorKeyService implements IService {
    private final IAuditMiddleware audit;
    private final AuthMiddleware auth;
    private final WriteLock writeLock;
    private final IStorageManager storageManager;
    private final RotatingOperatorKeyProvider operatorKeyProvider;
    private final IKeyGenerator keyGenerator;
    private final ObjectWriter jsonWriter = JsonUtil.createJsonWriter();
    private final String operatorKeyPrefix;

    public OperatorKeyService(JsonObject config,
                              IAuditMiddleware audit,
                              AuthMiddleware auth,
                              WriteLock writeLock,
                              IStorageManager storageManager,
                              RotatingOperatorKeyProvider operatorKeyProvider,
                              IKeyGenerator keyGenerator) {
        this.audit = audit;
        this.auth = auth;
        this.writeLock = writeLock;
        this.storageManager = storageManager;
        this.operatorKeyProvider = operatorKeyProvider;
        this.keyGenerator = keyGenerator;

        this.operatorKeyPrefix = config.getString("operator_key_prefix");
    }

    @Override
    public void setupRoutes(Router router) {
        router.get("/api/operator/metadata").handler(
                auth.handle(this::handleOperatorMetadata, Role.OPERATOR_MANAGER));
        router.get("/api/operator/list").handler(
                auth.handle(ctx -> this.handleOperatorList(ctx, audit.handle(ctx)), Role.OPERATOR_MANAGER));
        router.get("/api/operator/reveal").handler(
                auth.handle(ctx -> this.handleOperatorReveal(ctx, audit.handle(ctx)), Role.OPERATOR_MANAGER));

        router.post("/api/operator/add").blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleOperatorAdd(ctx, audit.handle(ctx));
            }
        }, Role.OPERATOR_MANAGER));

        router.post("/api/operator/del").blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleOperatorDel(ctx, audit.handle(ctx));
            }
        }, Role.ADMINISTRATOR));

        router.post("/api/operator/disable").blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleOperatorDisable(ctx, audit.handle(ctx));
            }
        }, Role.OPERATOR_MANAGER));

        router.post("/api/operator/enable").blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleOperatorEnable(ctx, audit.handle(ctx));
            }
        }, Role.OPERATOR_MANAGER));

        router.post("/api/operator/rekey").blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleOperatorRekey(ctx, audit.handle(ctx));
            }
        }, Role.ADMINISTRATOR));
    }

    @Override
    public Collection<OperationModel> qldbSetup(){
        try {
            Collection<OperatorKey> operatorCollection = operatorKeyProvider.getAll();
            Collection<OperationModel> newModels = new HashSet<>();
            for (OperatorKey o : operatorCollection) {
                newModels.add(new OperationModel(Type.OPERATOR, o.getName(), null,
                        DigestUtils.sha256Hex(jsonWriter.writeValueAsString(o)), null));
            }
            return newModels;
        } catch (Exception e) {
            e.printStackTrace();
            return new HashSet<>();
        }
    }

    @Override
    public Type tableType(){
        return Type.OPERATOR;
    }

    private void handleOperatorMetadata(RoutingContext rc) {
        try {
            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(operatorKeyProvider.getMetadata().encode());
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleOperatorList(RoutingContext rc, Function<List<OperationModel>, Boolean> fxn) {
        try {
            JsonArray ja = new JsonArray();
            Collection<OperatorKey> collection = this.operatorKeyProvider.getAll();
            for (OperatorKey o : collection) {
                JsonObject jo = new JsonObject();
                ja.add(jo);

                jo.put("name", o.getName());
                jo.put("contact", o.getContact());
                jo.put("protocol", o.getProtocol());
                jo.put("created", o.getCreated());
                jo.put("disabled", o.isDisabled());
            }

            List<OperationModel> modelList = Collections.singletonList(new OperationModel(Type.OPERATOR,
                    Constants.DEFAULT_ITEM_KEY, Actions.LIST, null, "list operators"));
            if(!fxn.apply(modelList)){
                ResponseUtil.error(rc, 500, "failed");
                return;
            }

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(ja.encode());
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleOperatorReveal(RoutingContext rc, Function<List<OperationModel>, Boolean> fxn) {
        try {
            final String name = rc.queryParam("name").get(0);
            Optional<OperatorKey> existingOperator = this.operatorKeyProvider.getAll()
                    .stream().filter(o -> o.getName().equals(name))
                    .findFirst();
            if (!existingOperator.isPresent()) {
                ResponseUtil.error(rc, 404, "operator not exist");
                return;
            }

            List<OperationModel> modelList =  Collections.singletonList(new OperationModel(Type.OPERATOR, name, Actions.REVEAL,
                    DigestUtils.sha256Hex(jsonWriter.writeValueAsString(existingOperator.get())), "revealed " + name));
            if(!fxn.apply(modelList)){
                ResponseUtil.error(rc, 500, "failed");
                return;
            }

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(jsonWriter.writeValueAsString(existingOperator.get()));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleOperatorAdd(RoutingContext rc, Function<List<OperationModel>, Boolean> fxn) {
        try {
            // refresh manually
            operatorKeyProvider.loadContent(operatorKeyProvider.getMetadata());

            final String name = rc.queryParam("name").get(0);
            Optional<OperatorKey> existingOperator = this.operatorKeyProvider.getAll()
                    .stream().filter(o -> o.getName().equals(name))
                    .findFirst();
            if (existingOperator.isPresent()) {
                ResponseUtil.error(rc, 400, "key existed");
                return;
            }

            String protocol = RequestUtil.validateOperatorProtocol(rc.queryParam("protocol").get(0));
            if (protocol == null) {
                ResponseUtil.error(rc, 400, "no protocol specified");
                return;
            }

            List<OperatorKey> operators = this.operatorKeyProvider.getAll()
                    .stream().sorted((a, b) -> (int) (a.getCreated() - b.getCreated()))
                    .collect(Collectors.toList());

            // create a random key
            String key = keyGenerator.generateRandomKeyString(32);
            if (this.operatorKeyPrefix != null) key = this.operatorKeyPrefix + key;

            // add new client to array
            long created = Instant.now().getEpochSecond();
            OperatorKey newOperator = new OperatorKey(key, name, name, protocol, created, false);

            // add client to the array
            operators.add(newOperator);

            List<OperationModel> modelList = Collections.singletonList(new OperationModel(Type.OPERATOR, name, Actions.CREATE,
                    DigestUtils.sha256Hex(jsonWriter.writeValueAsString(newOperator)), "created " + name));
            if(!fxn.apply(modelList)){
                ResponseUtil.error(rc, 500, "failed");
                return;
            }

            // upload to storage
            storageManager.uploadOperatorKeys(operatorKeyProvider, operators);

            // respond with new key
            rc.response().end(jsonWriter.writeValueAsString(newOperator));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleOperatorDel(RoutingContext rc, Function<List<OperationModel>, Boolean> fxn) {
        try {
            // refresh manually
            operatorKeyProvider.loadContent(operatorKeyProvider.getMetadata());

            final String name = rc.queryParam("name").get(0);
            Optional<OperatorKey> existingOperator = this.operatorKeyProvider.getAll()
                    .stream().filter(o -> o.getName().equals(name))
                    .findFirst();
            if (!existingOperator.isPresent()) {
                ResponseUtil.error(rc, 404, "operator name not found");
                return;
            }

            List<OperatorKey> operators = this.operatorKeyProvider.getAll()
                    .stream().sorted((a, b) -> (int) (a.getCreated() - b.getCreated()))
                    .collect(Collectors.toList());

            // delete client from the array
            OperatorKey o = existingOperator.get();
            operators.remove(o);

            List<OperationModel> modelList = Collections.singletonList(new OperationModel(Type.OPERATOR, name, Actions.DELETE,
                    DigestUtils.sha256Hex(jsonWriter.writeValueAsString(o)), "deleted " + name));
            if(!fxn.apply(modelList)){
                ResponseUtil.error(rc, 500, "failed");
                return;
            }

            // upload to storage
            storageManager.uploadOperatorKeys(operatorKeyProvider, operators);

            // respond with client deleted
            rc.response().end(jsonWriter.writeValueAsString(o));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleOperatorDisable(RoutingContext rc, Function<List<OperationModel>, Boolean> fxn) {
        handleOperatorDisable(rc, fxn, true);
    }

    private void handleOperatorEnable(RoutingContext rc, Function<List<OperationModel>, Boolean> fxn) {
        handleOperatorDisable(rc, fxn, false);
    }

    private void handleOperatorDisable(RoutingContext rc, Function<List<OperationModel>, Boolean> fxn, boolean disableFlag) {
        try {
            // refresh manually
            operatorKeyProvider.loadContent(operatorKeyProvider.getMetadata());

            final String name = rc.queryParam("name").get(0);
            Optional<OperatorKey> existingOperator = this.operatorKeyProvider.getAll()
                    .stream().filter(o -> o.getName().equals(name))
                    .findFirst();
            if (!existingOperator.isPresent()) {
                ResponseUtil.error(rc, 404, "operator name not found");
                return;
            }

            List<OperatorKey> operators = this.operatorKeyProvider.getAll()
                    .stream().sorted((a, b) -> (int) (a.getCreated() - b.getCreated()))
                    .collect(Collectors.toList());

            OperatorKey o = existingOperator.get();
            if (o.isDisabled() == disableFlag) {
                ResponseUtil.error(rc, 400, "no change needed");
                return;
            }

            o.setDisabled(disableFlag);

            JsonObject response = new JsonObject();
            response.put("name", o.getName());
            response.put("contact", o.getContact());
            response.put("created", o.getCreated());
            response.put("disabled", o.isDisabled());

            List<OperationModel> modelList = Collections.singletonList(new OperationModel(Type.OPERATOR, o.getName(), disableFlag ? Actions.DISABLE : Actions.ENABLE,
                    DigestUtils.sha256Hex(jsonWriter.writeValueAsString(o)), (disableFlag ? "disabled " : "enabled ") + o.getName()));
            if(!fxn.apply(modelList)){
                ResponseUtil.error(rc, 500, "failed");
                return;
            }

            // upload to storage
            storageManager.uploadOperatorKeys(operatorKeyProvider, operators);

            // respond with operator disabled/enabled
            rc.response().end(response.encode());
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleOperatorRekey(RoutingContext rc, Function<List<OperationModel>, Boolean> fxn) {
        try {
            // refresh manually
            operatorKeyProvider.loadContent(operatorKeyProvider.getMetadata());

            final String name = rc.queryParam("name").get(0);
            Optional<OperatorKey> existingOperator = this.operatorKeyProvider.getAll()
                    .stream().filter(o -> o.getName().equals(name))
                    .findFirst();
            if (!existingOperator.isPresent()) {
                ResponseUtil.error(rc, 404, "operator key not found");
                return;
            }

            List<OperatorKey> operators = this.operatorKeyProvider.getAll()
                    .stream().sorted((a, b) -> (int) (a.getCreated() - b.getCreated()))
                    .collect(Collectors.toList());

            OperatorKey o = existingOperator.get();
            String newKey = keyGenerator.generateRandomKeyString(32);
            if (this.operatorKeyPrefix != null) newKey = this.operatorKeyPrefix + newKey;
            o.setKey(newKey);

            List<OperationModel> modelList = Collections.singletonList(new OperationModel(Type.OPERATOR, o.getName(), Actions.UPDATE,
                    DigestUtils.sha256Hex(jsonWriter.writeValueAsString(o)), "rekeyed " + o.getName()));
            if(!fxn.apply(modelList)){
                ResponseUtil.error(rc, 500, "failed");
                return;
            }

            // upload to storage
            storageManager.uploadOperatorKeys(operatorKeyProvider, operators);

            // return client with new key
            rc.response().end(jsonWriter.writeValueAsString(o));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }
}
