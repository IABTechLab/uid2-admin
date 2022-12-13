package com.uid2.admin.auth;

import io.vertx.core.json.JsonObject;

public class AuthUtils {
    public static boolean isAuthDisabled(JsonObject config) {
        Boolean isAuthDisabled = config.getBoolean("is_auth_disabled");
        return isAuthDisabled != null && isAuthDisabled;
    }
}
