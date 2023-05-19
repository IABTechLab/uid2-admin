package com.uid2.admin.store.writer;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.shared.auth.EnclaveIdentifierProvider;
import com.uid2.shared.model.EnclaveIdentifier;
import com.uid2.shared.store.CloudPath;
import com.uid2.admin.store.FileName;
import io.vertx.core.json.JsonObject;

import java.time.Instant;
import java.util.Collection;

public class EnclaveStoreWriter {
    private final EnclaveIdentifierProvider provider;
    private final FileManager fileManager;
    private final ObjectWriter jsonWriter;
    private final VersionGenerator versionGenerator;

    public EnclaveStoreWriter(EnclaveIdentifierProvider provider, FileManager fileManager, ObjectWriter jsonWriter, VersionGenerator versionGenerator) {
        this.provider = provider;
        this.fileManager = fileManager;
        this.jsonWriter = jsonWriter;
        this.versionGenerator = versionGenerator;
    }

    public void upload(Collection<EnclaveIdentifier> data) throws Exception {
        long generated = Instant.now().getEpochSecond();
        FileName dataFile = new FileName("enclaves", ".json");
        JsonObject metadata = provider.getMetadata();

        metadata.put("version", versionGenerator.getVersion());
        metadata.put("generated", generated);
        CloudPath location = new CloudPath(metadata.getJsonObject("enclaves").getString("location"));

        // generate new clients
        String content = jsonWriter.writeValueAsString(data);
        fileManager.uploadFile(location, dataFile, content);
        fileManager.uploadMetadata(metadata, "enclaves", new CloudPath(provider.getMetadataPath()));

        // refresh manually
        provider.loadContent(provider.getMetadata());
    }
}
