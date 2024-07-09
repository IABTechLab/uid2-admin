package com.uid2.admin.store.factory;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.*;
import com.uid2.shared.auth.EncryptionKeyAcl;
import com.uid2.shared.cloud.ICloudStorage;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.reader.RotatingKeyAclProvider;
import com.uid2.shared.store.reader.RotatingS3KeyProvider;
import com.uid2.shared.store.reader.StoreReader;
import com.uid2.shared.store.scope.EncryptedScope;
import com.uid2.shared.store.scope.GlobalScope;
import com.uid2.shared.store.scope.SiteScope;
import com.uid2.shared.store.scope.StoreScope;

import java.util.Map;

public class KeyAclStoreFactory implements EncryptedStoreFactory<Map<Integer, EncryptionKeyAcl>> {
    private final ICloudStorage fileStreamProvider;
    private final CloudPath rootMetadataPath;
    private final ObjectWriter objectWriter;
    private final VersionGenerator versionGenerator;
    private final Clock clock;
    private final FileManager fileManager;
    private final RotatingKeyAclProvider globalReader;
    private final RotatingS3KeyProvider s3KeyProvider;

    public KeyAclStoreFactory(
            ICloudStorage fileStreamProvider,
            CloudPath rootMetadataPath,
            ObjectWriter objectWriter,
            VersionGenerator versionGenerator,
            Clock clock,
            FileManager fileManager) {
        this(fileStreamProvider, rootMetadataPath, objectWriter, versionGenerator, clock, null, fileManager);
    }

    public KeyAclStoreFactory(
            ICloudStorage fileStreamProvider,
            CloudPath rootMetadataPath,
            ObjectWriter objectWriter,
            VersionGenerator versionGenerator,
            Clock clock,
            RotatingS3KeyProvider s3KeyProvider,
            FileManager fileManager) {
        this.fileStreamProvider = fileStreamProvider;
        this.rootMetadataPath = rootMetadataPath;
        this.objectWriter = objectWriter;
        this.versionGenerator = versionGenerator;
        this.clock = clock;
        this.fileManager = fileManager;
        this.s3KeyProvider = s3KeyProvider;
        GlobalScope globalScope = new GlobalScope(rootMetadataPath);
        globalReader = new RotatingKeyAclProvider(fileStreamProvider, globalScope);
    }

    public StoreReader<Map<Integer, EncryptionKeyAcl>> getReader(Integer siteId) {
        return new RotatingKeyAclProvider(fileStreamProvider, new SiteScope(rootMetadataPath, siteId));
    }

    public StoreReader<Map<Integer, EncryptionKeyAcl>> getEncryptedReader(Integer siteId, boolean isPublic) {
        return new RotatingKeyAclProvider(fileStreamProvider, new EncryptedScope(rootMetadataPath, siteId,isPublic),s3KeyProvider);
    }
    public StoreWriter<Map<Integer, EncryptionKeyAcl>> getWriter(Integer siteId) {
        return new KeyAclStoreWriter(
                getReader(siteId),
                fileManager,
                objectWriter,
                versionGenerator,
                clock,
                new SiteScope(rootMetadataPath, siteId)
        );
    }

    public StoreWriter<Map<Integer, EncryptionKeyAcl>> getEncryptedWriter(Integer siteId, boolean isPublic) {
        StoreScope encryptedScope = new EncryptedScope(rootMetadataPath, siteId, isPublic);
        return new KeyAclStoreWriter(
                getEncryptedReader(siteId,isPublic),
                fileManager,
                objectWriter,
                versionGenerator,
                clock,
                encryptedScope,
                s3KeyProvider
        );
    }

    public RotatingS3KeyProvider getS3Provider() {
        return this.s3KeyProvider;
    }


    public RotatingKeyAclProvider getGlobalReader() {
        return globalReader;
    }
}
