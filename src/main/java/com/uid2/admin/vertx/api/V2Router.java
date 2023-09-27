package com.uid2.admin.vertx.api;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.uid2.admin.vertx.api.annotations.Method;
import com.uid2.admin.vertx.api.annotations.Path;
import com.uid2.admin.vertx.api.annotations.Roles;
import com.uid2.shared.middleware.AuthMiddleware;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.val;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

@Singleton
public class V2Router {
    private static final Logger LOGGER = LoggerFactory.getLogger(V2Router.class);
    private final Set<IRouteProvider> routeProviders;
    private final AuthMiddleware auth;

    @Inject
    public V2Router(Set<IRouteProvider> routeProviders, AuthMiddleware auth) {
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
                v2router.route(method, path).handler(auth.handle(provider.getHandler(), roles));
            } catch (NoSuchMethodException e) {
                LOGGER.error("Could not find handle method for API handler: " + provider.getClass().getName());
            }
        }

        parentRouter.route("/v2api/*").subRouter(v2router);
    }
}
