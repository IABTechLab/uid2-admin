package com.uid2.admin.vertx.api;

import io.vertx.core.Handler;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/*
 Implement this interface to automatically be picked up by V2Router and have your routes registered under /v2api/.
 Any constructor dependencies which are registered should be auto-injected by Guice, as long as it knows about them.
 You must have a constructor marked with @Inject for DI to use it.

 *Important*
 If you implement this interface, your route will be registered as a non-blocking handler. Use IBlockingRouteProvider
 instead if you want to provide a blocking handler. See `readme.md` for more information.
 */
public interface IRouteProvider {
    Handler<RoutingContext> getHandler();
}


