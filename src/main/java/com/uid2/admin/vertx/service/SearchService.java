package com.uid2.admin.vertx.service;

import com.uid2.shared.auth.Role;
import com.uid2.shared.middleware.AuthMiddleware;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.http.HttpHeaders;

public class SearchService implements IService{
    private static final Logger LOGGER = LoggerFactory.getLogger(SearchService.class);
    private final AuthMiddleware auth;

    public SearchService(AuthMiddleware auth) {
        this.auth = auth;
    }
    @Override
    public void setupRoutes(Router router) {
        router.get("/api/search/keyOrSecret").handler(
                auth.handle(this::handleSearchKeyOrSecret, Role.ADMINISTRATOR));
    }

    private void handleSearchKeyOrSecret(RoutingContext rc) {
        try {
            LOGGER.debug("Starting Search Key Or Secret");
            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end("OK");
        } catch (Exception ex) {
            // does this go to some handler that removes sensitive info?
            rc.fail(500, ex);
        }
    }
}
