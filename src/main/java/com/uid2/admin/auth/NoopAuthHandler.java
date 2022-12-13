package com.uid2.admin.auth;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthHandler;

import java.util.Set;

public class NoopAuthHandler implements AuthHandler {
    @Override
    public AuthHandler addAuthority(String s) {
        return this;
    }

    @Override
    public AuthHandler addAuthorities(Set<String> set) {
        return this;
    }

    @Override
    public void parseCredentials(RoutingContext routingContext, Handler<AsyncResult<JsonObject>> handler) {

    }

    @Override
    public void authorize(User user, Handler<AsyncResult<Void>> handler) {

    }

    @Override
    public void handle(RoutingContext routingContext) {
        routingContext.next();
    }
}

