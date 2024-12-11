package com.uid2.admin.store.factory;

import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.EncyptedSaltStoreWriter;
import com.uid2.admin.store.writer.StoreWriter;
import com.uid2.shared.Const;
import com.uid2.shared.cloud.TaggableCloudStorage;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.EncryptedRotatingSaltProvider;
import com.uid2.shared.store.RotatingSaltProvider;
import com.uid2.shared.store.reader.RotatingCloudEncryptionKeyProvider;
import com.uid2.shared.store.reader.StoreReader;
import com.uid2.shared.store.scope.EncryptedScope;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public class SaltStoreFactory implements EncryptedStoreFactory<Collection<RotatingSaltProvider.SaltSnapshot>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SaltStoreFactory.class);

    JsonObject config;
    CloudPath rootMetadatapath;
    FileManager fileManager;
    TaggableCloudStorage taggableCloudStorage;
    VersionGenerator versionGenerator;
    RotatingCloudEncryptionKeyProvider cloudEncryptionKeyProvider;

    public SaltStoreFactory(JsonObject config, CloudPath rootMetadataPath, FileManager fileManager,
                            TaggableCloudStorage taggableCloudStorage, VersionGenerator versionGenerator,
                            RotatingCloudEncryptionKeyProvider cloudEncryptionKeyProvider) {
        this.config = config;
        this.rootMetadatapath = rootMetadataPath;
        this.fileManager = fileManager;
        this.taggableCloudStorage = taggableCloudStorage;
        this.versionGenerator = versionGenerator;
        this.cloudEncryptionKeyProvider = cloudEncryptionKeyProvider;
    }

    @Override
    public StoreWriter<Collection<RotatingSaltProvider.SaltSnapshot>> getEncryptedWriter(Integer siteId, boolean isPublic) {
        EncryptedScope scope = new EncryptedScope(rootMetadatapath, siteId, isPublic);
        EncryptedRotatingSaltProvider saltProvider = new EncryptedRotatingSaltProvider(taggableCloudStorage,
                scope.resolve(new CloudPath(config.getString(Const.Config.SaltsMetadataPathProp))).toString(), cloudEncryptionKeyProvider );
        return new EncyptedSaltStoreWriter(config, saltProvider, fileManager, taggableCloudStorage, versionGenerator, scope, cloudEncryptionKeyProvider, siteId);
    }

    @Override
    public StoreReader<Collection<RotatingSaltProvider.SaltSnapshot>> getEncryptedReader(Integer siteId, boolean isPublic) {
        LOGGER.warn("getEncryptedReader called on SaltStoreFactory. This method is not implemented.");
        return null;
    }

    @Override
    public RotatingCloudEncryptionKeyProvider getCloudEncryptionProvider() {
        return cloudEncryptionKeyProvider;
    }

    @Override
    public StoreReader<Collection<RotatingSaltProvider.SaltSnapshot>> getReader(Integer siteId) {
        LOGGER.warn("getReader called on SaltStoreFactory. This method is not implemented.");
        return null;
    }

    @Override
    public StoreWriter<Collection<RotatingSaltProvider.SaltSnapshot>> getWriter(Integer siteId) {
        LOGGER.warn("getWriter called on SaltStoreFactory. This method is not implemented.");
        return null;
    }
}
