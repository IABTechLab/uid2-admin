package com.uid2.admin.store.writer;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.FileName;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.shared.model.CloudEncryptionKey;
import com.uid2.shared.model.CloudEncryptionKey;
import com.uid2.shared.store.reader.StoreReader;
import com.uid2.shared.store.scope.StoreScope;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class CloudEncryptionKeyStoreWriter implements StoreWriter<Map<Integer, CloudEncryptionKey>> {

    private final ScopedStoreWriter writer;
    private final ObjectWriter jsonWriter;
    private static final Logger LOGGER = LoggerFactory.getLogger(CloudEncryptionKeyStoreWriter.class);

    public CloudEncryptionKeyStoreWriter(StoreReader<Map<Integer, CloudEncryptionKey>> provider, FileManager fileManager,
                                         ObjectWriter jsonWriter, VersionGenerator versionGenerator, Clock clock, StoreScope scope) {
        this.jsonWriter = jsonWriter;
        FileName dataFile = new FileName("cloud_encryption_keys", ".json");
        String dataType = "cloud_encryption_keys";
        writer = new ScopedStoreWriter(provider, fileManager, versionGenerator, clock, scope, dataFile, dataType);
    }

    @Override
    public void upload(Map<Integer, CloudEncryptionKey> data, JsonObject extraMeta) throws Exception {
        JsonArray jsonCloudEncryptionKeys = new JsonArray();
        for (Map.Entry<Integer, CloudEncryptionKey> cloudEncryptionKeyEntry : data.entrySet()) {
            jsonCloudEncryptionKeys.add(cloudEncryptionKeyEntry.getValue());
        }
        writer.upload(jsonCloudEncryptionKeys.encodePrettily(), extraMeta);
    }

    @Override
    public void rewriteMeta() throws Exception {
        // Implement if necessary for rewriting metadata
    }
}