package com.uid2.admin.store.factory;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.KeyAclStoreWriter;
import com.uid2.admin.store.writer.StoreWriter;
import com.uid2.shared.auth.EncryptionKeyAcl;
import com.uid2.shared.cloud.ICloudStorage;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.reader.RotatingKeyAclProvider;
import com.uid2.shared.store.reader.StoreReader;
import com.uid2.shared.store.scope.GlobalScope;
import com.uid2.shared.store.scope.SiteScope;

import java.util.Map;

public class KeyAclStoreFactory implements StoreFactory<Map<Integer, EncryptionKeyAcl>> {
    private final ICloudStorage fileStreamProvider;
    private final CloudPath rootMetadataPath;
    private final ObjectWriter objectWriter;
    private final VersionGenerator versionGenerator;
    private final Clock clock;
    private final FileManager fileManager;
    private final RotatingKeyAclProvider globalReader;

    public KeyAclStoreFactory(ICloudStorage fileStreamProvider,
                              CloudPath rootMetadataPath,
                              ObjectWriter objectWriter,
                              VersionGenerator versionGenerator,
                              Clock clock,
                              FileManager fileManager) {
        this.fileStreamProvider = fileStreamProvider;
        this.rootMetadataPath = rootMetadataPath;
        this.objectWriter = objectWriter;
        this.versionGenerator = versionGenerator;
        this.clock = clock;
        this.fileManager = fileManager;
        GlobalScope globalScope = new GlobalScope(rootMetadataPath);
        globalReader = new RotatingKeyAclProvider(fileStreamProvider, globalScope);
    }

    public StoreReader<Map<Integer, EncryptionKeyAcl>> getReader(Integer siteId) {
        return new RotatingKeyAclProvider(fileStreamProvider, new SiteScope(rootMetadataPath, siteId));
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

    public RotatingKeyAclProvider getGlobalReader() {
        return globalReader;
    }
}
