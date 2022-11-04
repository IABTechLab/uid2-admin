package com.uid2.admin.vertx;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.util.HashMap;

public class ResponseUtil {
    public static void error(RoutingContext rc, int statusCode, String message) {
        final JsonObject json = new JsonObject(new HashMap<String, Object>() {
            {
                put("status", "error");
            }
        });
        if (message != null) {
            json.put("message", message);
        }
        rc.response().setStatusCode(statusCode).putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end(json.encode());
    }
}
