package com.uid2.admin.vertx;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.auth.AdminUser;
import com.uid2.admin.auth.IAdminUserProvider;
import com.uid2.admin.auth.IAuthHandlerFactory;
import com.uid2.admin.vertx.service.IService;
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
import io.vertx.ext.web.handler.AuthHandler;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SpringVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminVerticle.class);
    private static final ObjectWriter JSON_WRITER = JsonUtil.createJsonWriter();

    private final int port;
    private final int portOffset;
    private final IAuthHandlerFactory authHandlerFactory;
    private final IAdminUserProvider adminUserProvider;
    private final IService[] services;

    public SpringVerticle(
            @Value("${port}") int port,
            int portOffset,
            IAuthHandlerFactory springGithubAuthHandlerFactory,
            IAdminUserProvider adminUserProvider) {
        this.port = port;
        this.portOffset = portOffset;
        this.authHandlerFactory = springGithubAuthHandlerFactory;
        this.adminUserProvider = adminUserProvider;
        this.services = new IService[]{};
    }

    @Override
    public void start(Promise<Void> startPromise) {
        final Router router = createRoutesSetup();

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(port + portOffset, result -> {
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
        final AuthHandler oauth2Handler = authHandlerFactory.createAuthHandler(vertx, router.route("/oauth2-callback"));

        router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)));

        // protect the resource under "/adm/*"
        router.route("/adm/*").handler(oauth2Handler);

        // login page requires oauth2
        router.route("/login").handler(oauth2Handler);
        router.get("/login").handler(ctx -> ctx.response()
                .setStatusCode(302)
                .putHeader(HttpHeaders.LOCATION, "/")
                .end("Redirecting to /"));

        // logout
        router.get("/logout").handler(ctx -> {
            ctx.session().destroy();
            ctx.response()
                    .setStatusCode(302)
                    .putHeader(HttpHeaders.LOCATION, "/")
                    .end("Redirecting to /");
        });

        // the protected resource
        router.get("/protected").handler(ctx -> {
            final AccessToken user = (AccessToken) ctx.user();
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
        final AccessToken user = (AccessToken) rc.user();
        // retrieve the user profile, this is a common feature but not from the official OAuth2 spec
        user.userInfo(res -> {
            if (res.failed()) {
                rc.session().destroy();
                rc.fail(res.cause());
            } else {
                final JsonObject userInfo = res.result();
                final String contact = userInfo.getString("email");
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

    private void handleEmailContactInfo(String contact, RoutingContext rc) {
        AdminUser adminUser = adminUserProvider.getAdminUserByContact(contact);
        if (adminUser == null) {
            adminUser = AdminUser.unknown(contact);
        }

        try {
            rc.response().end(JSON_WRITER.writeValueAsString(adminUser));
        } catch (JsonProcessingException e) {
            LOGGER.error(e.getMessage(), e);
            rc.fail(e);
        }
    }

}
