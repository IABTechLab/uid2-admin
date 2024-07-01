package com.uid2.admin.store.writer;

import io.vertx.core.json.JsonObject;

public interface EncryptedStoreWriter<T> {
    void upload(T data, JsonObject extraMeta) throws Exception;
}