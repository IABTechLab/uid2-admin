package com.uid2.admin.vertx.api;

import com.uid2.admin.auth.AdminAuthMiddleware;
import com.uid2.admin.vertx.api.annotations.Method;
import com.uid2.admin.vertx.api.annotations.Path;
import com.uid2.admin.vertx.api.annotations.Roles;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.val;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;

public class V2Router {
    private static final Logger LOGGER = LoggerFactory.getLogger(V2Router.class);
    private final IRouteProvider[] routeProviders;
    private final AdminAuthMiddleware auth;

    public V2Router(IRouteProvider[] routeProviders, AdminAuthMiddleware auth) {
        this.routeProviders = routeProviders;
        this.auth = auth;
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


    public void setupSubRouter(Vertx verticle, Router parentRouter) {
        val v2router = Router.router(verticle);

        for (IRouteProvider provider : routeProviders) {
            LOGGER.info("Configuring v2 router with " + provider.getClass());
            java.lang.reflect.Method handler = null;
            try {
                handler = provider.getClass().getMethod("getHandler");
                val path = handler.getAnnotation(Path.class).value();
                val method = handler.getAnnotation(Method.class).value().vertxMethod;
                val roles = handler.getAnnotation(Roles.class).value();
                val rawHandler = provider.getHandler();
                val loggedHandler = logAndHandle(rawHandler);
                val authWrappedHandler = auth.handle(loggedHandler, roles);
                if (Arrays.stream(provider.getClass().getInterfaces()).anyMatch(iface -> iface == IBlockingRouteProvider.class)) {
                    LOGGER.info("Using blocking handler for " + provider.getClass().getName());
                    v2router.route(method, path).blockingHandler(authWrappedHandler);
                }
                else {
                    LOGGER.info("Using non-blocking handler for " + provider.getClass().getName());
                    v2router.route(method, path).handler(authWrappedHandler);
                }
            } catch (NoSuchMethodException e) {
                LOGGER.error("Could not find handle method for API handler: " + provider.getClass().getName());
            }
        }

        parentRouter.route("/api/v2/*").subRouter(v2router);
    }
}
