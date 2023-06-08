package com.uid2.admin.store.writer;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.FileName;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.shared.auth.Keyset;
import com.uid2.shared.store.reader.StoreReader;
import com.uid2.shared.store.scope.StoreScope;
import io.vertx.core.json.JsonObject;

import java.util.Collection;
import java.util.Map;

public class KeysetStoreWriter implements StoreWriter<Collection<Keyset>> {

    private final ScopedStoreWriter writer;
    private final ObjectWriter jsonWriter;

    public KeysetStoreWriter(StoreReader<Map<Integer, Keyset>> provider, FileManager fileManager,
                             ObjectWriter jsonWriter, VersionGenerator versionGenerator, Clock clock, StoreScope scope) {
        this.jsonWriter = jsonWriter;
        FileName dataFile = new FileName("keysets", ".json");
        String dataType = "keysets";
        writer = new ScopedStoreWriter(provider, fileManager, versionGenerator, clock, scope, dataFile, dataType);
    }

    @Override
    public void upload(Collection<Keyset> data, JsonObject extraMeta) throws Exception {
        writer.upload(jsonWriter.writeValueAsString(data), extraMeta);
    }

    @Override
    public void rewriteMeta() throws Exception {

    }
}
