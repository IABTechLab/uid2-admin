package com.uid2.admin.store.writer;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.auth.AdminKeyset;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.FileName;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.shared.store.reader.StoreReader;
import com.uid2.shared.store.scope.StoreScope;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.Map;

public class AdminKeysetWriter implements StoreWriter<Map<Integer, AdminKeyset>> {

    private final ScopedStoreWriter writer;

    private final ObjectWriter jsonWriter;

    public AdminKeysetWriter(StoreReader<Map<Integer, AdminKeyset>> provider, FileManager fileManager,
                             ObjectWriter jsonWriter, VersionGenerator versionGenerator, Clock clock, StoreScope storeScope) {
        this.jsonWriter = jsonWriter;
        FileName dataFile = new FileName("admin_keysets", ".json");
        String dataType = "admin_keysets";
        writer = new ScopedStoreWriter(provider, fileManager, versionGenerator, clock, storeScope, dataFile, dataType);
    }

    public AdminKeysetWriter(EncryptedScopedStoreWriter writer, ObjectWriter jsonWriter) {
        this.writer = writer;
        this.jsonWriter = jsonWriter;
    }

    @Override
    public void upload(Map<Integer, AdminKeyset> data, JsonObject extraMeta) throws Exception {
        JsonArray jsonKeysets = new JsonArray();
        for (Map.Entry<Integer, AdminKeyset> keyset: data.entrySet()) {
            jsonKeysets.add(keyset.getValue());
        }
        writer.upload(jsonKeysets.encodePrettily(), extraMeta);
    }

    @Override
    public void rewriteMeta() throws Exception {

    }
}
