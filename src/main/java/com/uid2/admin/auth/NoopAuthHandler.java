package com.uid2.admin.auth;

import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthenticationHandler;

public class NoopAuthHandler implements AuthenticationHandler {
    @Override
    public void handle(RoutingContext routingContext) {
        routingContext.next();
    }
}
