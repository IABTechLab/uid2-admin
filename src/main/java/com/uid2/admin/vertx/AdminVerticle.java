package com.uid2.admin.vertx;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.auth.*;
import com.uid2.admin.vertx.service.IService;
import com.uid2.shared.Const;
import com.uid2.shared.Utils;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.ext.web.handler.*;
import io.vertx.ext.web.sstore.LocalSessionStore;

import static com.uid2.admin.auth.AuthUtils.isAuthDisabled;

public class AdminVerticle extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminVerticle.class);

    private final JsonObject config;
    private final AuthFactory authFactory;
    private final IAdminUserProvider adminUserProvider;
    private final IService[] services;
    private final ObjectWriter jsonWriter = JsonUtil.createJsonWriter();

    public AdminVerticle(
            JsonObject config,
            AuthFactory authFactory,
            IAdminUserProvider adminUserProvider,
            IService[] services) {
        this.config = config;
        this.authFactory = authFactory;
        this.adminUserProvider = adminUserProvider;
        this.services = services;
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
                    LOGGER.info("Admin verticle started on port: {}", server.actualPort());
                })
                .onFailure(startPromise::fail);
    }

    private Router createRoutesSetup() {
        final Router router = Router.router(vertx);
        router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)));

        OAuth2Auth oauth2Provider = (OAuth2Auth) authFactory.createAuthProvider(vertx);
        AuthenticationHandler authHandler = authFactory.createAuthHandler(vertx, router.route("/oauth2-callback"), oauth2Provider);

        // protect the resource under "/adm/*"
        router.route("/adm/*").handler(authHandler);

        // login page requires oauth2
        router.route("/login").handler(authHandler);
        router.get("/login").handler(ctx ->
                ctx.response().setStatusCode(302)
                        .putHeader(HttpHeaders.LOCATION, "/")
                        .end("Redirecting to /"));

        // logout
        router.get("/logout").handler(ctx -> {
            ctx.session().destroy();
            ctx.response().setStatusCode(302)
                    .putHeader(HttpHeaders.LOCATION, "/")
                    .end("Redirecting to /");
        });

        router.route().handler(BodyHandler.create());

        router.get("/ops/healthcheck").handler(this::handleHealthCheck);

        router.get("/api/token/get").handler(ctx -> handleTokenGet(ctx, oauth2Provider));

        for (IService service : this.services) {
            service.setupRoutes(router);
        }

        router.route().handler(StaticHandler.create("webroot"));
        return router;
    }

    private void handleHealthCheck(RoutingContext rc) {
        rc.response().end("OK");
    }

    private void handleTokenGet(RoutingContext rc, OAuth2Auth oauth2Provider) {
        if (isAuthDisabled(config)) {
            respondWithTestAdminUser(rc);
        } else {
            respondWithRealUser(rc, oauth2Provider);
        }
    }

    private void respondWithRealUser(RoutingContext rc, OAuth2Auth oauth2Provider) {
        oauth2Provider.userInfo(rc.user())
                .onFailure(e -> {
                    rc.session().destroy();
                    rc.fail(e);
                })
                .onSuccess(userInfo -> {
                    String contact = userInfo.getString("email");
                    if (contact == null) {
                        WebClient.create(rc.vertx())
                                .getAbs("https://api.github.com/user/emails")
                                .authentication(new TokenCredentials(rc.user().<String>get("access_token")))
                                .as(BodyCodec.jsonArray())
                                .send()
                                .onFailure(e -> {
                                    rc.session().destroy();
                                    rc.fail(e);
                                })
                                .onSuccess(res -> {
                                    JsonArray emails = res.body();
                                    if (emails.size() > 0) {
                                        final String publicEmail = emails.getJsonObject(0).getString("email");
                                        handleEmailContactInfo(rc, publicEmail);
                                    } else {
                                        LOGGER.error("No public emails");
                                        rc.fail(new Throwable("No public emails"));
                                    }
                                });
                    } else {
                        handleEmailContactInfo(rc, contact);
                    }
                });
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
