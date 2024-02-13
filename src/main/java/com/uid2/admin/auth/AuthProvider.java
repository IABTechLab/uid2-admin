package com.uid2.admin.auth;

import com.okta.jwt.AccessTokenVerifier;

import com.okta.jwt.IdTokenVerifier;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.handler.AuthenticationHandler;
import java.util.List;

public interface AuthProvider {
    AuthenticationHandler createAuthHandler(Vertx vertx, Route callbackRoute);
    AccessTokenVerifier getAccessTokenVerifier();
    IdTokenVerifier getIdTokenVerifier();
    List<String> getScopes();
}
