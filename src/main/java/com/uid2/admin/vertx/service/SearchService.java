package com.uid2.admin.vertx.service;

import com.uid2.admin.auth.AdminUserProvider;
import com.uid2.admin.vertx.ResponseUtil;
import com.uid2.shared.auth.Role;
import com.uid2.shared.auth.RotatingOperatorKeyProvider;
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
    private static final String QUERY_PARAMETER = "keyOrSecret";
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
        router.get("/api/search").handler(
                auth.handle(this::handleSearch, Role.ADMINISTRATOR));
    }

    private void handleSearch(RoutingContext rc) {
        try {
            if (!rc.queryParams().contains(QUERY_PARAMETER)) {
                ResponseUtil.error(rc, 400, "Invalid parameters");
                return;
            }
            final String queryParamInput = rc.queryParam("keyOrSecret").get(0);
            if (queryParamInput.length() < QUERY_PARAMETER_MIN_LENGTH) {
                ResponseUtil.error(rc, 400, "Parameter too short. Must be 6 or more characters.");
            }

            // the only special character that is a problem in the keys is a space, so replace it.
            // does also accept URL encoded query strings
            final String queryParam = queryParamInput.replace(' ', '+');

            JsonArray clientKeyResults = new JsonArray();
            JsonArray operatorKeyResults = new JsonArray();
            JsonArray adminUserResults = new JsonArray();
            JsonObject results = new JsonObject();
            results.put("ClientKeys", clientKeyResults);
            results.put("OperatorKeys", operatorKeyResults );
            results.put("AdministratorKeys", adminUserResults);

            this.clientKeyProvider.getAll()
                    .stream()
                    .filter(c -> c.getKey().contains(queryParam) || c.getSecret().contains(queryParam))
                    .forEach(clientKeyResults::add);

            this.operatorKeyProvider.getAll()
                    .stream()
                    .filter(o -> o.getKey().contains(queryParam))
                    .forEach(operatorKeyResults::add);

            this.adminUserProvider.getAll()
                    .stream()
                    .filter(a -> a.getKey().contains(queryParam))
                    .forEach(adminUserResults::add);

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(results.encode());
        } catch (Throwable t) {
            LOGGER.error("Error executing search", t);
            rc.fail(500, t);
        }
    }
}
