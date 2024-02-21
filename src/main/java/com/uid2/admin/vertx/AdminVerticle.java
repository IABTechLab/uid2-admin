package com.uid2.admin.vertx;

import com.okta.jwt.Jwt;
import com.uid2.admin.auth.*;
import com.uid2.admin.vertx.api.V2Router;
import com.uid2.admin.vertx.service.IService;
import com.uid2.shared.Const;
import com.uid2.shared.Utils;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.*;
import io.vertx.ext.web.sstore.LocalSessionStore;

import java.util.List;

public class AdminVerticle extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminVerticle.class);

    private final JsonObject config;
    private final AuthProvider authProvider;
    private final IService[] services;
    private final V2Router v2Router;

    public AdminVerticle(JsonObject config,
                         AuthProvider authProvider,
                         IService[] services,
                         V2Router v2Router) {
        this.config = config;
        this.authProvider = authProvider;
        this.services = services;
        this.v2Router = v2Router;
    }

    public void start(Promise<Void> startPromise) {
        final Router router = createRoutesSetup();
        final int portOffset = Utils.getPortOffset();
        final int port = Const.Port.ServicePortForAdmin + portOffset;
        vertx.createHttpServer()
                .requestHandler(router)
                .listen(port)
                .onSuccess(server -> {
                    startPromise.complete();
                    LOGGER.info("AdminVerticle instance started on HTTP port: {}", server.actualPort());
                })
                .onFailure(startPromise::fail);
    }

    private Router createRoutesSetup() {
        final Router router = Router.router(vertx);
        router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)).setSessionTimeout(32400000)); // 9 hr session timeout
        final AuthenticationHandler oktaHandler = this.authProvider.createAuthHandler(vertx, router.route("/oauth2-callback"));
        final TokenRefreshHandler tokenRefreshHandler = new TokenRefreshHandler(this.authProvider.getIdTokenVerifier(), config);

        router.route().handler(BodyHandler.create());
        router.route().handler(StaticHandler.create("webroot"));

        router.route("/login").handler(oktaHandler);
        router.route("/adm/*").handler(oktaHandler);
        router.route("/api/*").handler(tokenRefreshHandler);
        router.route("/api/*").handler(oktaHandler);

        router.get("/login").handler(new RedirectToRootHandler(false));
        router.get("/logout").handler(new RedirectToRootHandler(true));
        router.get("/ops/healthcheck").handler(this::handleHealthCheck);
        router.get("/api/userinfo").handler(this::handleUserinfo);

        for (IService service : this.services) {
            service.setupRoutes(router);
        }

        if (v2Router != null) {
            v2Router.setupSubRouter(vertx, router);
        }

        return router;
    }

    private void handleHealthCheck(RoutingContext rc) {
        rc.response().end("OK");
    }

    private void handleUserinfo(RoutingContext rc) {
        try {
            Jwt idJwt = this.authProvider.getIdTokenVerifier().decode(rc.user().principal().getString("id_token"), null);
            JsonObject jo = new JsonObject();
            List<String> groups = (List<String>) idJwt.getClaims().get("groups");
            jo.put("groups", new JsonArray(groups));
            jo.put("email", idJwt.getClaims().get("email"));
            rc.response().setStatusCode(200).end(jo.toString());
        } catch (Exception e) {
            rc.response().setStatusCode(500).end();
        }
    }

}
