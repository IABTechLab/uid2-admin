package com.uid2.admin.auth;


import com.okta.jwt.*;
import com.uid2.admin.AdminConst;
import com.uid2.admin.model.AuditParams;
import com.uid2.shared.auth.Role;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthenticationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;


public class AdminAuthMiddleware {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminAuthMiddleware.class);
    private final AuthProvider authProvider;
    private final String environment;
    private final boolean isAuthDisabled;

    final Map<Role, List<OktaGroup>> roleToOktaGroups = new EnumMap<>(Role.class);
    public AdminAuthMiddleware(AuthProvider authProvider, JsonObject config) {
        this.authProvider = authProvider;
        this.environment = config.getString("environment", "local");
        this.isAuthDisabled = config.getBoolean("is_auth_disabled", false);
        roleToOktaGroups.put(Role.MAINTAINER, parseOktaGroups(config.getString(AdminConst.ROLE_OKTA_GROUP_MAP_MAINTAINER)));
        roleToOktaGroups.put(Role.PRIVILEGED, parseOktaGroups(config.getString(AdminConst.ROLE_OKTA_GROUP_MAP_PRIVILEGED)));
        roleToOktaGroups.put(Role.SUPER_USER, parseOktaGroups(config.getString(AdminConst.ROLE_OKTA_GROUP_MAP_SUPER_USER)));
    }

    private List<OktaGroup> parseOktaGroups(final String oktaGroups) {
        final List<OktaGroup> allOktaGroups = new ArrayList<>();
        for (String group : oktaGroups.split(",")) {
            OktaGroup oktaGroup = OktaGroup.fromName(group.trim());
            if (oktaGroup.equals(OktaGroup.INVALID)) {
                throw new IllegalArgumentException("Invalid okta group name " + group);
            }
            allOktaGroups.add(oktaGroup);
        }
        return allOktaGroups;
    }

    public Handler<RoutingContext> handle(Handler<RoutingContext> handler, AuditParams params, Role... roles) {
        if (isAuthDisabled) return handler;
        if (roles == null || roles.length == 0) {
            throw new IllegalArgumentException("must specify at least one role");
        }
        Handler<RoutingContext> loggedHandler = logAndHandle(handler, params);
        AdminAuthHandler adminAuthHandler = new AdminAuthHandler(loggedHandler, authProvider, Set.of(roles),
                environment, roleToOktaGroups);
        return adminAuthHandler::handle;
    }

    public Handler<RoutingContext> handle(Handler<RoutingContext> handler, Role... roles) {
        return this.handle(handler, null, roles);
    }

    private Handler<RoutingContext> logAndHandle(Handler<RoutingContext> handler, AuditParams params) {
        return ctx -> {
            long startTime = System.currentTimeMillis();

            ctx.addBodyEndHandler(v -> {
                long durationMs = System.currentTimeMillis() - startTime;
                String method = ctx.request().method().name();
                String path = ctx.request().path();
                int status = ctx.response().getStatusCode();
                String userAgent = ctx.request().getHeader("User-Agent");
                String ip = ctx.request().remoteAddress().host();
                String requestId = ctx.request().getHeader("X-Amzn-Trace-Id");
                JsonObject userDetails = ctx.get("userDetails");
                String requestBody = ctx.getBodyAsString();
                String queryParams = ctx.request().query();
                MultiMap queryParamsMap = ctx.request().params();
                JsonObject queryParamsJson = new JsonObject();
                queryParamsMap.forEach(entry -> {
                    if (params.getQueryParams().contains(entry.getKey())) {
                        queryParamsJson.put(entry.getKey(), entry.getValue());
                    }
                });
                // Structured JSON log
                JsonObject auditLog = new JsonObject()
                        .put("timestamp", Instant.now().toString())
                        .put("method", method)
                        .put("endpoint", path)
                        .put("status", status)
                        .put("duration_ms", durationMs)
                        .put("request_id", requestId != null ? requestId : "ABUTEST")
                        .put("ip", ip)
                        .put("user_agent", userAgent)
                        .put("user", userDetails);
                if (params != null)
                    auditLog.put("params", queryParamsJson);

                if ("POST".equals(method) || "PUT".equals(method)) {
                    auditLog.put("body", requestBody);
                }

                // logger.info(auditLog.encode());
                System.out.println(auditLog.toString());
            });
            handler.handle(ctx);
        };
    }

    private static class AdminAuthHandler {
        private final String environment;
        private final Handler<RoutingContext> innerHandler;
        private final Set<Role> allowedRoles;
        private final Map<Role, List<OktaGroup>> roleToOktaGroups;
        private final AuthProvider authProvider;
        private AdminAuthHandler(Handler<RoutingContext> handler, AuthProvider authProvider, Set<Role> allowedRoles,
                                 String environment, Map<Role, List<OktaGroup>> roleToOktaGroups) {
            this.environment = environment;
            this.innerHandler = handler;
            this.authProvider = authProvider;
            this.allowedRoles = allowedRoles;
            this.roleToOktaGroups = roleToOktaGroups;
        }

        public static String extractBearerToken(String headerValue) {
            final String bearerPrefix = "bearer ";
            if (headerValue == null) {
                return null;
            } else {
                String v = headerValue.trim();
                if (v.length() < bearerPrefix.length()) {
                    return null;
                } else {
                    String givenPrefix = v.substring(0, bearerPrefix.length());
                    return !bearerPrefix.equalsIgnoreCase(givenPrefix) ? null : v.substring(bearerPrefix.length());
                }
            }
        }
        private boolean isAuthorizedUser(List<String> userAssignedGroups) {
            for (Role role : allowedRoles) {
                if (roleToOktaGroups.containsKey(role)) {
                    List<OktaGroup> allowedOktaGroupsForRole = roleToOktaGroups.get(role);
                    for (String userGroup : userAssignedGroups) {
                        if (allowedOktaGroupsForRole.contains(OktaGroup.fromName(userGroup))) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
        private boolean isAuthorizedService(List<String> scopes) {
            for (String scope : scopes) {
                if (allowedRoles.contains(OktaCustomScope.fromName(scope).getRole())) {
                    return true;
                }
            }
            return false;
        }

        public void handle(RoutingContext rc) {
            String idToken = null;
            if(rc.user() != null && rc.user().principal() != null) {
                idToken = rc.user().principal().getString("id_token");
            }
            if(idToken != null) {
                validateIdToken(rc, idToken);
                return;
            }

            // machine user
            String authHeaderValue = rc.request().getHeader("Authorization");
            String accessToken = extractBearerToken(authHeaderValue);
            if(accessToken == null) {
                rc.response().putHeader("REQUIRES_AUTH", "1").setStatusCode(401).end();
                return;
            }
            validateAccessToken(rc, accessToken);
        }

        private void validateAccessToken(RoutingContext rc, String accessToken) {
            Jwt jwt;
            try {
                jwt = authProvider.getAccessTokenVerifier().decode(accessToken);
            } catch (JwtVerificationException e) {
                rc.response().setStatusCode(401).end();
                return;
            }
            if(jwt.getClaims().get("environment") == null || !jwt.getClaims().get("environment").toString().equals(environment)) {
                rc.response().setStatusCode(401).end();
                return;
            }
            List<String> scopes = (List<String>) jwt.getClaims().get("scp");
            if(isAuthorizedService(scopes)) {
                innerHandler.handle(rc);
            } else {
                rc.response().setStatusCode(401).end();
            }
        }

        private void validateIdToken(RoutingContext rc, String idToken) {
            Jwt jwt;
            rc.put("random", "value");
            try {
                jwt = authProvider.getIdTokenVerifier().decode(idToken, null);
            } catch (JwtVerificationException e) {
                rc.session().destroy();
                rc.response().putHeader("REQUIRES_AUTH", "1").setStatusCode(401).end();
                return;
            }
            if(jwt.getClaims().get("environment") == null || !jwt.getClaims().get("environment").toString().equals(environment)) {
                rc.response().setStatusCode(401).end();
                return;
            }
            // this varies by human/system user
            List<String> groups = (List<String>) jwt.getClaims().get("groups");
            JsonObject userDetails = new JsonObject();
            userDetails.put("groups", (List<String>) jwt.getClaims().get("groups"));
            userDetails.put("email", jwt.getClaims().get("email"));
            userDetails.put("sub", jwt.getClaims().get("sub"));
            if(isAuthorizedUser(groups)) {
                rc.put("userDetails", userDetails);
                innerHandler.handle(rc);
            } else {
                rc.response().setStatusCode(401).end();
            }
        }
    }
}
