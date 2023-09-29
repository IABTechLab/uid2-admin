package com.uid2.admin.vertx.api;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import lombok.val;

public class UrlParameterProviders {
    @FunctionalInterface
    public interface ISiteIdRouteHandler {
        void handle(RoutingContext rc, int siteId);
    }
    public static Handler<RoutingContext> provideSiteId(ISiteIdRouteHandler handler) {
        return (RoutingContext rc) -> {
            val siteId = Integer.parseInt(rc.pathParam("siteId"));
            handler.handle(rc, siteId);
        };
    }
}
