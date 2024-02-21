package com.uid2.admin.auth;

import com.okta.jwt.IdTokenVerifier;
import com.okta.jwt.JwtVerificationException;
import com.uid2.shared.util.URLConnectionHttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.net.http.HttpResponse;

import static org.mockito.Mockito.*;

public class TokenRefreshHandlerTest {
    private final String OKTA_URL = "testAuthServer/v1/token?grant_type=refresh_token&refresh_token=testRefreshToken";
    private AutoCloseable mocks;
    @Mock private RoutingContext rc;
    @Mock private User user;
    @Mock private JsonObject principal;
    @Mock private IdTokenVerifier idTokenVerifier;
    @Mock private URLConnectionHttpClient httpClient;
    private TokenRefreshHandler tokenRefreshHandler;

    @BeforeEach
    public void setup() {
        mocks = MockitoAnnotations.openMocks(this);

        JsonObject config = new JsonObject();
        config.put("okta_client_id", "testClientId");
        config.put("okta_client_secret", "testClientSecret");
        config.put("okta_auth_server", "testAuthServer");
        this.tokenRefreshHandler = new TokenRefreshHandler(idTokenVerifier, config, httpClient);
    }

    @AfterEach
    public void teardown() throws Exception {
        mocks.close();
    }

    private void mockSession(boolean tokensPresent){
        when(rc.user()).thenReturn(user);
        when(user.principal()).thenReturn(principal);
        if(tokensPresent) {
            when(principal.getString(eq("id_token"))).thenReturn("testIdToken");
            when(principal.getString(eq("refresh_token"))).thenReturn("testRefreshToken");
        }
    }

    private void verifySession(boolean sessionExists) {
        if(sessionExists) {
            verify(rc, times(4)).user();
            verify(user, times(3)).principal();
            verify(principal, times(1)).getString(eq("id_token"));
            verify(principal, times(1)).getString(eq("refresh_token"));
        } else {
            verify(rc).user();
        }
        verify(rc).next();
    }

    @Test
    public void testNoUserSession() {
        tokenRefreshHandler.handle(rc);
        verifySession(false);
    }

    @Test
    public void testNoRefreshAndIdToken() throws JwtVerificationException {
        mockSession(false);

        tokenRefreshHandler.handle(rc);

        verifySession(true);
        verify(idTokenVerifier, never()).decode(anyString(), any());
    }

    @Test
    public void testIdTokenNotExpired() throws JwtVerificationException, IOException {
        mockSession(true);
        when(idTokenVerifier.decode(anyString(), any())).thenReturn(null);

        tokenRefreshHandler.handle(rc);

        verifySession(true);
        verify(idTokenVerifier).decode(anyString(), any());
        verify(httpClient, never()).post(eq(OKTA_URL), eq(""), any());
    }

    @Test
    public void testRefreshTokensRefreshFailed() throws JwtVerificationException, IOException {
        mockSession(true);
        when(idTokenVerifier.decode(anyString(), any())).thenThrow(JwtVerificationException.class);
        HttpResponse<String> response = (HttpResponse<String>) mock(HttpResponse.class);
        when(httpClient.post(anyString(), anyString(), any())).thenReturn(response);
        when(response.statusCode()).thenReturn(400);

        tokenRefreshHandler.handle(rc);

        verifySession(true);
        verify(idTokenVerifier).decode(anyString(), any());
        verify(httpClient).post(eq(OKTA_URL), eq(""), any());
        verify(response).statusCode();
        verify(principal, never()).put(anyString(), anyString());
    }

    @Test
    public void testRefreshTokensRefreshSucceeded() throws JwtVerificationException, IOException {
        mockSession(true);
        when(idTokenVerifier.decode(anyString(), any())).thenThrow(JwtVerificationException.class);
        HttpResponse<String> response = (HttpResponse<String>) mock(HttpResponse.class);
        when(httpClient.post(anyString(), anyString(), any())).thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"id_token\": \"testIdToken2\",\"refresh_token\":\"testRefreshToken2\",\"access_token\":\"testAccessToken2\"}");

        tokenRefreshHandler.handle(rc);

        verify(idTokenVerifier).decode(anyString(), any());
        verify(httpClient).post(eq(OKTA_URL), eq(""), any());
        verify(response).statusCode();
        verify(principal).put(eq("id_token"), eq("testIdToken2"));
        verify(principal).put(eq("refresh_token"), eq("testRefreshToken2"));
        verify(principal).put(eq("access_token"), eq("testAccessToken2"));
    }

}
