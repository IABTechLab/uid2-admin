// Copyright (c) 2021 The Trade Desk, Inc
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice,
//    this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice,
//    this list of conditions and the following disclaimer in the documentation
//    and/or other materials provided with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
// POSSIBILITY OF SUCH DAMAGE.

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

import java.util.List;

public class AdminVerticle extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminVerticle.class);

    private final IAuthHandlerFactory authHandlerFactory;
    private final AuthMiddleware auth;
    private final IAdminUserProvider adminUserProvider;
    private final IService[] services;
    private final ObjectWriter jsonWriter = JsonUtil.createJsonWriter();

    public AdminVerticle(IAuthHandlerFactory authHandlerFactory,
                         AuthMiddleware auth,
                         IAdminUserProvider adminUserProvider,
                         IService... services) {
        this.authHandlerFactory = authHandlerFactory;
        this.auth = auth;
        this.adminUserProvider = adminUserProvider;
        this.services = services;
    }

    public AdminVerticle(IAuthHandlerFactory authHandlerFactory,
                         AuthMiddleware auth,
                         IAdminUserProvider adminUserProvider,
                         List<IService> services) {
        this.authHandlerFactory = authHandlerFactory;
        this.auth = auth;
        this.adminUserProvider = adminUserProvider;
        this.services = services.toArray(new IService[1]);
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
