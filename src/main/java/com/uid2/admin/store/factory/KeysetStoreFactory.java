package com.uid2.admin.store.factory;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.KeysetStoreWriter;
import com.uid2.admin.store.writer.StoreWriter;
import com.uid2.shared.auth.Keyset;
import com.uid2.shared.cloud.ICloudStorage;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.reader.*;
import com.uid2.shared.store.scope.GlobalScope;
import com.uid2.shared.store.scope.SiteScope;
import com.uid2.shared.store.scope.EncryptedScope;


import java.util.Map;

public class KeysetStoreFactory implements EncryptedStoreFactory<Map<Integer, Keyset>> {
    private final ICloudStorage fileStreamProvider;
    private final CloudPath rootMetadataPath;
    private final ObjectWriter objectWriter;
    private final VersionGenerator versionGenerator;
    private final Clock clock;
    private final FileManager fileManager;
    private final RotatingKeysetProvider globalReader;
    private final KeysetStoreWriter globalWriter;
    private final RotatingCloudEncryptionKeyProvider cloudEncryptionKeyProvider;
    private final boolean enableKeysets;

    public KeysetStoreFactory(ICloudStorage fileStreamProvider,
                              CloudPath rootMetadataPath,
                              ObjectWriter objectWriter,
                              VersionGenerator versionGenerator,
                              Clock clock,
                              FileManager fileManager,
                              boolean enableKeysets) {
        this(fileStreamProvider, rootMetadataPath, objectWriter, versionGenerator, clock,  fileManager,null,enableKeysets);
    }

    public KeysetStoreFactory(ICloudStorage fileStreamProvider,
                              CloudPath rootMetadataPath,
                              ObjectWriter objectWriter,
                              VersionGenerator versionGenerator,
                              Clock clock,
                              FileManager fileManager,
                              RotatingCloudEncryptionKeyProvider cloudEncryptionKeyProvider,
                              boolean enableKeysets) {
        this.fileStreamProvider = fileStreamProvider;
        this.rootMetadataPath = rootMetadataPath;
        this.objectWriter = objectWriter;
        this.versionGenerator = versionGenerator;
        this.clock = clock;
        this.fileManager = fileManager;
        this.cloudEncryptionKeyProvider = cloudEncryptionKeyProvider;
        GlobalScope globalScope = new GlobalScope(rootMetadataPath);
        globalReader = new RotatingKeysetProvider(fileStreamProvider, globalScope);
        globalWriter = new KeysetStoreWriter(
                globalReader,
                this.fileManager,
                objectWriter,
                versionGenerator,
                clock,
                globalScope,
                enableKeysets
        );
        this.enableKeysets = enableKeysets;
    }

    @Override
    public StoreReader<Map<Integer, Keyset>> getReader(Integer siteId) {
        return new RotatingKeysetProvider(fileStreamProvider, new SiteScope(rootMetadataPath, siteId));
    }

    public StoreReader<Map<Integer, Keyset>> getEncryptedReader(Integer siteId, boolean isPublic) {
        return new RotatingKeysetProvider(fileStreamProvider, new EncryptedScope(rootMetadataPath, siteId,isPublic), cloudEncryptionKeyProvider);
    }

    @Override
    public StoreWriter<Map<Integer, Keyset>> getWriter(Integer siteId) {
        return new KeysetStoreWriter(
                getReader(siteId),
                fileManager,
                objectWriter,
                versionGenerator,
                clock,
                new SiteScope(rootMetadataPath, siteId),
                enableKeysets
        );
    }

    public StoreWriter<Map<Integer, Keyset>> getEncryptedWriter(Integer siteId, boolean isPublic) {
        return new KeysetStoreWriter(
                getEncryptedReader(siteId,isPublic),
                fileManager,
                objectWriter,
                versionGenerator,
                clock,
                new EncryptedScope(rootMetadataPath, siteId, isPublic),
                cloudEncryptionKeyProvider,
                enableKeysets
        );
    }

    public RotatingCloudEncryptionKeyProvider getCloudEncryptionProvider() {
        return this.cloudEncryptionKeyProvider;
    }

    public RotatingKeysetProvider getGlobalReader() { return globalReader; }

    public StoreWriter<Map<Integer, Keyset>> getGlobalWriter() { return globalWriter; }
}
