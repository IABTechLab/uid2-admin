package com.uid2.admin.vertx.api.annotations;

import io.vertx.core.http.HttpMethod;

public enum ApiMethod {
    GET(HttpMethod.GET);
    public final HttpMethod vertxMethod;

    ApiMethod(HttpMethod vertxMethod) {
        this.vertxMethod = vertxMethod;
    }
}
