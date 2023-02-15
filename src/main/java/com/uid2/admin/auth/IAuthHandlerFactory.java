package com.uid2.admin.auth;

import io.vertx.core.Vertx;
import io.vertx.ext.auth.authentication.AuthenticationProvider;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.handler.AuthenticationHandler;

public interface IAuthHandlerFactory {

    AuthenticationHandler createAuthHandler(Vertx vertx, Route callbackRoute, AuthenticationProvider authProvider);
    AuthenticationProvider createAuthProvider(Vertx vertx);

}
