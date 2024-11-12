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
import com.uid2.shared.store.reader.RotatingCloudEncryptionKeyProvider;
import com.uid2.shared.store.reader.StoreReader;
import com.uid2.shared.store.scope.EncryptedScope;
import com.uid2.shared.store.scope.GlobalScope;
import com.uid2.shared.store.scope.SiteScope;

import java.util.Map;

public class KeyAclStoreFactory implements EncryptedStoreFactory<Map<Integer, EncryptionKeyAcl>> {
    private final ICloudStorage fileStreamProvider;
    private final CloudPath rootMetadataPath;
    private final ObjectWriter objectWriter;
    private final VersionGenerator versionGenerator;
    private final Clock clock;
    private final FileManager fileManager;
    private final RotatingKeyAclProvider globalReader;
    private final RotatingCloudEncryptionKeyProvider cloudEncryptionKeyProvider;

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
            RotatingCloudEncryptionKeyProvider cloudEncryptionKeyProvider,
            FileManager fileManager) {
        this.fileStreamProvider = fileStreamProvider;
        this.rootMetadataPath = rootMetadataPath;
        this.objectWriter = objectWriter;
        this.versionGenerator = versionGenerator;
        this.clock = clock;
        this.fileManager = fileManager;
        this.cloudEncryptionKeyProvider = cloudEncryptionKeyProvider;
        GlobalScope globalScope = new GlobalScope(rootMetadataPath);
        globalReader = new RotatingKeyAclProvider(fileStreamProvider, globalScope);
    }

    public StoreReader<Map<Integer, EncryptionKeyAcl>> getReader(Integer siteId) {
        return new RotatingKeyAclProvider(fileStreamProvider, new SiteScope(rootMetadataPath, siteId));
    }

    public StoreReader<Map<Integer, EncryptionKeyAcl>> getEncryptedReader(Integer siteId, boolean isPublic) {
        return new RotatingKeyAclProvider(fileStreamProvider, new EncryptedScope(rootMetadataPath, siteId,isPublic), cloudEncryptionKeyProvider);
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
        return new KeyAclStoreWriter(
                getEncryptedReader(siteId,isPublic),
                fileManager,
                objectWriter,
                versionGenerator,
                clock,
                new EncryptedScope(rootMetadataPath, siteId, isPublic),
                cloudEncryptionKeyProvider
        );
    }

    public RotatingCloudEncryptionKeyProvider getCloudEncryptionProvider() {
        return this.cloudEncryptionKeyProvider;
    }


    public RotatingKeyAclProvider getGlobalReader() {
        return globalReader;
    }
}
