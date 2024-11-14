package com.uid2.admin.legacy;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.legacy.LegacyClientKey;
import com.uid2.admin.legacy.RotatingLegacyClientKeyProvider;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.FileName;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.EncryptedScopedStoreWriter;
import com.uid2.admin.store.writer.ScopedStoreWriter;
import com.uid2.admin.store.writer.StoreWriter;
import com.uid2.shared.store.reader.RotatingCloudEncryptionKeyProvider;
import com.uid2.shared.store.scope.EncryptedScope;
import com.uid2.shared.store.scope.StoreScope;
import io.vertx.core.json.JsonObject;

import java.util.Collection;

public class LegacyClientKeyStoreWriter implements StoreWriter<Collection<LegacyClientKey>> {
    private final ScopedStoreWriter writer;
    private final ObjectWriter jsonWriter;

    public LegacyClientKeyStoreWriter(RotatingLegacyClientKeyProvider provider, FileManager fileManager, ObjectWriter jsonWriter, VersionGenerator versionGenerator, Clock clock, StoreScope scope) {
        this.jsonWriter = jsonWriter;
        FileName dataFile = new FileName("clients", ".json");
        String dataType = "client_keys";
        writer = new ScopedStoreWriter(provider, fileManager, versionGenerator, clock, scope, dataFile, dataType);
    }
    public LegacyClientKeyStoreWriter(RotatingLegacyClientKeyProvider provider,
                                      FileManager fileManager,
                                      ObjectWriter jsonWriter,
                                      VersionGenerator versionGenerator,
                                      Clock clock,
                                      EncryptedScope scope,
                                      RotatingCloudEncryptionKeyProvider cloudEncryptionKeyProvider) {
        this.jsonWriter = jsonWriter;
        FileName dataFile = new FileName("clients", ".json");
        String dataType = "client_keys";
        this.writer = new EncryptedScopedStoreWriter(provider, fileManager, versionGenerator, clock, scope, dataFile, dataType, cloudEncryptionKeyProvider, scope.getId());
    }

    @Override
    public void upload(Collection<LegacyClientKey> data, JsonObject extraMeta) throws Exception {
        writer.upload(jsonWriter.writeValueAsString(data), extraMeta);
    }

    @Override
    public void rewriteMeta() throws Exception {
        writer.rewriteMeta();
    }
}
