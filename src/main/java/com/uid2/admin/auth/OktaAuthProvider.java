package com.uid2.admin.auth;

import com.okta.jwt.IdTokenVerifier;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2Options;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.handler.AuthenticationHandler;
import io.vertx.ext.web.handler.OAuth2AuthHandler;
import com.okta.jwt.AccessTokenVerifier;
import com.okta.jwt.JwtVerifiers;
import static com.uid2.admin.auth.AuthUtil.isAuthDisabled;
import java.time.Duration;
import java.util.List;

public class OktaAuthProvider implements AuthProvider {
    private final JsonObject config;
    private final List<String> scopes = List.of("openid", "profile", "email", "uid2.admin.human", "offline_access");
    private final AccessTokenVerifier accessTokenVerifier;
    private final IdTokenVerifier idTokenVerifier;
    public OktaAuthProvider(JsonObject config) {
        this.config = config;
        if(isAuthDisabled(config)) {
            this.accessTokenVerifier = null;
            this.idTokenVerifier = null;
            return;
        }
        this.accessTokenVerifier = JwtVerifiers.accessTokenVerifierBuilder()
                .setIssuer(config.getString("okta_auth_server"))
                .setAudience(config.getString("okta_audience"))
                .setConnectionTimeout(Duration.ofSeconds(1))
                .setRetryMaxAttempts(2)
                .setRetryMaxElapsed(Duration.ofSeconds(10))
                .build();
        this.idTokenVerifier = JwtVerifiers.idTokenVerifierBuilder()
                .setClientId(config.getString("okta_client_id"))
                .setIssuer(config.getString("okta_auth_server"))
                .setConnectionTimeout(Duration.ofSeconds(1))
                .setRetryMaxAttempts(2)
                .setRetryMaxElapsed(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public AuthenticationHandler createAuthHandler(Vertx vertx, Route callbackRoute) {

        if (isAuthDisabled(config)) {
            return new NoopAuthHandler();
        }

        OAuth2Auth oktaAuth = OAuth2Auth.create(vertx,
        new OAuth2Options()
            .setClientId(this.config.getString("okta_client_id"))
            .setClientSecret(this.config.getString("okta_client_secret"))
            .setSite(this.config.getString("okta_auth_server"))
            .setTokenPath("/v1/token")
            .setAuthorizationPath("/v1/authorize")
            .setUserInfoPath("/v1/userinfo")
        );
        OAuth2AuthHandler authHandler = OAuth2AuthHandler.create(vertx, oktaAuth, this.config.getString("okta_callback"));
        authHandler.extraParams(new JsonObject(String.format("{\"scope\":\"%s\"}", String.join(" ", this.scopes))));
        authHandler.setupCallback(callbackRoute);
        return authHandler;
    }


    @Override
    public AccessTokenVerifier getAccessTokenVerifier() {
        return this.accessTokenVerifier;
    }

    @Override
    public IdTokenVerifier getIdTokenVerifier() {
        return this.idTokenVerifier;
    }

    @Override
    public List<String> getScopes() {
        return this.scopes;
    }
}
