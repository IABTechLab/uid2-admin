package com.uid2.admin.vertx.api;

import com.google.inject.Inject;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import lombok.val;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class V2Router {
    private static final Logger LOGGER = LoggerFactory.getLogger(V2Router.class);
    private final Set<IRouteProvider> routerProviders;

    @Inject
    public V2Router(Set<IRouteProvider> routerProviders) {
        this.routerProviders = routerProviders;
    }

    public Router createRouter(Vertx verticle) {
        val v2router = Router.router(verticle);

        for (IRouteProvider provider : routerProviders) {
            LOGGER.info("Configuring v2 router with " + provider.getClass());
            provider.setupRoutes(v2router);
        }

        return v2router;
    }
}
