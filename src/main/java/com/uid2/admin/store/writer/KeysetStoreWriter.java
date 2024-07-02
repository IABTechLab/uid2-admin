package com.uid2.admin.store.writer;

import com.azure.json.implementation.DefaultJsonWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.FileName;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.shared.auth.Keyset;
import com.uid2.shared.model.S3Key;
import com.uid2.shared.store.reader.RotatingS3KeyProvider;
import com.uid2.shared.store.reader.StoreReader;
import com.uid2.shared.store.scope.StoreScope;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class KeysetStoreWriter implements StoreWriter<Map<Integer, Keyset>> {

    private final ScopedStoreWriter writer;
    private final ObjectWriter jsonWriter;
    private final boolean enableKeysets;
    private static final Logger LOGGER = LoggerFactory.getLogger(KeysetStoreWriter.class);


    public KeysetStoreWriter(StoreReader<Map<Integer, Keyset>> provider, FileManager fileManager,
                             ObjectWriter jsonWriter, VersionGenerator versionGenerator, Clock clock, StoreScope scope,
                             boolean enableKeysets) {
        this.jsonWriter = jsonWriter;
        FileName dataFile = new FileName("keysets", ".json");
        String dataType = "keysets";
        writer = new ScopedStoreWriter(provider, fileManager, versionGenerator, clock, scope, dataFile, dataType);
        this.enableKeysets = enableKeysets;
    }

    public KeysetStoreWriter(EncryptedScopedStoreWriter writer, ObjectWriter jsonWriter) {
        this.writer = writer;
        this.jsonWriter = jsonWriter;
        this.enableKeysets = true;
    }

    @Override
    public void upload(Map<Integer, Keyset> data, JsonObject extraMeta) throws Exception {
        if(!enableKeysets) {
            LOGGER.error("Uploaded Attempted to Keysets with keysets disabled");
            return;
        }
        JsonArray jsonKeysets = new JsonArray();
        for (Map.Entry<Integer, Keyset> keyset: data.entrySet()) {
            jsonKeysets.add(keyset.getValue());
        }
        writer.upload(jsonKeysets.encodePrettily(), extraMeta);
    }

    @Override
    public void rewriteMeta() throws Exception {

    }
}
