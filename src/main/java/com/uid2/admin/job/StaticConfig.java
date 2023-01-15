package com.uid2.admin.job;

import io.vertx.core.json.JsonObject;

//For use by OverallSyncJob
public final class StaticConfig {
    public static JsonObject Config;

    public static void LoadConfig(JsonObject config)
    {
        Config = config;
    }
}
