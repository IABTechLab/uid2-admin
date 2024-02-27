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
    public static final String OKTA_AUTH_SERVER = "okta_auth_server";
    public static final String OKTA_AUDIENCE = "okta_audience";
    public static final String OKTA_CLIENT_ID = "okta_client_id";
    public static final String OKTA_CLIENT_SECRET = "okta_client_secret";
    public static final String OKTA_CALLBACK = "okta_callback";
    private final JsonObject config;
    private final List<String> scopes = List.of("openid", "email", "uid2.admin.human");
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
                .setIssuer(config.getString(OKTA_AUTH_SERVER))
                .setAudience(config.getString(OKTA_AUDIENCE))
                .setConnectionTimeout(Duration.ofSeconds(1))
                .setRetryMaxAttempts(2)
                .setRetryMaxElapsed(Duration.ofSeconds(10))
                .build();
        this.idTokenVerifier = JwtVerifiers.idTokenVerifierBuilder()
                .setClientId(config.getString(OKTA_CLIENT_ID))
                .setIssuer(config.getString(OKTA_AUTH_SERVER))
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
            .setClientId(this.config.getString(OKTA_CLIENT_ID))
            .setClientSecret(this.config.getString(OKTA_CLIENT_SECRET))
            .setSite(this.config.getString(OKTA_AUTH_SERVER))
            .setTokenPath("/v1/token")
            .setAuthorizationPath("/v1/authorize")
            .setUserInfoPath("/v1/userinfo")
        );
        OAuth2AuthHandler authHandler = OAuth2AuthHandler.create(vertx, oktaAuth, this.config.getString(OKTA_CALLBACK));
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
