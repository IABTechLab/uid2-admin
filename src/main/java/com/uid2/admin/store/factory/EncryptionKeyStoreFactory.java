package com.uid2.admin.store.factory;

import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.FileStorage;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.EncryptionKeyStoreWriter;
import com.uid2.shared.cloud.ICloudStorage;
import com.uid2.shared.model.EncryptionKey;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.reader.RotatingKeyStore;
import com.uid2.shared.store.scope.GlobalScope;
import com.uid2.shared.store.scope.SiteScope;

import java.util.Collection;

public class EncryptionKeyStoreFactory implements StoreFactory<Collection<EncryptionKey>> {
    private final ICloudStorage fileStreamProvider;
    private final CloudPath rootMetadataPath;
    private final VersionGenerator versionGenerator;
    private final Clock clock;
    private final FileManager fileManager;
    private final RotatingKeyStore globalReader;

    public EncryptionKeyStoreFactory(
            ICloudStorage fileStreamProvider,
            CloudPath rootMetadataPath,
            FileStorage fileStorage,
            VersionGenerator versionGenerator,
            Clock clock) {
        this.fileStreamProvider = fileStreamProvider;
        this.rootMetadataPath = rootMetadataPath;
        this.versionGenerator = versionGenerator;
        this.clock = clock;
        fileManager = new FileManager(fileStreamProvider, fileStorage);
        GlobalScope globalScope = new GlobalScope(rootMetadataPath);
        globalReader = new RotatingKeyStore(fileStreamProvider, globalScope);
    }

    @Override
    public RotatingKeyStore getReader(Integer siteId) {
        return new RotatingKeyStore(fileStreamProvider, new SiteScope(rootMetadataPath, siteId));
    }

    @Override
    public EncryptionKeyStoreWriter getWriter(Integer siteId) {
        return new EncryptionKeyStoreWriter(
                getReader(siteId),
                fileManager,
                versionGenerator,
                clock,
                new SiteScope(rootMetadataPath, siteId)
        );
    }

    public RotatingKeyStore getGlobalReader() {
        return globalReader;
    }
}
