package com.uid2.admin.vertx;

import com.okta.jwt.Jwt;
import com.uid2.admin.auth.*;
import com.uid2.admin.vertx.api.V2Router;
import com.uid2.admin.vertx.service.IService;
import com.uid2.shared.Const;
import com.uid2.shared.Utils;
import com.uid2.shared.audit.AuditParams;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.common.template.TemplateEngine;
import io.vertx.ext.web.templ.pebble.PebbleTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.*;
import io.vertx.ext.web.sstore.LocalSessionStore;
import com.uid2.shared.audit.Audit;

import java.time.Instant;
import java.util.List;

import static com.uid2.admin.auth.AuthUtil.isAuthDisabled;

public class AdminVerticle extends AbstractVerticle {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminVerticle.class);
    public static final long MAX_REQUEST_BODY_SIZE = 1 << 20; // 1MB

    private final JsonObject config;
    private final AuthProvider authProvider;
    private final TokenRefreshHandler tokenRefreshHandler;
    private final IService[] services;
    private final V2Router v2Router;
    private final Audit audit;

    public AdminVerticle(JsonObject config,
                         AuthProvider authProvider,
                         TokenRefreshHandler tokenRefreshHandler,
                         IService[] services,
                         V2Router v2Router) {
        this.config = config;
        this.authProvider = authProvider;
        this.tokenRefreshHandler = tokenRefreshHandler;
        this.services = services;
        this.v2Router = v2Router;
        this.audit = new Audit();
    }

    public void start(Promise<Void> startPromise) {
        final Router router = createRoutesSetup();
        final int portOffset = Utils.getPortOffset();
        final int port = Const.Port.ServicePortForAdmin + portOffset;
        vertx.createHttpServer(new HttpServerOptions().setMaxFormBufferedBytes((int) MAX_REQUEST_BODY_SIZE))
                .requestHandler(router)
                .listen(port)
                .onSuccess(server -> {
                    startPromise.complete();
                    LOGGER.info("AdminVerticle instance started on HTTP port: {}", server.actualPort());
                })
                .onFailure(startPromise::fail);
    }

    private Handler<RoutingContext> logAndHandle(Handler<RoutingContext> handler) {
        return ctx -> {
            ctx.addBodyEndHandler(v -> this.audit.log(ctx, null));
            handler.handle(ctx);
        };
    }

//    private Handler<RoutingContext> logAndHandle(Handler<RoutingContext> handler) {
//        return ctx -> {
//            long startTime = System.currentTimeMillis();
//
//            ctx.addBodyEndHandler(v -> {
//                long durationMs = System.currentTimeMillis() - startTime;
//                String method = ctx.request().method().name();
//                String path = ctx.request().path();
//                int status = ctx.response().getStatusCode();
//                String userAgent = ctx.request().getHeader("User-Agent");
//                String ip = ctx.request().remoteAddress().host();
//                String requestId = ctx.request().getHeader("X-Amzn-Trace-Id");
//                JsonObject userDetails = ctx.get("userDetails"); // assuming you store this somewhere in context
//                String requestBody = ctx.getBodyAsString();
//                String queryParams = ctx.request().query();
//                MultiMap queryParamsMap = ctx.request().params();
//                JsonObject queryParamsJson = new JsonObject();
//                queryParamsMap.forEach(entry -> queryParamsJson.put(entry.getKey(), entry.getValue()));
//
//                if ("POST".equals(method) || "PUT".equals(method)) {
//                    userDetails.put("body", requestBody);
//                }
//                // Structured JSON log
//                JsonObject auditLog = new JsonObject()
//                        .put("timestamp", Instant.now().toString())
//                        .put("method", method)
//                        .put("endpoint", path)
//                        .put("status", status)
//                        .put("duration_ms", durationMs)
//                        .put("request_id", requestId != null ? requestId : "ABUTEST")
//                        .put("ip", ip)
//                        .put("user_agent", userAgent)
//                        .put("user", userDetails)
//                        .put("params", queryParamsJson);
//
//                //logger.info(auditLog.encode());
//                System.out.println(auditLog.toString());
//            });
//            handler.handle(ctx);
//        };
//    }


    private Router createRoutesSetup() {
        final Router router = Router.router(vertx);
        //ABU ADD CUSTOM ROUTER HERE!
        router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)).setSessionTimeout(config.getInteger("vertx_session_timeout", 32400000))); // default 9 hr session timeout
        final AuthenticationHandler oktaHandler = this.authProvider.createAuthHandler(vertx, router.route("/oauth2-callback"));

        router.route().handler(BodyHandler.create());
        router.get("/").handler(StaticHandler.create("webroot"));
        router.get("/js/*").handler(StaticHandler.create("webroot/js"));
        router.get("/css/*").handler(StaticHandler.create("webroot/css"));

        final TemplateEngine engine = PebbleTemplateEngine.create(vertx, "html");
        final TemplateHandler templateHandler = TemplateHandler.create(engine, "webroot/adm/", TemplateHandler.DEFAULT_CONTENT_TYPE);

        router.route("/login").handler(logAndHandle(oktaHandler));
        router.get("/adm/*").handler(oktaHandler)
                .handler(ctx -> {
                    ctx.put("ADD_CLIENT_KEY_MESSAGE", config.getString("add_client_key_message"));
                    ctx.put("ADD_SITE_MESSAGE", config.getString("add_site_message"));
                    ctx.next();
                })
                .handler(templateHandler);
        router.route("/api/*").handler(tokenRefreshHandler);

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
        if (isAuthDisabled(config)) rc.response().setStatusCode(200).end(
                JsonObject.of("groups", JsonArray.of("developer", "developer-elevated", "infra-admin", "admin"), "email", "test.user@unifiedid.com").toString());
        try {
            Jwt idJwt = this.authProvider.getIdTokenVerifier().decode(rc.user().principal().getString("id_token"), null);
            JsonObject jo = new JsonObject();
            List<String> groups = (List<String>) idJwt.getClaims().get("groups");
            jo.put("groups", new JsonArray(groups));
            jo.put("email", idJwt.getClaims().get("email"));
            rc.response().setStatusCode(200).end(jo.toString());
        } catch (Exception e) {
            if (rc.session() !=  null) {
                rc.session().destroy();
            }
            rc.response().putHeader("REQUIRES_AUTH", "1").setStatusCode(401).end();
        }
    }

}
