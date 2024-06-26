package com.uid2.admin.store.writer;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.FileName;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.shared.model.S3Key;
import com.uid2.shared.store.reader.StoreReader;
import com.uid2.shared.store.scope.StoreScope;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class S3KeyStoreWriter implements StoreWriter<Map<Integer, S3Key>> {

    private final ScopedStoreWriter writer;
    private final ObjectWriter jsonWriter;
    private static final Logger LOGGER = LoggerFactory.getLogger(S3KeyStoreWriter.class);

    public S3KeyStoreWriter(StoreReader<Map<Integer, S3Key>> provider, FileManager fileManager,
                            ObjectWriter jsonWriter, VersionGenerator versionGenerator, Clock clock, StoreScope scope) {
        this.jsonWriter = jsonWriter;
        FileName dataFile = new FileName("s3encryption_keys", ".json");
        String dataType = "s3encryption_keys";
        writer = new ScopedStoreWriter(provider, fileManager, versionGenerator, clock, scope, dataFile, dataType);
    }

    @Override
    public void upload(Map<Integer, S3Key> data, JsonObject extraMeta) throws Exception {
        JsonArray jsonS3Keys = new JsonArray();
        for (Map.Entry<Integer, S3Key> s3KeyEntry : data.entrySet()) {
            jsonS3Keys.add(s3KeyEntry.getValue());
        }
        writer.upload(jsonS3Keys.encodePrettily(), extraMeta);
    }

    @Override
    public void rewriteMeta() throws Exception {
        // Implement if necessary for rewriting metadata
    }
}