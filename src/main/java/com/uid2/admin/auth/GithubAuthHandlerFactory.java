package com.uid2.admin.auth;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.providers.GithubAuth;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.handler.AuthHandler;
import io.vertx.ext.web.handler.OAuth2AuthHandler;

import static com.uid2.admin.auth.AuthUtils.isAuthDisabled;

public class GithubAuthHandlerFactory implements IAuthHandlerFactory {
    private final JsonObject config;

    public GithubAuthHandlerFactory(JsonObject config) {
        this.config = config;
    }

    @Override
    public AuthHandler createAuthHandler(Vertx vertx, Route callbackRoute) {
        if (isAuthDisabled(config)) {
            return new NoopAuthHandler();
        }

        final String clientId = config.getString("github_client_id");
        final String clientSecret = config.getString("github_client_secret");
        OAuth2Auth oauth2Provider = GithubAuth.create(vertx, clientId, clientSecret);
        return OAuth2AuthHandler.create(oauth2Provider)
                .setupCallback(callbackRoute)
                .addAuthority("user:email");
    }
}