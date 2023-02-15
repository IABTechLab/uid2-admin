package com.uid2.admin.vertx;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;

public class RedirectToRootHandler implements Handler<RoutingContext> {
    @Override
    public void handle(RoutingContext ctx) {
        ctx.session().destroy();
        ctx.response().setStatusCode(302)
                .putHeader(HttpHeaders.LOCATION, "/")
                .end("Redirecting to /");
    }
}
