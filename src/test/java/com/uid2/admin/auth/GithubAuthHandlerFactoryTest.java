package com.uid2.admin.auth;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.handler.AuthHandler;
import io.vertx.ext.web.handler.impl.OAuth2AuthHandlerImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;


class GithubAuthHandlerFactoryTest {
    Vertx vertx = mock(Vertx.class);
    Route route = mock(Route.class);

    @Test
    void buildsRealAuthHandlerWhenAuthIsEnabled() {
        JsonObject config = new JsonObject();
        config.put("is_auth_disabled", false);
        config.put("github_client_id", "id1");
        config.put("github_client_secret", "secret1");

        GithubAuthHandlerFactory factory = new GithubAuthHandlerFactory(config);
        AuthHandler handler = factory.createAuthHandler(vertx, route);

        assertEquals(handler.getClass(), OAuth2AuthHandlerImpl.class);
    }

    @Test
    void buildsRealAuthHandlerWhenAuthIsNotInTheConfig() {
        JsonObject config = new JsonObject();
        config.put("github_client_id", "id1");
        config.put("github_client_secret", "secret1");

        GithubAuthHandlerFactory factory = new GithubAuthHandlerFactory(config);
        AuthHandler handler = factory.createAuthHandler(vertx, route);

        assertEquals(handler.getClass(), OAuth2AuthHandlerImpl.class);
    }

    @Test
    void buildsNoopAuthHandlerWhenAuthIsDisabled() {
        JsonObject config = new JsonObject();
        config.put("is_auth_disabled", true);

        GithubAuthHandlerFactory factory = new GithubAuthHandlerFactory(config);
        AuthHandler handler = factory.createAuthHandler(vertx, route);

        assertEquals(handler.getClass(), NoopAuthHandler.class);
    }
}