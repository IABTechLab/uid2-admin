package com.uid2.admin.vertx.service;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.auth.AdminAuthMiddleware;
import com.uid2.admin.auth.RevealedKey;
import com.uid2.shared.model.Site;
import com.uid2.shared.secret.IKeyGenerator;
import com.uid2.admin.store.writer.OperatorKeyStoreWriter;
import com.uid2.admin.vertx.JsonUtil;
import com.uid2.admin.vertx.RequestUtil;
import com.uid2.admin.vertx.ResponseUtil;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.auth.*;
import com.uid2.shared.secret.KeyHashResult;
import com.uid2.shared.secret.KeyHasher;
import com.uid2.shared.store.reader.RotatingSiteStore;
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
    private static final ObjectWriter JSON_WRITER = JsonUtil.createJsonWriter();
    private static final Set<Set<Role>> VALID_ROLE_COMBINATIONS = (Set.of(
            new TreeSet<>(Set.of()), // Empty role input is accepted as OPERATOR will be automatically added
            new TreeSet<>(Set.of(Role.OPERATOR)),
            new TreeSet<>(Set.of(Role.OPTOUT)),
            new TreeSet<>(Set.of(Role.OPTOUT_SERVICE)),
            new TreeSet<>(Set.of(Role.OPERATOR, Role.OPTOUT))
    ));

    private final AdminAuthMiddleware auth;
    private final WriteLock writeLock;
    private final OperatorKeyStoreWriter operatorKeyStoreWriter;
    private final RotatingOperatorKeyProvider operatorKeyProvider;
    private final RotatingSiteStore siteProvider;
    private final IKeyGenerator keyGenerator;
    private final KeyHasher keyHasher;
    private final String operatorKeyPrefix;

    public OperatorKeyService(JsonObject config,
                              AdminAuthMiddleware auth,
                              WriteLock writeLock,
                              OperatorKeyStoreWriter operatorKeyStoreWriter,
                              RotatingOperatorKeyProvider operatorKeyProvider,
                              RotatingSiteStore siteProvider,
                              IKeyGenerator keyGenerator,
                              KeyHasher keyHasher) {
        this.auth = auth;
        this.writeLock = writeLock;
        this.operatorKeyStoreWriter = operatorKeyStoreWriter;
        this.operatorKeyProvider = operatorKeyProvider;
        this.siteProvider = siteProvider;
        this.keyGenerator = keyGenerator;
        this.keyHasher = keyHasher;

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

        router.post("/api/operator/add").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleOperatorAdd(ctx);
            }
        }, Role.OPERATOR_MANAGER));

        router.post("/api/operator/del").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleOperatorDel(ctx);
            }
        }, Role.ADMINISTRATOR));

        router.post("/api/operator/disable").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleOperatorDisable(ctx);
            }
        }, Role.OPERATOR_MANAGER));

        router.post("/api/operator/enable").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleOperatorEnable(ctx);
            }
        }, Role.OPERATOR_MANAGER));

        router.post("/api/operator/update").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleOperatorUpdate(ctx);
            }
        }, Role.ADMINISTRATOR));

        router.post("/api/operator/roles").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleOperatorRoles(ctx);
            }
        }, Role.OPERATOR_MANAGER));
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

    private void handleOperatorList(RoutingContext rc) {
        try {
            final JsonArray ja = new JsonArray();
            final Collection<OperatorKey> collection = this.operatorKeyProvider.getAll();
            for (OperatorKey o : collection) {
                final JsonObject jo = new JsonObject();
                ja.add(jo);

                jo.put("key_id", o.getKeyId());
                jo.put("name", o.getName());
                jo.put("contact", o.getContact());
                jo.put("roles", RequestUtil.getRolesSpec(o.getRoles()));
                jo.put("protocol", o.getProtocol());
                jo.put("created", o.getCreated());
                jo.put("disabled", o.isDisabled());
                jo.put("site_id", o.getSiteId());
                jo.put("operator_type", o.getOperatorType());
            }

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(ja.encode());
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleOperatorReveal(RoutingContext rc) {
        try {
            final String name = rc.queryParam("name").get(0);
            Optional<OperatorKey> existingOperator = this.operatorKeyProvider.getAll()
                    .stream().filter(o -> o.getName().equals(name))
                    .findFirst();
            if (existingOperator.isEmpty()) {
                ResponseUtil.error(rc, 404, "operator not exist");
                return;
            }

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(JSON_WRITER.writeValueAsString(existingOperator.get()));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private String[] generateKeyAndKeyId(Integer finalSiteId) throws Exception {
        String keyCommonPrefix = this.operatorKeyPrefix != null ? (this.operatorKeyPrefix + finalSiteId + "-") : "";
        String key = keyCommonPrefix + keyGenerator.generateFormattedKeyString(32);
        String keyId = key.substring(0, keyCommonPrefix.length() + 5);

        // Check if keyId is duplicated
        Optional<OperatorKey> existingOperatorKeyId = this.operatorKeyProvider.getAll()
                .stream().filter(o -> o.getKeyId().equals(keyId))
                .findFirst();
        if (existingOperatorKeyId.isPresent()) {
            return generateKeyAndKeyId(finalSiteId);
        }
        return new String[]{ key, keyId };
    }

    private void handleOperatorAdd(RoutingContext rc) {
        try {
            // refresh manually
            operatorKeyProvider.loadContent(operatorKeyProvider.getMetadata());

            if (!rc.queryParams().contains("name")) {
                ResponseUtil.error(rc, 400, "no name specified");
                return;
            }
            final String name = rc.queryParam("name").get(0);

            final Optional<OperatorKey> existingOperator = this.operatorKeyProvider.getAll()
                    .stream().filter(o -> o.getName().equals(name))
                    .findFirst();
            if (existingOperator.isPresent()) {
                ResponseUtil.error(rc, 400, "key existed");
                return;
            }

            final String protocol = rc.queryParams().contains("protocol")
                    ? RequestUtil.validateOperatorProtocol(rc.queryParam("protocol").get(0))
                    : null;
            if (protocol == null) {
                ResponseUtil.error(rc, 400, "no protocol specified");
                return;
            }

            Set<Role> roles;
            if (!rc.queryParams().contains("roles")) {
                roles = new HashSet<>();
            } else {
                roles = RequestUtil.getRoles(rc.queryParam("roles").get(0)) == null
                        ? new HashSet<>() // If roles are not specified in the request, we are still able to add new operator key
                        : RequestUtil.getRoles(rc.queryParam("roles").get(0));
            }
            if (!validateOperatorRoles(rc, roles)) {
                return;
            }

            Integer siteId;
            try {
                siteId = rc.queryParam("site_id").get(0) == null ? null : Integer.parseInt(rc.queryParam("site_id").get(0));
            } catch (NumberFormatException e) {
                LOGGER.error(e.getMessage(), e);
                siteId = null;
            }
            if (siteId == null) {
                ResponseUtil.error(rc, 400, "no site id specified");
                return;
            }
            Integer finalSiteId = siteId;
            if (this.siteProvider.getAllSites().stream().noneMatch(site -> site.getId() == finalSiteId)) {
                ResponseUtil.error(rc, 400, "provided site id does not exist");
                return;
            }

            OperatorType operatorType;
            try {
                operatorType = OperatorType.valueOf(rc.queryParam("operator_type").get(0).toUpperCase());
            } catch (Exception e) {
                ResponseUtil.error(rc, 400, "Operator type must be either public or private");
                return;
            }

            final List<OperatorKey> operators = this.operatorKeyProvider.getAll()
                    .stream().sorted((a, b) -> (int) (a.getCreated() - b.getCreated()))
                    .collect(Collectors.toList());

            // create a random key
            String[] generatedKeyAndKeyId = generateKeyAndKeyId(finalSiteId);
            String key = generatedKeyAndKeyId[0];
            String keyId = generatedKeyAndKeyId[1];
            KeyHashResult khr = keyHasher.hashKey(key);

            // create new operator
            long created = Instant.now().getEpochSecond();
            OperatorKey newOperator = new OperatorKey(khr.getHash(), khr.getSalt(), name, name, protocol, created, false, siteId, roles, operatorType, keyId);

            // add client to the array
            operators.add(newOperator);

            // upload to storage
            operatorKeyStoreWriter.upload(operators);

            // respond with new key
            rc.response().end(JSON_WRITER.writeValueAsString(new RevealedKey<>(newOperator, key)));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleOperatorDel(RoutingContext rc) {
        try {
            // refresh manually
            operatorKeyProvider.loadContent(operatorKeyProvider.getMetadata());

            final String name = rc.queryParam("name").get(0);
            Optional<OperatorKey> existingOperator = this.operatorKeyProvider.getAll()
                    .stream().filter(o -> o.getName().equals(name))
                    .findFirst();
            if (existingOperator.isEmpty()) {
                ResponseUtil.error(rc, 404, "operator name not found");
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
            rc.response().end(JSON_WRITER.writeValueAsString(o));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleOperatorDisable(RoutingContext rc) {
        handleOperatorDisable(rc, true);
    }

    private void handleOperatorEnable(RoutingContext rc) {
        handleOperatorDisable(rc, false);
    }

    private void handleOperatorDisable(RoutingContext rc, boolean disableFlag) {
        try {
            // refresh manually
            operatorKeyProvider.loadContent(operatorKeyProvider.getMetadata());

            final String name = rc.queryParam("name").get(0);
            Optional<OperatorKey> existingOperator = this.operatorKeyProvider.getAll()
                    .stream().filter(o -> o.getName().equals(name))
                    .findFirst();
            if (existingOperator.isEmpty()) {
                ResponseUtil.error(rc, 404, "operator name not found");
                return;
            }

            List<OperatorKey> operators = this.operatorKeyProvider.getAll()
                    .stream().sorted((a, b) -> (int) (a.getCreated() - b.getCreated()))
                    .collect(Collectors.toList());

            OperatorKey operator = existingOperator.get();
            if (operator.isDisabled() == disableFlag) {
                ResponseUtil.error(rc, 400, "no change needed");
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
            rc.response().end(response.encode());
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleOperatorUpdate(RoutingContext rc) {
        try {
            // refresh manually
            operatorKeyProvider.loadContent(operatorKeyProvider.getMetadata());

            final String name = rc.queryParam("name").get(0);
            OperatorKey existingOperator = this.operatorKeyProvider.getAll()
                    .stream().filter(o -> o.getName().equals(name))
                    .findFirst().orElse(null);
            if (existingOperator == null) {
                ResponseUtil.error(rc, 404, "operator name not found");
                return;
            }

            if (!rc.queryParam("site_id").isEmpty()) {
                final Site site = RequestUtil.getSiteFromParam(rc, "site_id", this.siteProvider);
                if (site == null) {
                    ResponseUtil.error(rc, 404, "site id not found");
                    return;
                }
                existingOperator.setSiteId(site.getId());
            }

            if (!rc.queryParam("operator_type").isEmpty() && rc.queryParam("operator_type").get(0) != null) {
                OperatorType operatorType;
                try {
                    operatorType = OperatorType.valueOf(rc.queryParam("operator_type").get(0).toUpperCase());
                } catch (Exception e) {
                    ResponseUtil.error(rc, 400, "Operator type can only be either public or private");
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
            rc.response().end(JSON_WRITER.writeValueAsString(existingOperator));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleOperatorRoles(RoutingContext rc) {
        try {
            // refresh manually
            operatorKeyProvider.loadContent(operatorKeyProvider.getMetadata());

            final String name = rc.queryParam("name").get(0);
            Optional<OperatorKey> existingOperator = this.operatorKeyProvider.getAll()
                    .stream().filter(o -> o.getName().equals(name))
                    .findFirst();
            if (existingOperator.isEmpty()) {
                ResponseUtil.error(rc, 404, "operator key not found");
                return;
            }

            Set<Role> roles = !rc.queryParams().contains("roles")
                    || RequestUtil.getRoles(rc.queryParam("roles").get(0)) == null
                    ? null
                    : RequestUtil.getRoles(rc.queryParam("roles").get(0));
            if (!validateOperatorRoles(rc, roles)) {
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
            rc.response().end(JSON_WRITER.writeValueAsString(o));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private boolean validateOperatorRoles(RoutingContext rc, Set<Role> roles) {
        if (roles == null) {
            ResponseUtil.error(rc, 400, "Incorrect roles specified");
            return false;
        }
        if (!VALID_ROLE_COMBINATIONS.contains(roles)) {
            ResponseUtil.error(rc, 400, "Invalid role combination for operator key. Must be one of: " + VALID_ROLE_COMBINATIONS);
            return false;
        }

        return true;
    }
}
