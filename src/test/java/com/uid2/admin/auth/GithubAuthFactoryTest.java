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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GithubAuthFactoryTest {
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
        config.put("github_client_id", "id1");
        config.put("github_client_secret", "secret1");
        config.put("oauth2_callback_url", "http://localhost/oauth2-callback");

        GithubAuthFactory factory = new GithubAuthFactory(config);
        AuthenticationHandler handler = factory.createAuthHandler(vertx, route, provider);

        assertEquals(handler.getClass(), OAuth2AuthHandlerImpl.class);
    }

    @Test
    public void buildsRealAuthHandlerWhenAuthIsNotInTheConfig() {
        JsonObject config = new JsonObject();
        config.put("github_client_id", "id1");
        config.put("github_client_secret", "secret1");
        config.put("oauth2_callback_url", "http://localhost/oauth2-callback");

        GithubAuthFactory factory = new GithubAuthFactory(config);
        AuthenticationHandler handler = factory.createAuthHandler(vertx, route, provider);

        assertEquals(handler.getClass(), OAuth2AuthHandlerImpl.class);
    }

    @Test
    public void buildsNoopAuthHandlerWhenAuthIsDisabled() {
        JsonObject config = new JsonObject();
        config.put("is_auth_disabled", true);

        GithubAuthFactory factory = new GithubAuthFactory(config);
        AuthenticationHandler handler = factory.createAuthHandler(vertx, route, provider);

        assertEquals(handler.getClass(), NoopAuthHandler.class);
    }
}