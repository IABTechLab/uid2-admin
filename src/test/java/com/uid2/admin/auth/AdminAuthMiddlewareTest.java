package com.uid2.admin.auth;

import com.okta.jwt.AccessTokenVerifier;
import com.okta.jwt.IdTokenVerifier;
import com.okta.jwt.Jwt;
import com.okta.jwt.JwtVerificationException;
import com.uid2.shared.auth.Role;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

public class AdminAuthMiddlewareTest {
    private AutoCloseable mocks;
    @Mock private AuthProvider authProvider;
    @Mock private IdTokenVerifier idTokenVerifier;
    @Mock private AccessTokenVerifier accessTokenVerifier;
    @Mock private Jwt jwt;
    @Mock private RoutingContext rc;
    @Mock private Session session;
    @Mock private HttpServerRequest request;
    @Mock private HttpServerResponse response;

    @Mock private User user;
    @Mock private JsonObject principal;
    @Mock private Handler<RoutingContext> innerHandler;
    private AdminAuthMiddleware adminAuthMiddleware;
    Role[] allRolesArr = new Role[] {Role.MAINTAINER, Role.PRIVILEGED, Role.SUPER_USER};

    @BeforeEach
    public void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        this.adminAuthMiddleware = new AdminAuthMiddleware(authProvider, JsonObject.of(
                "role_okta_group_map_default", "developer",
                "role_okta_group_map_privileged", "developer,developer-elevated",
                "role_okta_group_map_super_user", "developer,developer-elevated,admin"
        ));

        when(authProvider.getIdTokenVerifier()).thenReturn(idTokenVerifier);
        when(authProvider.getAccessTokenVerifier()).thenReturn(accessTokenVerifier);

        when(rc.request()).thenReturn(request);
        when(rc.response()).thenReturn(response);
        when(rc.session()).thenReturn(session);

