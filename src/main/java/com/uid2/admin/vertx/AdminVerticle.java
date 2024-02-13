package com.uid2.admin.vertx;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.okta.jwt.Jwt;
import com.uid2.admin.auth.*;
import com.uid2.admin.vertx.api.V2Router;
import com.uid2.admin.vertx.service.IService;
import com.uid2.shared.Const;
import com.uid2.shared.Utils;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.*;
import io.vertx.ext.web.sstore.LocalSessionStore;

import static com.uid2.admin.auth.AuthUtil.isAuthDisabled;

public class AdminVerticle extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminVerticle.class);

    private final JsonObject config;
    private final AuthProvider authProvider;
    private final IAdminUserProvider adminUserProvider;
    private final IService[] services;
    private final V2Router v2Router;
    private final ObjectWriter jsonWriter = JsonUtil.createJsonWriter();

    public AdminVerticle(JsonObject config,
                         AuthProvider authProvider,
                         IAdminUserProvider adminUserProvider,
                         IService[] services,
                         V2Router v2Router) {
        this.config = config;
        this.authProvider = authProvider;
        this.adminUserProvider = adminUserProvider;
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
        router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)));
        final AuthenticationHandler oktaHandler = this.authProvider.createAuthHandler(vertx, router.route("/oauth2-callback"));
        final TokenRefreshHandler tokenRefreshHandler = new TokenRefreshHandler(this.authProvider.getIdTokenVerifier(), config);

        router.route().handler(BodyHandler.create());
        router.route().handler(StaticHandler.create("webroot"));

        router.route("/login").handler(oktaHandler);
        router.route("/adm/*").handler(tokenRefreshHandler);
        router.route("/adm/*").handler(oktaHandler);
        router.route("/api/*").handler(tokenRefreshHandler);
        router.route("/api/*").handler(oktaHandler);

        router.get("/login").handler(new RedirectToRootHandler(false));
        router.get("/logout").handler(new RedirectToRootHandler(true));
        router.get("/ops/healthcheck").handler(this::handleHealthCheck);
        router.get("/api/token/get").handler(ctx -> handleTokenGet(ctx));

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

    private void handleTokenGet(RoutingContext rc) {
        if (isAuthDisabled(config)) {
            respondWithTestAdminUser(rc);
        } else {
            respondWithRealUser(rc);
        }
    }

    private void respondWithRealUser(RoutingContext rc) {
        if (getEmailClaim(rc) != null) {
            handleEmailContactInfo(rc, getEmailClaim(rc));
        } else {
            rc.response().setStatusCode(401).end("Not logged in");
        }
    }

    String getEmailClaim(RoutingContext ctx) {
        try {
            Jwt jwt = this.authProvider.getIdTokenVerifier().decode(ctx.user().principal().getString("id_token"), null);
            return jwt.getClaims().get("email").toString();
        } catch (Exception e) {
            return null;
        } 
     }

    private void respondWithTestAdminUser(RoutingContext rc) {
        // This test user is set up in localstack
        handleEmailContactInfo(rc, "test.user@uidapi.com");
    }

    private void handleEmailContactInfo(RoutingContext rc, String contact) {
        AdminUser adminUser = adminUserProvider.getAdminUserByContact(contact);
        if (adminUser == null) {
            adminUser = AdminUser.unknown(contact);
        }

        try {
            rc.response().end(jsonWriter.writeValueAsString(adminUser));
        } catch (Exception ex) {
            rc.fail(ex);
        }
    }
}
