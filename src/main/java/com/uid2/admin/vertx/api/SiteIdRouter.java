package com.uid2.admin.vertx.api;

import com.google.common.collect.Streams;
import com.google.inject.Inject;
import com.uid2.admin.vertx.ResponseUtil;
import com.uid2.admin.vertx.service.ClientSideKeypairService;
import com.uid2.shared.auth.Role;
import com.uid2.shared.middleware.AuthMiddleware;
import io.vertx.core.Handler;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.val;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SiteIdRouter implements IRouteProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(SiteIdRouter.class);

    private final ClientSideKeypairService clientSideKeypairService;
    private final AuthMiddleware auth;

    @Inject
    public SiteIdRouter(ClientSideKeypairService clientSideKeypairService, AuthMiddleware auth) {
        this.clientSideKeypairService = clientSideKeypairService;
        this.auth = auth;
    }

    @FunctionalInterface
    interface ISiteIdRouteHandler {
        void handle(RoutingContext rc, int siteId);
    }
    private Handler<RoutingContext> checkAuth(Handler<RoutingContext> handler, Role... roles) {
        return auth.handle(handler, roles);
    }
    private Handler<RoutingContext> provideSiteId(ISiteIdRouteHandler handler) {
        return (RoutingContext rc) -> {
            val siteId = Integer.parseInt(rc.pathParam("siteId"));
            handler.handle(rc, siteId);
        };
    }

    @Override
    public void setupRoutes(Router router) {
        router.get("/sites/:siteId/client-side-keys").handler(checkAuth(provideSiteId(this::handleGetClientSideKeys), Role.ADMINISTRATOR));
    }


    public void handleGetClientSideKeys(RoutingContext rc, int siteId) {
        val keypairs = clientSideKeypairService.getKeypairsBySite(siteId);
        if (keypairs != null) {
            val result = Streams.stream(keypairs)
                    .map(kp -> ClientSideKeypairResponse.fromClientSiteKeypair(kp))
                    .toArray(ClientSideKeypairResponse[]::new);
            rc.json(result);
        }
        else {
            ResponseUtil.error(rc, 404, "No keypairs available for site ID: " + siteId);
        }
    }
}