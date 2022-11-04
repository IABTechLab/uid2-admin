package com.uid2.admin.auth;

import io.vertx.core.Vertx;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.handler.AuthHandler;

public interface IAuthHandlerFactory {
    AuthHandler createAuthHandler(Vertx vertx, Route callbackRoute);
}
