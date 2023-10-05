package com.uid2.admin.vertx.api;

import io.vertx.core.Handler;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/*
 *Important*
 If you implement this interface, your route will be registered as a non-blocking handler. Use IBlockingRouteProvider
 instead if you want to provide a blocking handler. See `readme.md` for more information.
 */
public interface IRouteProvider {
    Handler<RoutingContext> getHandler();
}


