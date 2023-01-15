package com.uid2.admin.job;

import io.vertx.core.json.JsonObject;

public final class MetadataPaths {
    public static JsonObject Config;

    public static void LoadConfig(JsonObject config)
    {
        Config = config;
    }
}
