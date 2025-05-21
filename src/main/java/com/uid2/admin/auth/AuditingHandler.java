package com.uid2.admin.auth;
import com.uid2.shared.auth.Role;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.time.Instant;

public class AuditingHandler {
    private final AdminAuthMiddleware auth;

    public AuditingHandler(AdminAuthMiddleware auth) {
        this.auth = auth;
    }

    public Handler<RoutingContext> handle(Handler<RoutingContext> handler, Role... roles) {
        return auth.handle(logAndHandle(handler), roles);
    }

    private Handler<RoutingContext> logAndHandle(Handler<RoutingContext> handler) {
        return ctx -> {
            long startTime = System.currentTimeMillis();

                ctx.addBodyEndHandler(v -> {
                    long durationMs = System.currentTimeMillis() - startTime;
                    String method = ctx.request().method().name();
                    String path = ctx.request().path();
                    int status = ctx.response().getStatusCode();
                    String userAgent = ctx.request().getHeader("User-Agent");
                    String ip = ctx.request().remoteAddress().host();
                    String requestId = ctx.request().getHeader("X-Amzn-Trace-Id");
                    JsonObject userDetails = ctx.get("userDetails"); // assuming you store this somewhere in context
                    String requestBody = ctx.getBodyAsString();
                    String queryParams = ctx.request().query();
                    MultiMap queryParamsMap = ctx.request().params();
                    JsonObject queryParamsJson = new JsonObject();
                    queryParamsMap.forEach(entry -> queryParamsJson.put(entry.getKey(), entry.getValue()));

                    if ("POST".equals(method) || "PUT".equals(method)) {
                        userDetails.put("body", requestBody);
                    }
                    // Structured JSON log
                    JsonObject auditLog = new JsonObject()
                            .put("timestamp", Instant.now().toString())
                            .put("method", method)
                            .put("endpoint", path)
                            .put("status", status)
                            .put("duration_ms", durationMs)
                            .put("request_id", requestId != null ? requestId : "ABUTEST")
                            .put("ip", ip)
                            .put("user_agent", userAgent)
                            .put("user", userDetails)
                            .put("params", queryParamsJson);

                    //logger.info(auditLog.encode());
                    System.out.println(auditLog.toString());
                });
                handler.handle(ctx);
        };
    }
}
