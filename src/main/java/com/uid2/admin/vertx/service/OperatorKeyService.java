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
import com.uid2.admin.audit.AuditMiddleware;
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
import java.util.stream.Collectors;

public class OperatorKeyService implements IService {
    private final AuditMiddleware audit;
    private final AuthMiddleware auth;
    private final WriteLock writeLock;
    private final IStorageManager storageManager;
    private final RotatingOperatorKeyProvider operatorKeyProvider;
    private final IKeyGenerator keyGenerator;
    private final ObjectWriter jsonWriter = JsonUtil.createJsonWriter();
    private final String operatorKeyPrefix;

    public OperatorKeyService(JsonObject config,
                              AuditMiddleware audit,
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
                auth.handle(audit.handle(this::handleOperatorList), Role.OPERATOR_MANAGER));
        router.get("/api/operator/reveal").handler(
                auth.handle(audit.handle(this::handleOperatorReveal), Role.OPERATOR_MANAGER));

        router.post("/api/operator/add").blockingHandler(auth.handle(audit.handle((ctx) -> {
            synchronized (writeLock) {
                return this.handleOperatorAdd(ctx);
            }
        }), Role.OPERATOR_MANAGER));

        router.post("/api/operator/del").blockingHandler(auth.handle(audit.handle((ctx) -> {
            synchronized (writeLock) {
                return this.handleOperatorDel(ctx);
            }
        }), Role.ADMINISTRATOR));

        router.post("/api/operator/disable").blockingHandler(auth.handle(audit.handle((ctx) -> {
            synchronized (writeLock) {
                return this.handleOperatorDisable(ctx);
            }
        }), Role.OPERATOR_MANAGER));

        router.post("/api/operator/enable").blockingHandler(auth.handle(audit.handle((ctx) -> {
            synchronized (writeLock) {
                return this.handleOperatorEnable(ctx);
            }
        }), Role.OPERATOR_MANAGER));

