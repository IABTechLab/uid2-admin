package com.uid2.admin.model;

import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AuditService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuditService.class);

    private static Set<String> flattenToDotNotation(JsonObject json, String parentKey) {
        Set<String> keys = new HashSet<>();

        for (Map.Entry<String, Object> entry : json) {
            String fullKey = parentKey.isEmpty() ? entry.getKey() : parentKey + "." + entry.getKey();
            Object value = entry.getValue();

            if (value instanceof JsonObject) {
                keys.addAll(flattenToDotNotation((JsonObject) value, fullKey));
            } else {
                keys.add(fullKey);
            }
        }

        return keys;
    }

    private static void removeByDotKey(JsonObject json, String dotKey) {
        String[] parts = dotKey.split("\\.");
        JsonObject current = json;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.getValue(parts[i]);
            if (!(next instanceof JsonObject)) {
                return;
            }
            current = (JsonObject) next;
        }
        current.remove(parts[parts.length - 1]);
    }

    private JsonObject filterQueryParams(MultiMap queryParamsMap, List<String> queryParams) {
        JsonObject queryParamsJson = new JsonObject();
        queryParamsMap.forEach(entry -> {
            if ( queryParams.contains(entry.getKey())) {
                queryParamsJson.put(entry.getKey(), entry.getValue());
            }
        });
        return queryParamsJson;
    }

    private JsonObject filterBody(JsonObject bodyJson, List<String> bodyParams) {
        Set<String> allowedKeys = bodyParams != null
                ? new HashSet<>(bodyParams): null;
        if (bodyJson != null && allowedKeys !=null ) {
            Set<String> dotKeys = flattenToDotNotation(bodyJson, "");
            for (String key : dotKeys) {
                if (!allowedKeys.contains(key)) {
                    removeByDotKey(bodyJson, key);
                }
            }
        }
        return bodyJson;
    }

    //move to shared
    public void log(RoutingContext ctx, AuditParams params) {

        JsonObject userDetails = ctx.get("userDetails");
        if (userDetails == null) {
            userDetails = new JsonObject();
        }
        userDetails.put("User-Agent",ctx.request().getHeader("User-Agent") );
        userDetails.put("IP", ctx.request().remoteAddress().host() );

        AuditLog.Builder builder = new AuditLog.Builder(
                ctx.response().getStatusCode(),
                ctx.request().method().name(),
                ctx.request().path(),
                ctx.request().getHeader("X-Amzn-Trace-Id"),
                userDetails
        );

        if (params != null) {
            JsonObject bodyJson = filterBody(ctx.body().asJsonObject(), params.getBodyParams());
            JsonObject queryParamsJson = filterQueryParams(ctx.request().params(), params.getQueryParams());
            if (!queryParamsJson.isEmpty()) {
                builder.queryParams(queryParamsJson);
            }

            if (bodyJson != null && !bodyJson.isEmpty()) {
                builder.requestBody(bodyJson);
            }
        }

        if (ctx.request().getHeader("UID2-Forwarded-Trace-Id") != null) {
            builder.forwardedRequestId(ctx.request().getHeader("UID2-Forwarded-Trace-Id"));
        }

        AuditLog log = builder.build();
        LOGGER.info(log.toString());
    }

}
