package com.uid2.admin.store.factory;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.FileName;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.EncryptedScopedStoreWriter;
import com.uid2.admin.store.writer.EncryptionKeyStoreWriter;
import com.uid2.admin.store.writer.KeysetStoreWriter;
import com.uid2.admin.store.writer.StoreWriter;
import com.uid2.shared.auth.Keyset;
import com.uid2.shared.cloud.ICloudStorage;
import com.uid2.shared.model.EncryptionKey;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.reader.RotatingKeyStore;
import com.uid2.shared.store.reader.RotatingS3KeyProvider;
import com.uid2.shared.store.scope.EncryptedScope;
import com.uid2.shared.store.scope.GlobalScope;
import com.uid2.shared.store.scope.SiteScope;
import com.uid2.shared.store.scope.StoreScope;

import java.util.Collection;
import java.util.Map;

public class EncryptionKeyStoreFactory implements EncryptedStoreFactory<Collection<EncryptionKey>> {
    private final ICloudStorage fileStreamProvider;
    private final CloudPath rootMetadataPath;
    private final VersionGenerator versionGenerator;
    private final Clock clock;
    private final FileManager fileManager;
    private final RotatingKeyStore globalReader;
    private final RotatingS3KeyProvider s3KeyProvider;

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
            RotatingS3KeyProvider s3KeyProvider,
            FileManager fileManager) {
        this.fileStreamProvider = fileStreamProvider;
        this.rootMetadataPath = rootMetadataPath;
        this.versionGenerator = versionGenerator;
        this.clock = clock;
        this.fileManager = fileManager;
        this.s3KeyProvider = s3KeyProvider;
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

    public EncryptionKeyStoreWriter getEncryptedWriter(Integer siteId) {
        StoreScope encryptedScope = new EncryptedScope(rootMetadataPath, siteId);
        return new EncryptionKeyStoreWriter(
                getReader(siteId),
                fileManager,
                versionGenerator,
                clock,
                encryptedScope,
                s3KeyProvider
        );
    }

    public RotatingS3KeyProvider getS3Provider() {
        return this.s3KeyProvider;
    }


    public RotatingKeyStore getGlobalReader() {
        return globalReader;
    }
}
