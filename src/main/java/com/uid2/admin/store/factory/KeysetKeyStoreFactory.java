package com.uid2.admin.store.factory;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.legacy.RotatingLegacyClientKeyProvider;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.FileName;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.EncryptedScopedStoreWriter;
import com.uid2.admin.store.writer.KeysetKeyStoreWriter;
import com.uid2.admin.store.writer.KeysetStoreWriter;
import com.uid2.admin.store.writer.StoreWriter;
import com.uid2.shared.auth.Keyset;
import com.uid2.shared.cloud.ICloudStorage;
import com.uid2.shared.model.KeysetKey;
import com.uid2.shared.model.Site;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.reader.RotatingKeysetKeyStore;
import com.uid2.shared.store.reader.RotatingS3KeyProvider;
import com.uid2.shared.store.reader.RotatingSiteStore;
import com.uid2.shared.store.reader.StoreReader;
import com.uid2.shared.store.scope.EncryptedScope;
import com.uid2.shared.store.scope.GlobalScope;
import com.uid2.shared.store.scope.SiteScope;
import com.uid2.shared.store.scope.StoreScope;


import java.util.Collection;
import java.util.Map;

public class KeysetKeyStoreFactory implements EncryptedStoreFactory<Collection<KeysetKey>> {
    private final ICloudStorage fileStreamProvider;
    private final CloudPath rootMetadataPath;
    private final VersionGenerator versionGenerator;
    private final Clock clock;
    private final FileManager fileManager;
    private final RotatingKeysetKeyStore globalReader;
    private final RotatingS3KeyProvider s3KeyProvider;
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
            RotatingS3KeyProvider s3KeyProvider,
            boolean enableKeyset) {
        this.fileStreamProvider = fileStreamProvider;
        this.rootMetadataPath = rootMetadataPath;
        this.versionGenerator = versionGenerator;
        this.clock = clock;
        this.fileManager = fileManager;
        this.s3KeyProvider = s3KeyProvider;
        GlobalScope globalScope = new GlobalScope(rootMetadataPath);
        globalReader = new RotatingKeysetKeyStore(fileStreamProvider, globalScope);
        this.enableKeyset = enableKeyset;
    }

    @Override
    public RotatingKeysetKeyStore getReader(Integer siteId) {
        return new RotatingKeysetKeyStore(fileStreamProvider, new SiteScope(rootMetadataPath, siteId));
    }

    public RotatingKeysetKeyStore getEncryptedReader(Integer siteId, boolean isPublic) {
        return new RotatingKeysetKeyStore(fileStreamProvider, new EncryptedScope(rootMetadataPath, siteId,isPublic), s3KeyProvider);
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
                s3KeyProvider,
                enableKeyset
        );
    }

    public RotatingS3KeyProvider getS3Provider() {
        return this.s3KeyProvider;
    }

    public RotatingKeysetKeyStore getGlobalReader() { return globalReader; }
}
