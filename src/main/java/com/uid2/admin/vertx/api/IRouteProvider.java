package com.uid2.admin.vertx.api;

import io.vertx.ext.web.Router;

/*
 Implement this interface to automatically be picked up by V2Router and have your routes registered under /v2api/*.
 Any constructor dependencies which are registered should be auto-injected by Guice, as long as it knows about them.
*/
public interface IRouteProvider {
    void setupRoutes(Router router);
}


