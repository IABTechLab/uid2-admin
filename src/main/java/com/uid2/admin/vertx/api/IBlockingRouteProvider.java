package com.uid2.admin.vertx.api;

/*
 *Important*
 If you implement this interface, your route will be registered as a blocking handler. Use IRouteProvider
 instead if you want to provide a non-blocking handler. See `readme.md` for more information.
*/
public interface IBlockingRouteProvider extends IRouteProvider {
}
