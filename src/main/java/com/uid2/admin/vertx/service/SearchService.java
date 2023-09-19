package com.uid2.admin.vertx.service;

import com.uid2.admin.auth.AdminUser;
import com.uid2.admin.auth.AdminUserProvider;
import com.uid2.admin.vertx.ResponseUtil;
import com.uid2.shared.auth.*;
import com.uid2.shared.middleware.AuthMiddleware;
import com.uid2.shared.store.reader.RotatingClientKeyProvider;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class SearchService implements IService {
    private static final Integer QUERY_PARAMETER_MIN_LENGTH = 6;

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchService.class);
    private final AuthMiddleware auth;
    private final RotatingClientKeyProvider clientKeyProvider;
    private final RotatingOperatorKeyProvider operatorKeyProvider;
    private final AdminUserProvider adminUserProvider;

    public SearchService(
            AuthMiddleware auth,
            RotatingClientKeyProvider clientKeyProvider,
            RotatingOperatorKeyProvider operatorKeyProvider,
            AdminUserProvider adminUserProvider) {
        this.auth = auth;
        this.clientKeyProvider = clientKeyProvider;
        this.operatorKeyProvider = operatorKeyProvider;
        this.adminUserProvider = adminUserProvider;
    }

    @Override
    public void setupRoutes(Router router) {
        router.post("/api/search").handler(
                auth.handle(this::handleSearch, Role.ADMINISTRATOR));
    }

    private void handleSearch(RoutingContext rc) {
        try {
            // body contains the query
            final String queryParam = rc.body().asString();

            if (queryParam.length() < QUERY_PARAMETER_MIN_LENGTH) {
                ResponseUtil.error(rc, 400, String.format("Parameter too short. Must be %d or more characters.", QUERY_PARAMETER_MIN_LENGTH));
            }

            JsonArray clientKeyResults = new JsonArray();
            JsonArray operatorKeyResults = new JsonArray();
            JsonArray adminUserResults = new JsonArray();
            JsonObject results = new JsonObject();
            results.put("ClientKeys", clientKeyResults);
            results.put("OperatorKeys", operatorKeyResults);
            results.put("AdministratorKeys", adminUserResults);
            
            ClientKey clientKey = this.clientKeyProvider.getClientKey(queryParam);
            OperatorKey operatorKey = this.operatorKeyProvider.getOperatorKey(queryParam);
            AdminUser adminKey = this.adminUserProvider.getAdminUserFromKey(queryParam);

            this.clientKeyProvider.getAll()
                    .stream()
                    .filter(c -> c.getSecret().contains(queryParam))
                    .forEach(clientKeyResults::add);

            if (clientKey != null) {
                this.clientKeyProvider.getAll()
                        .stream()
                        .filter(c -> c.getKeyHash().contains(clientKey.getKeyHash()))
                        .forEach(clientKeyResults::add);
            }

            if (operatorKey != null) {
                this.operatorKeyProvider.getAll()
                        .stream()
                        .filter(o -> o.getKeyHash().contains(operatorKey.getKeyHash()))
                        .forEach(operatorKeyResults::add);
            }

            if (adminKey != null) {
                this.adminUserProvider.getAll()
                        .stream()
                        .filter(a -> a.getKeyHash().contains(adminKey.getKeyHash()))
                        .forEach(adminUserResults::add);
            }

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(results.encode());
        } catch (Throwable t) {
            LOGGER.error("Error executing search", t);
            rc.fail(500, t);
        }
    }
}
