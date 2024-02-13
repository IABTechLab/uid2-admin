package com.uid2.admin.vertx.service;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.auth.AdminAuthMiddleware;
import com.uid2.admin.store.writer.EnclaveStoreWriter;
import com.uid2.admin.vertx.JsonUtil;
import com.uid2.admin.vertx.RequestUtil;
import com.uid2.admin.vertx.ResponseUtil;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.auth.EnclaveIdentifierProvider;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.EnclaveIdentifier;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class EnclaveIdService implements IService {
    private final AdminAuthMiddleware auth;
    private final WriteLock writeLock;
    private final EnclaveStoreWriter storeWriter;
    private final EnclaveIdentifierProvider enclaveIdProvider;
    private final ObjectWriter jsonWriter = JsonUtil.createJsonWriter();

    public EnclaveIdService(AdminAuthMiddleware auth,
                            WriteLock writeLock,
                            EnclaveStoreWriter storeWriter,
                            EnclaveIdentifierProvider enclaveIdProvider) {
        this.auth = auth;
        this.writeLock = writeLock;
        this.storeWriter = storeWriter;
        this.enclaveIdProvider = enclaveIdProvider;
    }

    @Override
    public void setupRoutes(Router router) {
        router.get("/api/enclave/metadata").handler(
                auth.handle(this::handleEnclaveMetadata, Role.OPERATOR_MANAGER));
        router.get("/api/enclave/list").handler(
                auth.handle(this::handleEnclaveList, Role.OPERATOR_MANAGER));

        router.post("/api/enclave/add").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleEnclaveAdd(ctx);
            }
        }, Role.OPERATOR_MANAGER));
        router.post("/api/enclave/del").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleEnclaveDel(ctx);
            }
        }, Role.ADMINISTRATOR));
    }

    private void handleEnclaveMetadata(RoutingContext rc) {
        try {
            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(enclaveIdProvider.getMetadata().encode());
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleEnclaveList(RoutingContext rc) {
        try {
            JsonArray ja = new JsonArray();
            Collection<EnclaveIdentifier> collection = this.enclaveIdProvider.getAll();
            for (EnclaveIdentifier e : collection) {
                JsonObject jo = new JsonObject();
                ja.add(jo);

                jo.put("name", e.getName());
                jo.put("protocol", e.getProtocol());
                jo.put("identifier", e.getIdentifier());
                jo.put("created", e.getCreated());
            }

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(jsonWriter.writeValueAsString(collection));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleEnclaveAdd(RoutingContext rc) {
        try {
            // refresh manually
            enclaveIdProvider.loadContent(enclaveIdProvider.getMetadata());

            final String name = rc.queryParam("name").get(0);
            Optional<EnclaveIdentifier> existingEnclaveId = this.enclaveIdProvider.getAll()
                    .stream().filter(e -> e.getName().equals(name))
                    .findFirst();
            if (existingEnclaveId.isPresent()) {
                ResponseUtil.error(rc, 400, "enclave existed");
                return;
            }

            String protocol = RequestUtil.validateOperatorProtocol(rc.queryParam("protocol").get(0));
            if (protocol == null) {
                ResponseUtil.error(rc, 400, "no protocol specified");
                return;
            }

            final String enclaveId = rc.queryParam("enclave_id").get(0);
            if (enclaveId == null) {
                ResponseUtil.error(rc, 400, "enclave_id not specified");
                return;
            }

            List<EnclaveIdentifier> enclaveIds = this.enclaveIdProvider.getAll()
                    .stream().sorted((a, b) -> (int) (a.getCreated() - b.getCreated()))
                    .collect(Collectors.toList());

            // create new enclave id
            long created = Instant.now().getEpochSecond();
            EnclaveIdentifier newEnclaveId = new EnclaveIdentifier(name, protocol, enclaveId, created);

            // add enclave id to the array
            enclaveIds.add(newEnclaveId);

            // upload to storage
            storeWriter.upload(enclaveIds);

            // respond with new enclave id
            rc.response().end(jsonWriter.writeValueAsString(newEnclaveId));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleEnclaveDel(RoutingContext rc) {
        try {
            // refresh manually
            enclaveIdProvider.loadContent(enclaveIdProvider.getMetadata());

            final String name = rc.queryParam("name").get(0);
            Optional<EnclaveIdentifier> existingEnclaveId = this.enclaveIdProvider.getAll()
                    .stream().filter(e -> e.getName().equals(name))
                    .findFirst();
            if (!existingEnclaveId.isPresent()) {
                ResponseUtil.error(rc, 404, "enclave id not found");
                return;
            }

            List<EnclaveIdentifier> enclaveIds = this.enclaveIdProvider.getAll()
                    .stream().sorted((a, b) -> (int) (a.getCreated() - b.getCreated()))
                    .collect(Collectors.toList());

            // delete client from the array
            EnclaveIdentifier e = existingEnclaveId.get();
            enclaveIds.remove(e);

            // upload to storage
            storeWriter.upload(enclaveIds);

            // respond with the deleted enclave id
            rc.response().end(jsonWriter.writeValueAsString(e));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }
}
