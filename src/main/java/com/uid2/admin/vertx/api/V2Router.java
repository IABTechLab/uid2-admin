package com.uid2.admin.vertx.api;

import com.uid2.admin.auth.AdminAuthMiddleware;
import com.uid2.admin.vertx.api.annotations.Method;
import com.uid2.admin.vertx.api.annotations.Path;
import com.uid2.admin.vertx.api.annotations.Roles;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import lombok.val;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
                val authWrappedHandler = auth.handle(provider.getHandler(), roles);
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