        when(response.setStatusCode(anyInt())).thenReturn(response);
        when(response.putHeader(anyString(), anyString())).thenReturn(response);
    }

    @AfterEach
    public void teardown() throws Exception {
        mocks.close();
    }

    private void mockSession(boolean includeId, boolean includeAccess) {
        when(rc.user()).thenReturn(user);
        when(user.principal()).thenReturn(principal);
        if (includeId) when(principal.getString(eq("id_token"))).thenReturn("testIdToken");
        if (includeAccess) when(request.getHeader(eq("Authorization"))).thenReturn("Bearer testAccessToken");
    }

    private void verifyUnauthorized(boolean reAuth) {
        verify(response, times(1)).setStatusCode(eq(401));
        verify(response, times(1)).end();
        verify(innerHandler, never()).handle(any());

        if(reAuth) {
            verify(response).putHeader(eq("REQUIRES_AUTH"), eq("1"));
            verify(session).destroy();
        }
    }

    @Test
    public void testNoRolesSpecified() {
        assertThrows(IllegalArgumentException.class, () -> adminAuthMiddleware.handle(innerHandler));
        verify(innerHandler, never()).handle(any());
    }

    @Test
    public void testNoSessionOrAccessToken() {
        Handler<RoutingContext> handler = adminAuthMiddleware.handle(innerHandler, allRolesArr);
        handler.handle(rc);
        verifyUnauthorized(false);
    }

    @Test
    public void testNoIdOrAccessToken() {
        mockSession(false, false);
        Handler<RoutingContext> handler = adminAuthMiddleware.handle(innerHandler, allRolesArr);
        handler.handle(rc);
        verifyUnauthorized(false);
    }

    @Test
    public void testIdToken_BadToken() throws JwtVerificationException {
        mockSession(true, true);
        when(idTokenVerifier.decode(anyString(), any())).thenThrow(JwtVerificationException.class);

        Handler<RoutingContext> handler = adminAuthMiddleware.handle(innerHandler, allRolesArr);
        handler.handle(rc);

        verify(idTokenVerifier).decode(eq("testIdToken"), any());
        verifyUnauthorized(true);
    }

    @Test
    public void testIdToken_BadTokenEnvironment() throws JwtVerificationException {
        mockSession(true, true);
        when(idTokenVerifier.decode(anyString(), any())).thenReturn(jwt);
        when(jwt.getClaims()).thenReturn(Map.of("groups", List.of("fake-role"), "environment", "incorrect"));

        Handler<RoutingContext> handler = adminAuthMiddleware.handle(innerHandler, allRolesArr);
        handler.handle(rc);

        verify(idTokenVerifier).decode(eq("testIdToken"), any());
        verify(jwt, times(2)).getClaims();
        verifyUnauthorized(false);
    }

    @Test
    public void testIdToken_GoodTokenUnauthorized() throws JwtVerificationException {
        mockSession(true, true);
        when(idTokenVerifier.decode(anyString(), any())).thenReturn(jwt);
        when(jwt.getClaims()).thenReturn(Map.of("groups", List.of("fake-role"), "environment", "local"));

        Handler<RoutingContext> handler = adminAuthMiddleware.handle(innerHandler, allRolesArr);
        handler.handle(rc);

        verify(idTokenVerifier).decode(eq("testIdToken"), any());
        verify(jwt, times(3)).getClaims();
        verifyUnauthorized(false);
    }

    @Test // TODO make it parametrized and test all okta groups
    public void testIdToken_GoodTokenAuthorized() throws JwtVerificationException {
        mockSession(true, true);
        when(idTokenVerifier.decode(anyString(), any())).thenReturn(jwt);
        when(jwt.getClaims()).thenReturn(Map.of("groups", List.of(OktaGroup.DEVELOPER.getName()), "environment", "local"));

        Handler<RoutingContext> handler = adminAuthMiddleware.handle(innerHandler, allRolesArr);
        handler.handle(rc);

        verify(idTokenVerifier).decode(eq("testIdToken"), any());
        verify(jwt, times(3)).getClaims();
        verify(innerHandler).handle(eq(rc));
    }

    @Test
    public void testAccessToken_BadToken() throws JwtVerificationException {
        mockSession(false, true);
        when(accessTokenVerifier.decode(anyString())).thenThrow(JwtVerificationException.class);

        Handler<RoutingContext> handler = adminAuthMiddleware.handle(innerHandler, allRolesArr);
        handler.handle(rc);

        verify(accessTokenVerifier).decode(eq("testAccessToken"));
        verifyUnauthorized(false);
    }

    @Test
    public void testAccessToken_BadTokenEnvironment() throws JwtVerificationException {
        mockSession(false, true);
        when(accessTokenVerifier.decode(anyString())).thenReturn(jwt);
        when(jwt.getClaims()).thenReturn(Map.of("scp", List.of("uid2.admin.ss-portal"), "environment", "incorrect"));

        Handler<RoutingContext> handler = adminAuthMiddleware.handle(innerHandler, allRolesArr);
        handler.handle(rc);

        verify(accessTokenVerifier).decode(eq("testAccessToken"));
        verify(jwt, times(2)).getClaims();
        verifyUnauthorized(false);
    }

    @Test
    public void testAccessToken_GoodTokenUnauthorized() throws JwtVerificationException {
        mockSession(false, true);
        when(accessTokenVerifier.decode(anyString())).thenReturn(jwt);
        when(jwt.getClaims()).thenReturn(Map.of("scp", List.of("uid2.admin.ss-portal"), "environment", "local"));

        Handler<RoutingContext> handler = adminAuthMiddleware.handle(innerHandler, Role.PRIVATE_OPERATOR_SYNC);
        handler.handle(rc);

        verify(accessTokenVerifier).decode(eq("testAccessToken"));
        verify(jwt, times(3)).getClaims();
        verifyUnauthorized(false);
    }

    @Test
    public void testAccessToken_BadScopeUnauthorized() throws JwtVerificationException {
        mockSession(false, true);
        when(accessTokenVerifier.decode(anyString())).thenReturn(jwt);
        when(jwt.getClaims()).thenReturn(Map.of("scp", List.of(OktaCustomScope.SS_PORTAL.getName()), "environment", "local"));

        Handler<RoutingContext> handler = adminAuthMiddleware.handle(innerHandler, Role.PRIVATE_OPERATOR_SYNC);
        handler.handle(rc);

        verify(accessTokenVerifier).decode(eq("testAccessToken"));
        verify(jwt, times(3)).getClaims();
        verifyUnauthorized(false);
    }

    private static Stream<Arguments> testAccessTokenGoodData() {
        return Stream.of(
          Arguments.of(OktaCustomScope.SS_PORTAL, OktaCustomScope.SS_PORTAL.getRole()),
          Arguments.of(OktaCustomScope.SECRET_ROTATION, OktaCustomScope.SECRET_ROTATION.getRole()),
          Arguments.of(OktaCustomScope.SITE_SYNC, OktaCustomScope.SITE_SYNC.getRole())
        );
    }

    @ParameterizedTest
    @MethodSource("testAccessTokenGoodData")
    public void testAccessToken_GoodTokenAuthorized(OktaCustomScope scope, Role allowedRole) throws JwtVerificationException {
        mockSession(false, true);
        when(accessTokenVerifier.decode(anyString())).thenReturn(jwt);
        when(jwt.getClaims()).thenReturn(Map.of("scp", List.of(scope.getName()), "environment", "local"));

        Handler<RoutingContext> handler = adminAuthMiddleware.handle(innerHandler, allowedRole);
        handler.handle(rc);

        verify(accessTokenVerifier).decode(eq("testAccessToken"));
        verify(jwt, times(3)).getClaims();
        verify(innerHandler).handle(eq(rc));
    }



}
