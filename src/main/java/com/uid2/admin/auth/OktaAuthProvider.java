package com.uid2.admin.auth;

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
import java.util.Arrays;
import java.util.List;
import java.util.Collections;

public class OktaAuthProvider implements AuthProvider {
    private final JsonObject config;
    private List<String> scopes = Collections.unmodifiableList(Arrays.asList("openid",  "profile" ,"email" ,"groups"));

    public OktaAuthProvider(JsonObject config) {
        this.config = config;
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
            .setSite(String.format("%soauth2/",  this.config.getString("okta_issuer")))
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
    public AccessTokenVerifier createTokenVerifier() {
        return JwtVerifiers.accessTokenVerifierBuilder()
            .setIssuer(this.config.getString("okta_issuer"))
            .setAudience(this.config.getString("okta_client_id"))                   
            .setConnectionTimeout(Duration.ofSeconds(1))
            .setRetryMaxAttempts(2) 
            .setRetryMaxElapsed(Duration.ofSeconds(10))
            .build();
    }

    @Override
    public List<String> getScopes() {
        return this.scopes;
    }
}
