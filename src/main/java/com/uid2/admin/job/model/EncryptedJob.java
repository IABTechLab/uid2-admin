package com.uid2.admin.job.model;

import io.vertx.core.json.JsonObject;

import java.time.Instant;

public abstract class EncryptedJob extends Job {
    private final JsonObject baseMeta;

    public EncryptedJob(Long unencryptedVersion){
        this.baseMeta = new JsonObject();
        this.baseMeta.put("version", unencryptedVersion);
    }
    public JsonObject getBaseMetadata() {
        return this.baseMeta;
    }
}
