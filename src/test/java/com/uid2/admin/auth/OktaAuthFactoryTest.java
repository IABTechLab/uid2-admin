package com.uid2.admin.auth;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.handler.AuthenticationHandler;
import io.vertx.ext.web.handler.impl.OAuth2AuthHandlerImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OktaAuthProviderTest {
    Vertx vertx = mock(Vertx.class);
    Route route = mock(Route.class);
    OAuth2Auth provider = mock(OAuth2Auth.class);

    @BeforeEach
    public void setup() {
        when(route.getPath()).thenReturn("/oauth2-callback");
    }

    @Test
    public void buildsRealAuthHandlerWhenAuthIsEnabled() {
        JsonObject config = new JsonObject();
        config.put("is_auth_disabled", false);
        config.put("okta_client_id", "id1");
        config.put("okta_client_secret", "secret1");
        config.put("okta_issuer", "http://uid2.okta.com");
        config.put("okta_callback", "http://localhost/oauth2-callbac");

        OktaAuthProvider provider = new OktaAuthProvider(config);
        AuthenticationHandler handler = provider.createAuthHandler(vertx, route);
        assertEquals(handler.getClass(), OAuth2AuthHandlerImpl.class);
    }

    @Test
    public void buildsRealAuthHandlerWhenAuthIsNotInTheConfig() {
        JsonObject config = new JsonObject();
        config.put("okta_client_id", "id1");
        config.put("okta_client_secret", "secret1");
        config.put("okta_issuer", "http://uid2.okta.com");
        config.put("okta_callback", "http://localhost/oauth2-callbac");
        OktaAuthProvider provider = new OktaAuthProvider(config);
        AuthenticationHandler handler = provider.createAuthHandler(vertx, route);

        assertEquals(handler.getClass(), OAuth2AuthHandlerImpl.class);
    }

    @Test
    public void ensureAuthHandlerRequestForEmailAndGroup() {
        JsonObject config = new JsonObject();
        config.put("okta_client_id", "id1");
        config.put("okta_client_secret", "secret1");
        config.put("okta_issuer", "http://uid2.okta.com");
        config.put("okta_callback", "http://localhost/oauth2-callbac");
        OktaAuthProvider provider = new OktaAuthProvider(config);
        assertTrue(provider.getScopes().contains("groups"));
        assertTrue(provider.getScopes().contains("email"));
        assertTrue(provider.getScopes().contains("profile"));
    }

    @Test
    public void buildsNoopAuthHandlerWhenAuthIsDisabled() {
        JsonObject config = new JsonObject();
        config.put("is_auth_disabled", true);

        OktaAuthProvider provider = new OktaAuthProvider(config);
        AuthenticationHandler handler = provider.createAuthHandler(vertx, route);

        assertEquals(handler.getClass(), NoopAuthHandler.class);
    }
}