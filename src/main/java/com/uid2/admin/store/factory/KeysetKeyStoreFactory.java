package com.uid2.admin.store.factory;

import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.KeysetKeyStoreWriter;
import com.uid2.admin.store.writer.StoreWriter;
import com.uid2.shared.cloud.ICloudStorage;
import com.uid2.shared.model.KeysetKey;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.reader.RotatingKeysetKeyStore;
import com.uid2.shared.store.reader.RotatingCloudEncryptionKeyProvider;
import com.uid2.shared.store.scope.EncryptedScope;
import com.uid2.shared.store.scope.GlobalScope;
import com.uid2.shared.store.scope.SiteScope;


import java.util.Collection;

public class KeysetKeyStoreFactory implements EncryptedStoreFactory<Collection<KeysetKey>> {
    private final ICloudStorage fileStreamProvider;
    private final CloudPath rootMetadataPath;
    private final VersionGenerator versionGenerator;
    private final Clock clock;
    private final FileManager fileManager;
    private final RotatingKeysetKeyStore globalReader;
    private final RotatingCloudEncryptionKeyProvider cloudEncryptionKeyProvider;
    private final boolean enableKeyset;

    public KeysetKeyStoreFactory(
            ICloudStorage fileStreamProvider,
            CloudPath rootMetadataPath,
            VersionGenerator versionGenerator,
            Clock clock,
            FileManager fileManager,
            boolean enableKeyset) {
        this(fileStreamProvider, rootMetadataPath, versionGenerator, clock,fileManager,null, enableKeyset);
    }

    public KeysetKeyStoreFactory(
            ICloudStorage fileStreamProvider,
            CloudPath rootMetadataPath,
            VersionGenerator versionGenerator,
            Clock clock,
            FileManager fileManager,
            RotatingCloudEncryptionKeyProvider cloudEncryptionKeyProvider,
            boolean enableKeyset) {
        this.fileStreamProvider = fileStreamProvider;
        this.rootMetadataPath = rootMetadataPath;
        this.versionGenerator = versionGenerator;
        this.clock = clock;
        this.fileManager = fileManager;
        this.cloudEncryptionKeyProvider = cloudEncryptionKeyProvider;
        GlobalScope globalScope = new GlobalScope(rootMetadataPath);
        globalReader = new RotatingKeysetKeyStore(fileStreamProvider, globalScope);
        this.enableKeyset = enableKeyset;
    }

    @Override
    public RotatingKeysetKeyStore getReader(Integer siteId) {
        return new RotatingKeysetKeyStore(fileStreamProvider, new SiteScope(rootMetadataPath, siteId));
    }

    public RotatingKeysetKeyStore getEncryptedReader(Integer siteId, boolean isPublic) {
        return new RotatingKeysetKeyStore(fileStreamProvider, new EncryptedScope(rootMetadataPath, siteId,isPublic), cloudEncryptionKeyProvider);
    }

    @Override
    public StoreWriter<Collection<KeysetKey>> getWriter(Integer siteId) {
        return new KeysetKeyStoreWriter(
                getReader(siteId),
                fileManager,
                versionGenerator,
                clock,
                new SiteScope(rootMetadataPath, siteId),
                enableKeyset
        );
    }

    public StoreWriter<Collection<KeysetKey>> getEncryptedWriter(Integer siteId, boolean isPublic) {
        return new KeysetKeyStoreWriter(
                getEncryptedReader(siteId,isPublic),
                fileManager,
                versionGenerator,
                clock,
                new EncryptedScope(rootMetadataPath, siteId, isPublic),
                cloudEncryptionKeyProvider,
                enableKeyset
        );
    }

    public RotatingCloudEncryptionKeyProvider getCloudEncryptionProvider() {
        return this.cloudEncryptionKeyProvider;
    }

    public RotatingKeysetKeyStore getGlobalReader() { return globalReader; }
}
