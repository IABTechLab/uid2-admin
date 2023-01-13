package com.uid2.admin.store.writer;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.auth.AdminUser;
import com.uid2.admin.auth.AdminUserProvider;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.shared.store.CloudPath;
import com.uid2.admin.store.FileName;
import io.vertx.core.json.JsonObject;

import java.time.Instant;
import java.util.Collection;

public class AdminUserStoreWriter {
    private final AdminUserProvider provider;
    private final FileManager fileManager;
    private final ObjectWriter jsonWriter;
    private final VersionGenerator versionGenerator;


    public AdminUserStoreWriter(AdminUserProvider provider, FileManager fileManager, ObjectWriter jsonWriter, VersionGenerator versionGenerator) {
        this.provider = provider;
        this.fileManager = fileManager;
        this.jsonWriter = jsonWriter;
        this.versionGenerator = versionGenerator;
    }

    public void upload(Collection<AdminUser> data) throws Exception {
        long generated = Instant.now().getEpochSecond();
        FileName backupFile = new FileName("admins-old", ".json");
        FileName dataFile = new FileName("admins", ".json");

        JsonObject metadata = provider.getMetadata();
        // bump up metadata version
        metadata.put("version", versionGenerator.getVersion());
        metadata.put("generated", generated);

        // get location to upload
        CloudPath location = new CloudPath(metadata.getJsonObject("admins").getString("location"));

        fileManager.backupFile(location, backupFile, generated);

        // generate new admins
        String content = jsonWriter.writeValueAsString(data);
        fileManager.uploadFile(location, dataFile, content);
        fileManager.uploadMetadata(metadata, "admins", new CloudPath(provider.getMetadataPath()));

        // refresh manually
        provider.loadContent(provider.getMetadata());
    }
}
