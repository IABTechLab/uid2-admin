package com.uid2.admin.auth;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.providers.GithubAuth;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.handler.AuthHandler;
import io.vertx.ext.web.handler.OAuth2AuthHandler;

public class GithubAuthHandlerFactory implements IAuthHandlerFactory {
    private JsonObject config;

    public GithubAuthHandlerFactory(JsonObject config) {
        this.config = config;
    }

    @Override
    public AuthHandler createAuthHandler(Vertx vertx, Route callbackRoute) {
        final String clientId = config.getString("github_client_id");
        final String clientSecret = config.getString("github_client_secret");
        OAuth2Auth oauth2Provider = GithubAuth.create(vertx, clientId, clientSecret);
        AuthHandler oauth2Handler = OAuth2AuthHandler.create(oauth2Provider)
                .setupCallback(callbackRoute)
                .addAuthority("user:email");
        return oauth2Handler;
    }
}
