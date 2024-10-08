package com.uid2.admin.store.factory;

import com.fasterxml.jackson.databind.ObjectWriter;
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
import com.uid2.shared.model.S3Key;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.reader.*;
import com.uid2.shared.store.scope.GlobalScope;
import com.uid2.shared.store.scope.SiteScope;
import com.uid2.shared.store.scope.StoreScope;
import com.uid2.shared.store.scope.EncryptedScope;


import java.util.Collection;
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
    private final RotatingS3KeyProvider s3KeyProvider;
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
                              RotatingS3KeyProvider s3KeyProvider,
                              boolean enableKeysets) {
        this.fileStreamProvider = fileStreamProvider;
        this.rootMetadataPath = rootMetadataPath;
        this.objectWriter = objectWriter;
        this.versionGenerator = versionGenerator;
        this.clock = clock;
        this.fileManager = fileManager;
        this.s3KeyProvider = s3KeyProvider;
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
        return new RotatingKeysetProvider(fileStreamProvider, new EncryptedScope(rootMetadataPath, siteId,isPublic),s3KeyProvider);
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
                s3KeyProvider,
                enableKeysets
        );
    }

    public RotatingS3KeyProvider getS3Provider() {
        return this.s3KeyProvider;
    }

    public RotatingKeysetProvider getGlobalReader() { return globalReader; }

    public StoreWriter<Map<Integer, Keyset>> getGlobalWriter() { return globalWriter; }
}
