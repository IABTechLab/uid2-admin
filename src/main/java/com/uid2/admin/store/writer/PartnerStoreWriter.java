package com.uid2.admin.store.writer;

import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.reader.RotatingPartnerStore;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.shared.store.CloudPath;
import com.uid2.admin.store.FileName;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.time.Instant;

public class PartnerStoreWriter {
    private final RotatingPartnerStore provider;
    private final FileManager fileManager;
    private final VersionGenerator versionGenerator;


    public PartnerStoreWriter(RotatingPartnerStore provider, FileManager fileManager, VersionGenerator versionGenerator) {
        this.provider = provider;
        this.fileManager = fileManager;
        this.versionGenerator = versionGenerator;
    }

    public void upload(JsonArray partners) throws Exception {
        long generated = Instant.now().getEpochSecond();
        FileName backupFile = new FileName("partners-old", ".json");
        FileName dataFile = new FileName("partners", ".json");

        JsonObject metadata = provider.getMetadata();
        // bump up metadata version
        metadata.put("version", versionGenerator.getVersion());
        metadata.put("generated", generated);

        // get location to upload
        CloudPath location = new CloudPath(metadata.getJsonObject("partners").getString("location"));

        fileManager.backupFile(location, backupFile, generated);

        // generate new partners
        String content = partners.encodePrettily();
        fileManager.uploadFile(location, dataFile, content);
        fileManager.uploadMetadata(metadata, "partners", new CloudPath(provider.getMetadataPath()));

        // refresh manually
        provider.loadContent(provider.getMetadata());
    }
}
