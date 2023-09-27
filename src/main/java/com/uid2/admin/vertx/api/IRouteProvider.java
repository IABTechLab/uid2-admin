package com.uid2.admin.vertx.api;

import io.vertx.core.Handler;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/*
 Implement this interface to automatically be picked up by V2Router and have your routes registered under /v2api/*.
 Any constructor dependencies which are registered should be auto-injected by Guice, as long as it knows about them.
*/
public interface IRouteProvider {
    Handler<RoutingContext> getHandler();
}


