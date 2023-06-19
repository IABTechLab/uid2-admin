package com.uid2.admin.vertx.service;

import com.uid2.admin.vertx.ResponseUtil;
import com.uid2.shared.auth.ClientKey;
import com.uid2.shared.auth.Role;
import com.uid2.shared.middleware.AuthMiddleware;
import com.uid2.shared.store.reader.RotatingClientKeyProvider;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.Optional;

public class SearchService implements IService {
    private static final String queryParameter = "keyOrSecret";

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchService.class);
    private final AuthMiddleware auth;
    private final RotatingClientKeyProvider clientKeyProvider;

    public SearchService(
            AuthMiddleware auth,
            RotatingClientKeyProvider clientKeyProvider) {
        this.auth = auth;
        this.clientKeyProvider = clientKeyProvider;
    }

    @Override
    public void setupRoutes(Router router) {
        router.get("/api/search").handler(
                auth.handle(this::handleSearch, Role.ADMINISTRATOR));
    }

    private void handleSearch(RoutingContext rc) {
        try {
            LOGGER.debug("Starting Search Key Or Secret");
            if (!rc.queryParams().contains(queryParameter)) {
                ResponseUtil.error(rc, 400, "Invalid parameters");
                return;
            }
            final String queryParam = rc.queryParam("keyOrSecret").get(0);
            if (queryParam.length() < 6) {
                ResponseUtil.error(rc, 400, "Parameter too short");
            }

            JsonArray results = new JsonArray();

            Optional<ClientKey> client = this.clientKeyProvider.getAll()
                    .stream().filter(c -> c.getKey().contains(queryParam))
                    .findFirst();
            if (client.isPresent()) {
                results.add(client.get());
            }

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(results.encode());
        } catch (Exception ex) {
            // does this go to some handler that removes sensitive info?
            rc.fail(500, ex);
        }
    }
}
