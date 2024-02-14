package com.uid2.admin.auth;


import com.okta.jwt.*;
import com.uid2.shared.auth.Role;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class AdminAuthMiddleware {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminAuthMiddleware.class);
    private final AuthProvider authProvider;
    public AdminAuthMiddleware(AuthProvider authProvider) {
        this.authProvider = authProvider;
    }

    public Handler<RoutingContext> handle(Handler<RoutingContext> handler, Role... roles) {
        if (roles == null || roles.length == 0) {
            throw new IllegalArgumentException("must specify at least one role");
        }
        AdminAuthHandler adminAuthHandler = new AdminAuthHandler(handler, authProvider, Set.of(roles));
        return adminAuthHandler::handle;
    }

    private static class AdminAuthHandler {
        private final Handler<RoutingContext> innerHandler;
        private final Set<Role> allowedRoles;
        private final List<String> oktaRoles = List.of("developer", "developer-elevated", "infra-admin", "admin");
        private final AuthProvider authProvider;
        private AdminAuthHandler(Handler<RoutingContext> handler, AuthProvider authProvider, Set<Role> allowedRoles) {
            this.innerHandler = handler;
            this.authProvider = authProvider;
            this.allowedRoles = allowedRoles;
        }

        public static String extractBearerToken(String headerValue) {
            if (headerValue == null) {
                return null;
            } else {
                String v = headerValue.trim();
                if (v.length() < "bearer ".length()) {
                    return null;
                } else {
                    String givenPrefix = v.substring(0, "bearer ".length());
                    return !"bearer ".equalsIgnoreCase(givenPrefix) ? null : v.substring("bearer ".length());
                }
            }
        }
        private boolean isAuthorizedUser(List<String> userGroups) {
            // TODO temporary, introduce role mapping later
            return oktaRoles.stream().anyMatch(userGroups::contains);
        }
        private boolean isAuthorizedService(List<String> scopes) {
            if (scopes.contains("uid2.admin.ss-portal") && allowedRoles.contains(Role.SHARING_PORTAL)) {
                return true;
            } else if (scopes.contains("uid2.admin.secret-rotation") && allowedRoles.contains(Role.SECRET_MANAGER)) {
                return true;
            } else return scopes.contains("uid2.admin.site-sync") && allowedRoles.contains(Role.SECRET_MANAGER);
        }
        public void handle(RoutingContext rc) {
            // human user
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
                rc.response().setStatusCode(401).end();
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
            List<String> scopes = (List<String>) jwt.getClaims().get("scp");
            if(isAuthorizedService(scopes)) {
                innerHandler.handle(rc);
            } else {
                rc.response().setStatusCode(401).end();
            }
        }

        private void validateIdToken(RoutingContext rc, String idToken) {
            Jwt jwt;
            try {
                jwt = authProvider.getIdTokenVerifier().decode(idToken, null);
            } catch (JwtVerificationException e) {
                rc.session().destroy();
                rc.response().putHeader("REQUIRES_AUTH", "1").setStatusCode(401).end();
                return;
            }
            List<String> groups = (List<String>) jwt.getClaims().get("groups");
            if(isAuthorizedUser(groups)) {
                innerHandler.handle(rc);
            } else {
                rc.response().setStatusCode(401).end();
            }
        }
    }
}
