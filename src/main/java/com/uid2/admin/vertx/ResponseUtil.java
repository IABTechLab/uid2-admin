package com.uid2.admin.vertx;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

import java.util.HashMap;

public final class ResponseUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResponseUtil.class);

    private ResponseUtil() {
    }

    public static void error(RoutingContext ctx, int statusCode, String message) {
        LOGGER.error(message);

        final JsonObject json = new JsonObject(new HashMap<String, Object>() {
            {
                put("status", "error");
            }
        });
        if (message != null) {
            json.put("message", message);
        }
        ctx.response().setStatusCode(statusCode).putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end(json.encode());
    }
}
