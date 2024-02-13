package com.uid2.admin.auth;


import com.okta.jwt.AccessTokenVerifier;
import com.okta.jwt.Jwt;
import com.okta.jwt.JwtVerificationException;
import com.okta.jwt.JwtVerifiers;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

import java.time.Duration;
import java.util.*;

public class AdminAuthMiddleware {
    private final AuthProvider authProvider;
    private final AccessTokenVerifier tokenVerifier;
    public AdminAuthMiddleware(AuthProvider authProvider) {
        this.authProvider = authProvider;
        this.tokenVerifier = JwtVerifiers.accessTokenVerifierBuilder()
                .setIssuer("https://uid2.okta.com/oauth2/aus1oqu660mF7W3hi1d8")
                .setAudience("https://api.admin.com/api")
                .setConnectionTimeout(Duration.ofSeconds(1))
                .build();
    }

    public Handler<RoutingContext> handle(Handler<RoutingContext> handler, AdminRole... roles) {
        if (roles == null || roles.length == 0) {
            throw new IllegalArgumentException("must specify at least one role");
        }
        AdminAuthHandler adminAuthHandler = new AdminAuthHandler(handler, authProvider, Set.of(roles), tokenVerifier);
        return adminAuthHandler::handle;
    }

    private static class AdminAuthHandler {
        private final Handler<RoutingContext> innerHandler;
        private final Set<AdminRole> allowedRoles;
        private final List<String> oktaRoles = List.of("developer", "developer-elevated", "infra-admin", "admin");
        private final AuthProvider authProvider;
        private final AccessTokenVerifier tokenVerifier;
        private AdminAuthHandler(Handler<RoutingContext> handler, AuthProvider authProvider, Set<AdminRole> allowedRoles, AccessTokenVerifier tokenVerifier) {
            this.innerHandler = handler;
            this.authProvider = authProvider;
            this.allowedRoles = allowedRoles;
            this.tokenVerifier = tokenVerifier;
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
            return true; // TODO
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
                jwt = tokenVerifier.decode(accessToken);
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
                jwt = tokenVerifier.decode(idToken);
            } catch (JwtVerificationException e) {
                rc.response().setStatusCode(401).end();
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
