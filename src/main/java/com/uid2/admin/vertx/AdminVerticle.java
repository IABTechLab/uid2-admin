package com.uid2.admin.vertx;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.auth.*;
import com.uid2.admin.vertx.service.IService;
import com.uid2.shared.Const;
import com.uid2.shared.Utils;
import com.uid2.shared.middleware.AuthMiddleware;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.oauth2.AccessToken;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.*;
import io.vertx.ext.web.sstore.LocalSessionStore;

import static com.uid2.admin.auth.AuthUtils.isAuthDisabled;

public class AdminVerticle extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminVerticle.class);

    private final JsonObject config;
    private final IAuthHandlerFactory authHandlerFactory;
    private final AuthMiddleware auth;
    private final IAdminUserProvider adminUserProvider;
    private final IService[] services;
    private final ObjectWriter jsonWriter = JsonUtil.createJsonWriter();

    public AdminVerticle(JsonObject config,
                         IAuthHandlerFactory authHandlerFactory,
                         AuthMiddleware auth,
                         IAdminUserProvider adminUserProvider,
                         IService... services) {
        this.config = config;
        this.authHandlerFactory = authHandlerFactory;
        this.auth = auth;
        this.adminUserProvider = adminUserProvider;
        this.services = services;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        final Router router = createRoutesSetup();

        final int portOffset = Utils.getPortOffset();
        final int port = Const.Port.ServicePortForAdmin + portOffset;

        vertx.createHttpServer()
                .requestHandler(router::handle)
                .listen(port, result -> {
                    if (result.succeeded()) {
                        startPromise.complete();
                        LOGGER.info("admin verticle started");
                    } else {
                        startPromise.fail(result.cause());
                    }
                });
    }

    private Router createRoutesSetup() {
        final Router router = Router.router(vertx);

        router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)));
        AuthHandler oauth2Handler = authHandlerFactory.createAuthHandler(vertx, router.route("/oauth2-callback"));

        // protect the resource under "/adm/*"
        router.route("/adm/*").handler(oauth2Handler);

        // login page requires oauth2
        router.route("/login").handler(oauth2Handler);
        router.get("/login").handler(ctx -> {
            ctx.response().setStatusCode(302)
                    .putHeader(HttpHeaders.LOCATION, "/")
                    .end("Redirecting to /");
        });

        // logout
        router.get("/logout").handler(ctx -> {
            ctx.session().destroy();
            ctx.response().setStatusCode(302)
                    .putHeader(HttpHeaders.LOCATION, "/")
                    .end("Redirecting to /");
        });

        // The protected resource
        router.get("/protected").handler(ctx -> {
            AccessToken user = (AccessToken) ctx.user();
            // retrieve the user profile, this is a common feature but not from the official OAuth2 spec
            user.userInfo(res -> {
                if (res.failed()) {
                    ctx.session().destroy();
                    ctx.fail(res.cause());
                } else {
                    final JsonObject userInfo = res.result();
                    ctx.response().end(userInfo.getString("email"));
                }
            });
        });

        router.route().handler(BodyHandler.create());

        router.get("/ops/healthcheck").handler(this::handleHealthCheck);

        router.get("/api/token/get").handler(this::handleTokenGet);

        for (IService service : this.services) {
            service.setupRoutes(router);
        }

        router.route().handler(StaticHandler.create("webroot"));
        return router;
    }

    private void handleHealthCheck(RoutingContext rc) {
        rc.response().end("OK");
    }

    private void handleTokenGet(RoutingContext rc) {
        if (isAuthDisabled(config)) {
            respondWithTestAdminUser(rc);
        }
        else {
            respondWithRealUser(rc);
        }
    }

    private void respondWithRealUser(RoutingContext rc) {
        AccessToken user = (AccessToken) rc.user();
        // retrieve the user profile, this is a common feature but not from the official OAuth2 spec
        user.userInfo(res -> {
            if (res.failed()) {
                rc.session().destroy();
                rc.fail(res.cause());
            } else {
                final JsonObject userInfo = res.result();
                String contact = userInfo.getString("email");
                if (contact == null) {
                    user.fetch("https://api.github.com/user/emails", rcEmail -> {
                        if (rcEmail.failed()) {
                            rc.session().destroy();
                            rc.fail(res.cause());
                        } else {
                            JsonArray emails = rcEmail.result().jsonArray();
                            if (emails.size() > 0) {
                                final String publicEmail = emails.getJsonObject(0).getString("email");
                                handleEmailContactInfo(publicEmail, rc);
                            } else {
                                rc.fail(new Throwable("No public emails"));
                            }
                        }
                    });
                } else {
                    handleEmailContactInfo(contact, rc);
                }
            }
        });
    }

    private void respondWithTestAdminUser(RoutingContext rc) {
        // This test user is set up in src/main/resources/localstack/s3/admins/admins.json
        handleEmailContactInfo("test.user@uidapi.com", rc);
    }

    private void handleEmailContactInfo(String contact, RoutingContext rc) {
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
