package com.uid2.admin.auth;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.authentication.AuthenticationProvider;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.providers.GithubAuth;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.handler.AuthenticationHandler;
import io.vertx.ext.web.handler.OAuth2AuthHandler;

import static com.uid2.admin.auth.AuthUtil.isAuthDisabled;

public class GithubAuthFactory implements AuthFactory {
    private final JsonObject config;

    public GithubAuthFactory(JsonObject config) {
        this.config = config;
    }

    @Override
    public AuthenticationHandler createAuthHandler(Vertx vertx, Route callbackRoute, AuthenticationProvider authProvider) {
        if (isAuthDisabled(config)) {
            return new NoopAuthHandler();
        }

        return OAuth2AuthHandler.create(vertx, (OAuth2Auth) authProvider, config.getString("oauth2_callback_url"))
                .setupCallback(callbackRoute)
                .withScope("user:email");
    }

    @Override
    public AuthenticationProvider createAuthProvider(Vertx vertx) {
        if (isAuthDisabled(config)) {
            return null;
        }

        final String clientId = config.getString("github_client_id");
        final String clientSecret = config.getString("github_client_secret");
        return GithubAuth.create(vertx, clientId, clientSecret);
    }
}