        router.post("/api/operator/rekey").blockingHandler(auth.handle(audit.handle((ctx) -> {
            synchronized (writeLock) {
                return this.handleOperatorRekey(ctx);
            }
        }), Role.ADMINISTRATOR));
    }

    public Collection<OperationModel> backfill(){
        try{
            Collection<OperatorKey> operatorCollection = operatorKeyProvider.getAll();
            Collection<OperationModel> returnList = new HashSet<>();
            for(OperatorKey o : operatorCollection){
                returnList.add(new OperationModel(Type.OPERATOR, o.getName(), null,
                        DigestUtils.sha256Hex(jsonWriter.writeValueAsString(o)), null));
            }
            return returnList;
        }
        catch(Exception e){
            e.printStackTrace();
            return new HashSet<>();
        }
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

    private List<OperationModel> handleOperatorList(RoutingContext rc) {
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

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(ja.encode());
            return Collections.singletonList(new OperationModel(Type.OPERATOR, Constants.DEFAULT_ITEM_KEY, Actions.LIST, null, "list operators"));
        } catch (Exception e) {
            rc.fail(500, e);
            return null;
        }
    }

    private List<OperationModel> handleOperatorReveal(RoutingContext rc) {
        try {
            final String name = rc.queryParam("name").get(0);
            Optional<OperatorKey> existingOperator = this.operatorKeyProvider.getAll()
                    .stream().filter(o -> o.getName().equals(name))
                    .findFirst();
            if (!existingOperator.isPresent()) {
                ResponseUtil.error(rc, 404, "operator not exist");
                return null;
            }

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(jsonWriter.writeValueAsString(existingOperator.get()));
            return Collections.singletonList(new OperationModel(Type.OPERATOR, name, Actions.GET,
                    DigestUtils.sha256Hex(jsonWriter.writeValueAsString(existingOperator.get())), "revealed " + name));
        } catch (Exception e) {
            rc.fail(500, e);
            return null;
        }
    }

    private List<OperationModel> handleOperatorAdd(RoutingContext rc) {
        try {
            // refresh manually
            operatorKeyProvider.loadContent(operatorKeyProvider.getMetadata());

            final String name = rc.queryParam("name").get(0);
            Optional<OperatorKey> existingOperator = this.operatorKeyProvider.getAll()
                    .stream().filter(o -> o.getName().equals(name))
                    .findFirst();
            if (existingOperator.isPresent()) {
                ResponseUtil.error(rc, 400, "key existed");
                return null;
            }

            String protocol = RequestUtil.validateOperatorProtocol(rc.queryParam("protocol").get(0));
            if (protocol == null) {
                ResponseUtil.error(rc, 400, "no protocol specified");
                return null;
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

            // upload to storage
            storageManager.uploadOperatorKeys(operatorKeyProvider, operators);

            // respond with new key
            rc.response().end(jsonWriter.writeValueAsString(newOperator));
            return Collections.singletonList(new OperationModel(Type.OPERATOR, name, Actions.CREATE,
                    DigestUtils.sha256Hex(jsonWriter.writeValueAsString(newOperator)), "created " + name));
        } catch (Exception e) {
            rc.fail(500, e);
            return null;
        }
    }

    private List<OperationModel> handleOperatorDel(RoutingContext rc) {
        try {
            // refresh manually
            operatorKeyProvider.loadContent(operatorKeyProvider.getMetadata());

            final String name = rc.queryParam("name").get(0);
            Optional<OperatorKey> existingOperator = this.operatorKeyProvider.getAll()
                    .stream().filter(o -> o.getName().equals(name))
                    .findFirst();
            if (!existingOperator.isPresent()) {
                ResponseUtil.error(rc, 404, "operator name not found");
                return null;
            }

            List<OperatorKey> operators = this.operatorKeyProvider.getAll()
                    .stream().sorted((a, b) -> (int) (a.getCreated() - b.getCreated()))
                    .collect(Collectors.toList());

            // delete client from the array
            OperatorKey o = existingOperator.get();
            operators.remove(o);

            // upload to storage
            storageManager.uploadOperatorKeys(operatorKeyProvider, operators);

            // respond with client deleted
            rc.response().end(jsonWriter.writeValueAsString(o));
            return Collections.singletonList(new OperationModel(Type.OPERATOR, name, Actions.DELETE,
                    DigestUtils.sha256Hex(jsonWriter.writeValueAsString(o)), "deleted " + name));
        } catch (Exception e) {
            rc.fail(500, e);
            return null;
        }
    }

    private List<OperationModel> handleOperatorDisable(RoutingContext rc) {
        return handleOperatorDisable(rc, true);
    }

    private List<OperationModel> handleOperatorEnable(RoutingContext rc) {
        return handleOperatorDisable(rc, false);
    }

    private List<OperationModel> handleOperatorDisable(RoutingContext rc, boolean disableFlag) {
        try {
            // refresh manually
            operatorKeyProvider.loadContent(operatorKeyProvider.getMetadata());

            final String name = rc.queryParam("name").get(0);
            Optional<OperatorKey> existingOperator = this.operatorKeyProvider.getAll()
                    .stream().filter(o -> o.getName().equals(name))
                    .findFirst();
            if (!existingOperator.isPresent()) {
                ResponseUtil.error(rc, 404, "operator name not found");
                return null;
            }

            List<OperatorKey> operators = this.operatorKeyProvider.getAll()
                    .stream().sorted((a, b) -> (int) (a.getCreated() - b.getCreated()))
                    .collect(Collectors.toList());

            OperatorKey o = existingOperator.get();
            if (o.isDisabled() == disableFlag) {
                ResponseUtil.error(rc, 400, "no change needed");
                return null;
            }

            o.setDisabled(disableFlag);

            JsonObject response = new JsonObject();
            response.put("name", o.getName());
            response.put("contact", o.getContact());
            response.put("created", o.getCreated());
            response.put("disabled", o.isDisabled());

            // upload to storage
            storageManager.uploadOperatorKeys(operatorKeyProvider, operators);

            // respond with operator disabled/enabled
            rc.response().end(response.encode());
            return Collections.singletonList(new OperationModel(Type.OPERATOR, o.getName(), disableFlag ? Actions.DISABLE : Actions.ENABLE,
                    DigestUtils.sha256Hex(jsonWriter.writeValueAsString(o)), (disableFlag ? "disabled " : "enabled ") + o.getName()));
        } catch (Exception e) {
            rc.fail(500, e);
            return null;
        }
    }

    private List<OperationModel> handleOperatorRekey(RoutingContext rc) {
        try {
            // refresh manually
            operatorKeyProvider.loadContent(operatorKeyProvider.getMetadata());

            final String name = rc.queryParam("name").get(0);
            Optional<OperatorKey> existingOperator = this.operatorKeyProvider.getAll()
                    .stream().filter(o -> o.getName().equals(name))
                    .findFirst();
            if (!existingOperator.isPresent()) {
                ResponseUtil.error(rc, 404, "operator key not found");
                return null;
            }

            List<OperatorKey> operators = this.operatorKeyProvider.getAll()
                    .stream().sorted((a, b) -> (int) (a.getCreated() - b.getCreated()))
                    .collect(Collectors.toList());

            OperatorKey o = existingOperator.get();
            String newKey = keyGenerator.generateRandomKeyString(32);
            if (this.operatorKeyPrefix != null) newKey = this.operatorKeyPrefix + newKey;
            o.setKey(newKey);

            // upload to storage
            storageManager.uploadOperatorKeys(operatorKeyProvider, operators);

            // return client with new key
            rc.response().end(jsonWriter.writeValueAsString(o));
            return Collections.singletonList(new OperationModel(Type.OPERATOR, o.getName(), Actions.UPDATE,
                    DigestUtils.sha256Hex(jsonWriter.writeValueAsString(o)), "rekeyed " + o.getName()));
        } catch (Exception e) {
            rc.fail(500, e);
            return null;
        }
    }
}
