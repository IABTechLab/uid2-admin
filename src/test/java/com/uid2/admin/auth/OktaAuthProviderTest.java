package com.uid2.admin.auth;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.handler.AuthenticationHandler;
import io.vertx.ext.web.handler.impl.OAuth2AuthHandlerImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.uid2.admin.auth.OktaAuthProvider.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OktaAuthProviderTest {
    Vertx vertx = mock(Vertx.class);
    Route route = mock(Route.class);

    @BeforeEach
    public void setup() {
        when(route.getPath()).thenReturn("/oauth2-callback");
    }

    @Test
    public void buildsRealAuthHandlerWhenAuthIsEnabled() {
        JsonObject config = new JsonObject();
        config.put("is_auth_disabled", false);
        config.put(OKTA_CLIENT_ID, "id1");
        config.put(OKTA_CLIENT_SECRET, "secret1");
        config.put(OKTA_AUDIENCE, "https://api.admin.com/api");
        config.put(OKTA_AUTH_SERVER, "https://uid2.okta.com/oauth2/aus1oqu660mF7W3hi1d8");
        config.put(OKTA_CALLBACK, "http://localhost/oauth2-callbac");

        OktaAuthProvider provider = new OktaAuthProvider(config);
        AuthenticationHandler handler = provider.createAuthHandler(vertx, route);
        assertEquals(handler.getClass(), OAuth2AuthHandlerImpl.class);
    }

    @Test
    public void buildsRealAuthHandlerWhenAuthIsNotInTheConfig() {
        JsonObject config = new JsonObject();
        config.put(OKTA_CLIENT_ID, "id1");
        config.put(OKTA_CLIENT_SECRET, "secret1");
        config.put(OKTA_AUDIENCE, "https://api.admin.com/api");
        config.put(OKTA_AUTH_SERVER, "https://uid2.okta.com/oauth2/aus1oqu660mF7W3hi1d8");
        config.put(OKTA_CALLBACK, "http://localhost/oauth2-callbac");
        OktaAuthProvider provider = new OktaAuthProvider(config);
        AuthenticationHandler handler = provider.createAuthHandler(vertx, route);

        assertEquals(handler.getClass(), OAuth2AuthHandlerImpl.class);
    }

    @Test
    public void ensureAuthHandlerRequestForEmail() {
        JsonObject config = new JsonObject();
        config.put(OKTA_CLIENT_ID, "id1");
        config.put(OKTA_CLIENT_SECRET, "secret1");
        config.put(OKTA_AUDIENCE, "https://api.admin.com/api");
        config.put(OKTA_AUTH_SERVER, "https://uid2.okta.com/oauth2/aus1oqu660mF7W3hi1d8");
        config.put(OKTA_CALLBACK, "http://localhost/oauth2-callbac");
        OktaAuthProvider provider = new OktaAuthProvider(config);
        assertTrue(provider.getScopes().contains("email"));
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