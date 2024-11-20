package com.uid2.admin.store.factory;

import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.EncryptionKeyStoreWriter;
import com.uid2.shared.cloud.ICloudStorage;
import com.uid2.shared.model.EncryptionKey;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.reader.RotatingKeyStore;
import com.uid2.shared.store.reader.RotatingCloudEncryptionKeyProvider;
import com.uid2.shared.store.scope.EncryptedScope;
import com.uid2.shared.store.scope.GlobalScope;
import com.uid2.shared.store.scope.SiteScope;

import java.util.Collection;

public class EncryptionKeyStoreFactory implements EncryptedStoreFactory<Collection<EncryptionKey>> {
    private final ICloudStorage fileStreamProvider;
    private final CloudPath rootMetadataPath;
    private final VersionGenerator versionGenerator;
    private final Clock clock;
    private final FileManager fileManager;
    private final RotatingKeyStore globalReader;
    private final RotatingCloudEncryptionKeyProvider cloudEncryptionKeyProvider;

    public EncryptionKeyStoreFactory(
            ICloudStorage fileStreamProvider,
            CloudPath rootMetadataPath,
            VersionGenerator versionGenerator,
            Clock clock,
            FileManager fileManager) {
        this(fileStreamProvider, rootMetadataPath, versionGenerator, clock, null, fileManager);
    }

    public EncryptionKeyStoreFactory(
            ICloudStorage fileStreamProvider,
            CloudPath rootMetadataPath,
            VersionGenerator versionGenerator,
            Clock clock,
            RotatingCloudEncryptionKeyProvider cloudEncryptionKeyProvider,
            FileManager fileManager) {
        this.fileStreamProvider = fileStreamProvider;
        this.rootMetadataPath = rootMetadataPath;
        this.versionGenerator = versionGenerator;
        this.clock = clock;
        this.fileManager = fileManager;
        this.cloudEncryptionKeyProvider = cloudEncryptionKeyProvider;
        GlobalScope globalScope = new GlobalScope(rootMetadataPath);
        globalReader = new RotatingKeyStore(fileStreamProvider, globalScope);
    }

    @Override
    public RotatingKeyStore getReader(Integer siteId) {
        return new RotatingKeyStore(fileStreamProvider, new SiteScope(rootMetadataPath, siteId));
    }

    public RotatingKeyStore getEncryptedReader(Integer siteId, boolean isPublic) {
        return new RotatingKeyStore(fileStreamProvider, new EncryptedScope(rootMetadataPath, siteId, isPublic), cloudEncryptionKeyProvider);
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

    public EncryptionKeyStoreWriter getEncryptedWriter(Integer siteId,boolean isPublic) {
        return new EncryptionKeyStoreWriter(
                getEncryptedReader(siteId,isPublic),
                fileManager,
                versionGenerator,
                clock,
                new EncryptedScope(rootMetadataPath, siteId, isPublic),
                cloudEncryptionKeyProvider
        );
    }

    public RotatingCloudEncryptionKeyProvider getCloudEncryptionProvider() {
        return this.cloudEncryptionKeyProvider;
    }


    public RotatingKeyStore getGlobalReader() {
        return globalReader;
    }
}
