package com.uid2.admin.vertx.service;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.model.Site;
import com.uid2.admin.secret.IKeyGenerator;
import com.uid2.admin.store.reader.RotatingSiteStore;
import com.uid2.admin.store.writer.OperatorKeyStoreWriter;
import com.uid2.admin.vertx.JsonUtil;
import com.uid2.admin.vertx.RequestUtil;
import com.uid2.admin.vertx.ResponseUtil;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.auth.*;
import com.uid2.shared.middleware.AuthMiddleware;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class OperatorKeyService implements IService {
    private static final Logger LOGGER = LoggerFactory.getLogger(OperatorKeyService.class);

    private final AuthMiddleware auth;
    private final WriteLock writeLock;
    private final OperatorKeyStoreWriter operatorKeyStoreWriter;
    private final RotatingOperatorKeyProvider operatorKeyProvider;
    private final RotatingSiteStore siteProvider;
    private final IKeyGenerator keyGenerator;
    private final ObjectWriter jsonWriter = JsonUtil.createJsonWriter();
    private final String operatorKeyPrefix;

    public OperatorKeyService(JsonObject config,
                              AuthMiddleware auth,
                              WriteLock writeLock,
                              OperatorKeyStoreWriter operatorKeyStoreWriter,
                              RotatingOperatorKeyProvider operatorKeyProvider,
                              RotatingSiteStore siteProvider,
                              IKeyGenerator keyGenerator) {
        this.auth = auth;
        this.writeLock = writeLock;
        this.operatorKeyStoreWriter = operatorKeyStoreWriter;
        this.operatorKeyProvider = operatorKeyProvider;
        this.siteProvider = siteProvider;
        this.keyGenerator = keyGenerator;

        this.operatorKeyPrefix = config.getString("operator_key_prefix");
    }

    @Override
    public void setupRoutes(Router router) {
        router.get("/api/operator/metadata").handler(
                auth.handle(this::handleOperatorMetadata, Role.OPERATOR_MANAGER));
        router.get("/api/operator/list").handler(
                auth.handle(this::handleOperatorList, Role.OPERATOR_MANAGER));
        router.get("/api/operator/reveal").handler(
                auth.handle(this::handleOperatorReveal, Role.OPERATOR_MANAGER));

        router.post("/api/operator/add").blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleOperatorAdd(ctx);
            }
        }, Role.OPERATOR_MANAGER));

        router.post("/api/operator/del").blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleOperatorDel(ctx);
            }
        }, Role.ADMINISTRATOR));

        router.post("/api/operator/disable").blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleOperatorDisable(ctx);
            }
        }, Role.OPERATOR_MANAGER));

        router.post("/api/operator/enable").blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleOperatorEnable(ctx);
            }
        }, Role.OPERATOR_MANAGER));

        router.post("/api/operator/update").blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleOperatorUpdate(ctx);
            }
        }, Role.ADMINISTRATOR));

        router.post("/api/operator/rekey").blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleOperatorRekey(ctx);
            }
        }, Role.ADMINISTRATOR));

        router.post("/api/operator/roles").blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleOperatorRoles(ctx);
            }
        }, Role.OPERATOR_MANAGER));
    }

    private void handleOperatorMetadata(RoutingContext ctx) {
        try {
            ctx.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(operatorKeyProvider.getMetadata().encode());
        } catch (Exception e) {
            ctx.fail(500, e);
        }
    }

    private void handleOperatorList(RoutingContext ctx) {
        try {
            final JsonArray ja = new JsonArray();
            final Collection<OperatorKey> collection = this.operatorKeyProvider.getAll();
            for (OperatorKey o : collection) {
                final JsonObject jo = new JsonObject();
                ja.add(jo);

                jo.put("name", o.getName());
                jo.put("contact", o.getContact());
                jo.put("roles", RequestUtil.getRolesSpec(o.getRoles()));
                jo.put("protocol", o.getProtocol());
                jo.put("created", o.getCreated());
                jo.put("disabled", o.isDisabled());
                jo.put("site_id", o.getSiteId());
                jo.put("operator_type", o.getOperatorType());
            }

            ctx.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(ja.encode());
        } catch (Exception e) {
            ctx.fail(500, e);
        }
    }

    private void handleOperatorReveal(RoutingContext ctx) {
        try {
            final String name = ctx.queryParam("name").get(0);
            Optional<OperatorKey> existingOperator = this.operatorKeyProvider.getAll()
                    .stream().filter(o -> o.getName().equals(name))
                    .findFirst();
            if (!existingOperator.isPresent()) {
                ResponseUtil.error(ctx, 404, "operator not exist");
                return;
            }

            ctx.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(jsonWriter.writeValueAsString(existingOperator.get()));
        } catch (Exception e) {
            ctx.fail(500, e);
        }
    }

    private void handleOperatorAdd(RoutingContext ctx) {
        try {
            // refresh manually
            operatorKeyProvider.loadContent(operatorKeyProvider.getMetadata());

            if (!ctx.queryParams().contains("name")) {
                ResponseUtil.error(ctx, 400, "no name specified");
                return;
            }
            final String name = ctx.queryParam("name").get(0);

            final Optional<OperatorKey> existingOperator = this.operatorKeyProvider.getAll()
                    .stream().filter(o -> o.getName().equals(name))
                    .findFirst();
            if (existingOperator.isPresent()) {
                ResponseUtil.error(ctx, 400, "key existed");
                return;
            }

            final String protocol = ctx.queryParams().contains("protocol")
                    ? RequestUtil.validateOperatorProtocol(ctx.queryParam("protocol").get(0))
                    : null;
            if (protocol == null) {
                ResponseUtil.error(ctx, 400, "no protocol specified");
                return;
            }
            Set<Role> roles;
            if (!ctx.queryParams().contains("roles")) {
                roles = new HashSet<>();
            } else {
                roles = RequestUtil.getRoles(ctx.queryParam("roles").get(0)) == null
                        ? new HashSet<>() // If roles are not specified in the request, we are still able to add new operator key
                        : RequestUtil.getRoles(ctx.queryParam("roles").get(0));
            }
            if (roles == null) {
                ResponseUtil.error(ctx, 400, "Incorrect roles specified");
                return;
            }

            Integer siteId;
            try {
                siteId = ctx.queryParam("site_id").get(0) == null ? null : Integer.parseInt(ctx.queryParam("site_id").get(0));
            } catch (NumberFormatException e) {
                LOGGER.error(e.getMessage(), e);
                siteId = null;
            }
            if (siteId == null) {
                ResponseUtil.error(ctx, 400, "no site id specified");
                return;
            }
            Integer finalSiteId = siteId;
            if (this.siteProvider.getAllSites().stream().noneMatch(site -> site.getId() == finalSiteId)) {
                ResponseUtil.error(ctx, 400, "provided site id does not exist");
                return;
            }

            OperatorType operatorType;
            try {
                operatorType = OperatorType.valueOf(ctx.queryParam("operator_type").get(0).toUpperCase());
            } catch (Exception e) {
                ResponseUtil.error(ctx, 400, "Operator type must be either public or private");
                return;
            }

            final List<OperatorKey> operators = this.operatorKeyProvider.getAll()
                    .stream().sorted((a, b) -> (int) (a.getCreated() - b.getCreated()))
                    .collect(Collectors.toList());

            // create a random key
            String key = keyGenerator.generateRandomKeyString(32);
            if (this.operatorKeyPrefix != null) key = this.operatorKeyPrefix + key;

            // add new client to array
            long created = Instant.now().getEpochSecond();
            OperatorKey newOperator = new OperatorKey(key, name, name, protocol, created, false, siteId, roles, operatorType);

            // add client to the array
            operators.add(newOperator);

            // upload to storage
            operatorKeyStoreWriter.upload(operators);

            // respond with new key
            ctx.response().end(jsonWriter.writeValueAsString(newOperator));
        } catch (Exception e) {
            ctx.fail(500, e);
        }
    }

    private void handleOperatorDel(RoutingContext ctx) {
        try {
            // refresh manually
            operatorKeyProvider.loadContent(operatorKeyProvider.getMetadata());

            final String name = ctx.queryParam("name").get(0);
            Optional<OperatorKey> existingOperator = this.operatorKeyProvider.getAll()
                    .stream().filter(o -> o.getName().equals(name))
                    .findFirst();
            if (!existingOperator.isPresent()) {
                ResponseUtil.error(ctx, 404, "operator name not found");
                return;
            }

            List<OperatorKey> operators = this.operatorKeyProvider.getAll()
                    .stream().sorted((a, b) -> (int) (a.getCreated() - b.getCreated()))
                    .collect(Collectors.toList());

            // delete client from the array
            OperatorKey o = existingOperator.get();
            operators.remove(o);

            // upload to storage
            operatorKeyStoreWriter.upload(operators);

            // respond with client deleted
            ctx.response().end(jsonWriter.writeValueAsString(o));
        } catch (Exception e) {
            ctx.fail(500, e);
        }
    }

    private void handleOperatorDisable(RoutingContext ctx) {
        handleOperatorDisable(ctx, true);
    }

    private void handleOperatorEnable(RoutingContext ctx) {
        handleOperatorDisable(ctx, false);
    }

    private void handleOperatorDisable(RoutingContext ctx, boolean disableFlag) {
        try {
            // refresh manually
            operatorKeyProvider.loadContent(operatorKeyProvider.getMetadata());

            final String name = ctx.queryParam("name").get(0);
            Optional<OperatorKey> existingOperator = this.operatorKeyProvider.getAll()
                    .stream().filter(o -> o.getName().equals(name))
                    .findFirst();
            if (!existingOperator.isPresent()) {
                ResponseUtil.error(ctx, 404, "operator name not found");
                return;
            }

            List<OperatorKey> operators = this.operatorKeyProvider.getAll()
                    .stream().sorted((a, b) -> (int) (a.getCreated() - b.getCreated()))
                    .collect(Collectors.toList());

            OperatorKey operator = existingOperator.get();
            if (operator.isDisabled() == disableFlag) {
                ResponseUtil.error(ctx, 400, "no change needed");
                return;
            }

            operator.setDisabled(disableFlag);

            JsonObject response = new JsonObject();
            response.put("name", operator.getName());
            response.put("contact", operator.getContact());
            response.put("created", operator.getCreated());
            response.put("disabled", operator.isDisabled());
            response.put("site_id", operator.getSiteId());
            response.put("roles", RequestUtil.getRolesSpec(operator.getRoles()));
            response.put("operator_type", operator.getOperatorType());

            // upload to storage
            operatorKeyStoreWriter.upload(operators);

            // respond with operator disabled/enabled
            ctx.response().end(response.encode());
        } catch (Exception e) {
            ctx.fail(500, e);
        }
    }

    private void handleOperatorUpdate(RoutingContext ctx) {
        try {
            // refresh manually
            operatorKeyProvider.loadContent(operatorKeyProvider.getMetadata());

            final String name = ctx.queryParam("name").get(0);
            OperatorKey existingOperator = this.operatorKeyProvider.getAll()
                    .stream().filter(o -> o.getName().equals(name))
                    .findFirst().orElse(null);
            if (existingOperator == null) {
                ResponseUtil.error(ctx, 404, "operator name not found");
                return;
            }

            if (!ctx.queryParam("site_id").isEmpty()) {
                final Site site = RequestUtil.getSite(ctx, "site_id", this.siteProvider);
                if (site == null) {
                    ResponseUtil.error(ctx, 404, "site id not found");
                    return;
                }
                existingOperator.setSiteId(site.getId());
            }

            if (!ctx.queryParam("operator_type").isEmpty() && ctx.queryParam("operator_type").get(0) != null) {
                OperatorType operatorType;
                try {
                    operatorType = OperatorType.valueOf(ctx.queryParam("operator_type").get(0).toUpperCase());
                } catch (Exception e) {
                    ResponseUtil.error(ctx, 400, "Operator type can only be either public or private");
                    return;
                }

                existingOperator.setOperatorType(operatorType);
            }

            List<OperatorKey> operators = this.operatorKeyProvider.getAll()
                    .stream().sorted((a, b) -> (int) (a.getCreated() - b.getCreated()))
                    .collect(Collectors.toList());

            // upload to storage
            operatorKeyStoreWriter.upload(operators);

            // return the updated client
            ctx.response().end(jsonWriter.writeValueAsString(existingOperator));
        } catch (Exception e) {
            ctx.fail(500, e);
        }
    }

    private void handleOperatorRekey(RoutingContext ctx) {
        try {
            // refresh manually
            operatorKeyProvider.loadContent(operatorKeyProvider.getMetadata());

            final String name = ctx.queryParam("name").get(0);
            Optional<OperatorKey> existingOperator = this.operatorKeyProvider.getAll()
                    .stream().filter(o -> o.getName().equals(name))
                    .findFirst();
            if (!existingOperator.isPresent()) {
                ResponseUtil.error(ctx, 404, "operator key not found");
                return;
            }

            List<OperatorKey> operators = this.operatorKeyProvider.getAll()
                    .stream().sorted((a, b) -> (int) (a.getCreated() - b.getCreated()))
                    .collect(Collectors.toList());

            OperatorKey o = existingOperator.get();
            String newKey = keyGenerator.generateRandomKeyString(32);
            if (this.operatorKeyPrefix != null) newKey = this.operatorKeyPrefix + newKey;
            o.setKey(newKey);

            // upload to storage
            operatorKeyStoreWriter.upload(operators);

            // return client with new key
            ctx.response().end(jsonWriter.writeValueAsString(o));
        } catch (Exception e) {
            ctx.fail(500, e);
        }
    }

    private void handleOperatorRoles(RoutingContext ctx) {
        try {
            // refresh manually
            operatorKeyProvider.loadContent(operatorKeyProvider.getMetadata());

            final String name = ctx.queryParam("name").get(0);
            Optional<OperatorKey> existingOperator = this.operatorKeyProvider.getAll()
                    .stream().filter(o -> o.getName().equals(name))
                    .findFirst();
            if (!existingOperator.isPresent()) {
                ResponseUtil.error(ctx, 404, "operator key not found");
                return;
            }

            Set<Role> roles = !ctx.queryParams().contains("roles")
                    || RequestUtil.getRoles(ctx.queryParam("roles").get(0)) == null
                    ? null
                    : RequestUtil.getRoles(ctx.queryParam("roles").get(0));
            if (roles == null) {
                ResponseUtil.error(ctx, 400, "No roles or incorrect roles specified");
                return;
            }

            List<OperatorKey> operators = this.operatorKeyProvider.getAll()
                    .stream().sorted((a, b) -> (int) (a.getCreated() - b.getCreated()))
                    .collect(Collectors.toList());

            OperatorKey o = existingOperator.get();
            o.setRoles(roles);

            // upload to storage
            operatorKeyStoreWriter.upload(operators);

            // return client with new key
            ctx.response().end(jsonWriter.writeValueAsString(o));
        } catch (Exception e) {
            ctx.fail(500, e);
        }
    }
}
