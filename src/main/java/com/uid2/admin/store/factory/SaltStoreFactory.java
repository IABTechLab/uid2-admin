package com.uid2.admin.store.factory;

import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.EncyptedSaltStoreWriter;
import com.uid2.admin.store.writer.StoreWriter;
import com.uid2.shared.cloud.TaggableCloudStorage;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.RotatingEncryptedSaltProvider;
import com.uid2.shared.store.RotatingSaltProvider;
import com.uid2.shared.store.reader.RotatingCloudEncryptionKeyProvider;
import com.uid2.shared.store.reader.StoreReader;
import com.uid2.shared.store.scope.EncryptedScope;
import io.vertx.core.json.JsonObject;

import java.util.Collection;

public class SaltStoreFactory implements EncryptedStoreFactory<Collection<RotatingSaltProvider.SaltSnapshot>> {
    JsonObject config;
    CloudPath rootMetadatapath;
    RotatingSaltProvider saltProvider;
    FileManager fileManager;
    TaggableCloudStorage taggableCloudStorage;
    VersionGenerator versionGenerator;
    RotatingCloudEncryptionKeyProvider cloudEncryptionKeyProvider;

    public SaltStoreFactory(JsonObject config, CloudPath rootMetadataPath, RotatingSaltProvider saltProvider, FileManager fileManager,
                            TaggableCloudStorage taggableCloudStorage, VersionGenerator versionGenerator,
                            RotatingCloudEncryptionKeyProvider cloudEncryptionKeyProvider) {
        this.config = config;
        this.rootMetadatapath = rootMetadataPath;
        this.saltProvider = saltProvider;
        this.fileManager = fileManager;
        this.taggableCloudStorage = taggableCloudStorage;
        this.versionGenerator = versionGenerator;
        this.cloudEncryptionKeyProvider = cloudEncryptionKeyProvider;
    }

    @Override
    public StoreWriter<Collection<RotatingSaltProvider.SaltSnapshot>> getEncryptedWriter(Integer siteId, boolean isPublic) {
        return new EncyptedSaltStoreWriter(config, saltProvider, fileManager, taggableCloudStorage, versionGenerator,
                new EncryptedScope(rootMetadatapath, siteId, isPublic), cloudEncryptionKeyProvider, siteId);
    }

    @Override
    public StoreReader<Collection<RotatingSaltProvider.SaltSnapshot>> getEncryptedReader(Integer siteId, boolean isPublic) {
        return new RotatingEncryptedSaltProvider(taggableCloudStorage,
                new EncryptedScope(rootMetadatapath, siteId, isPublic).getMetadataPath().toString(),
                cloudEncryptionKeyProvider);
    }

    @Override
    public RotatingCloudEncryptionKeyProvider getCloudEncryptionProvider() {
        return cloudEncryptionKeyProvider;
    }

    @Override
    public StoreReader<Collection<RotatingSaltProvider.SaltSnapshot>> getReader(Integer siteId) {
        return null;
    }

    @Override
    public StoreWriter<Collection<RotatingSaltProvider.SaltSnapshot>> getWriter(Integer siteId) {
        return null;
    }
}
