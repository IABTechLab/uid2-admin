package com.uid2.admin.auth;

import io.vertx.core.Vertx;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.providers.GithubAuth;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.handler.AuthHandler;
import io.vertx.ext.web.handler.OAuth2AuthHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SpringGithubAuthHandlerFactory implements IAuthHandlerFactory {

    private final String clientId;
    private final String clientSecret;

    public SpringGithubAuthHandlerFactory(
            @Value("${github.client_id}") String clientId,
            @Value("${github.client_secret}") String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public AuthHandler createAuthHandler(Vertx vertx, Route callbackRoute) {
        OAuth2Auth oauth2Provider = GithubAuth.create(vertx, clientId, clientSecret);
        return OAuth2AuthHandler.create(oauth2Provider)
                .setupCallback(callbackRoute)
                .addAuthority("user:email");
    }

}
