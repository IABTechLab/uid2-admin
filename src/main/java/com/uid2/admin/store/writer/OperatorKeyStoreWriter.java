package com.uid2.admin.store.writer;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.auth.RotatingOperatorKeyProvider;
import com.uid2.shared.store.CloudPath;
import com.uid2.admin.store.FileName;
import io.vertx.core.json.JsonObject;

import java.time.Instant;
import java.util.Collection;

public class OperatorKeyStoreWriter {
    private final RotatingOperatorKeyProvider provider;
    private final FileManager fileManager;
    private final ObjectWriter jsonWriter;
    private final VersionGenerator versionGenerator;

    public OperatorKeyStoreWriter(RotatingOperatorKeyProvider provider, FileManager fileManager, ObjectWriter jsonWriter, VersionGenerator versionGenerator) {
        this.provider = provider;
        this.fileManager = fileManager;
        this.jsonWriter = jsonWriter;
        this.versionGenerator = versionGenerator;
    }

    public void upload(Collection<OperatorKey> data) throws Exception {
        long generated = Instant.now().getEpochSecond();
        FileName dataFile = new FileName("operators", ".json");
        FileName backupFile = new FileName("operators-old", ".json");

        JsonObject metadata = provider.getMetadata();
        // bump up metadata version
        metadata.put("version", versionGenerator.getVersion());
        metadata.put("generated", generated);

        // get location to upload
        CloudPath location = new CloudPath(metadata.getJsonObject("operators").getString("location"));

        fileManager.backupFile(location, backupFile, generated);

        // generate new operators
        String content = jsonWriter.writeValueAsString(data);
        fileManager.uploadFile(location, dataFile, content);
        fileManager.uploadMetadata(metadata, "operators", provider.getMetadataPath());

        // refresh manually
        provider.loadContent(provider.getMetadata());
    }
}
