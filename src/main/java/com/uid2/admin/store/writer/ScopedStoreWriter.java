package com.uid2.admin.store.writer;

import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.shared.store.CloudPath;
import com.uid2.admin.store.FileName;
import com.uid2.shared.store.reader.IMetadataVersionedStore;
import com.uid2.shared.store.scope.StoreScope;
import io.vertx.core.json.JsonObject;

public class ScopedStoreWriter {
    private final IMetadataVersionedStore provider;
    private final FileManager fileManager;
    private final VersionGenerator versionGenerator;
    private final Clock clock;
    private final StoreScope scope;
    private final FileName dataFile;
    private final FileName backupFile;
    private final String dataType;

    public ScopedStoreWriter(
            IMetadataVersionedStore provider,
            FileManager fileManager,
            VersionGenerator versionGenerator,
            Clock clock,
            StoreScope scope,
            FileName dataFile,
            FileName backupFile,
            String dataType
    ) {
        this.provider = provider;
        this.fileManager = fileManager;
        this.versionGenerator = versionGenerator;
        this.clock = clock;
        this.scope = scope;
        this.dataFile = dataFile;
        this.backupFile = backupFile;
        this.dataType = dataType;
    }

    public void upload(String data, JsonObject extraMeta) throws Exception {
        final long generated = clock.getEpochSecond();
        JsonObject rawMetadata = provider.getMetadata();
        boolean isFirstWrite = rawMetadata == null;
        Metadata metadata;
        CloudPath location;
        if (isFirstWrite) {
            metadata = new Metadata(new JsonObject());
            location = scope.resolve(new CloudPath(dataFile.toString()));
            metadata.setLocation(dataType, location);
        } else {
            metadata = new Metadata(rawMetadata);
            location = metadata.locationOf(dataType);
        }

        metadata.setVersion(versionGenerator.getVersion());
        metadata.setGenerated(generated);
        metadata.addExtra(extraMeta);

        if (!isFirstWrite) {
            fileManager.backupFile(location, backupFile, generated);
        }

        fileManager.uploadFile(location, dataFile, data);
        fileManager.uploadMetadata(metadata.getJson(), dataType, scope.getMetadataPath());

        provider.loadContent(provider.getMetadata());
    }

    public void upload(String data) throws Exception {
        upload(data, new JsonObject());
    }
}
