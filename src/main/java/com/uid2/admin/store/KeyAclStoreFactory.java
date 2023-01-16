package com.uid2.admin.store;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.store.reader.RotatingSiteStore;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.EncryptionKeyStoreWriter;
import com.uid2.admin.store.writer.KeyAclStoreWriter;
import com.uid2.admin.store.writer.SiteStoreWriter;
import com.uid2.shared.cloud.ICloudStorage;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.reader.RotatingKeyAclProvider;
import com.uid2.shared.store.reader.RotatingKeyStore;
import com.uid2.shared.store.scope.GlobalScope;
import com.uid2.shared.store.scope.SiteScope;

public class KeyAclStoreFactory {
    private final ICloudStorage fileStreamProvider;
    private final CloudPath rootMetadataPath;
    private final ObjectWriter objectWriter;
    private final VersionGenerator versionGenerator;
    private final Clock clock;
    private final FileManager fileManager;
    private final RotatingKeyAclProvider globalReader;
    private final KeyAclStoreWriter globalWriter;

    public KeyAclStoreFactory(
            ICloudStorage fileStreamProvider,
            CloudPath rootMetadataPath,
            FileStorage fileStorage,
            ObjectWriter objectWriter,
            VersionGenerator versionGenerator,
            Clock clock) {
        this.fileStreamProvider = fileStreamProvider;
        this.rootMetadataPath = rootMetadataPath;
        this.objectWriter = objectWriter;
        this.versionGenerator = versionGenerator;
        this.clock = clock;
        fileManager = new FileManager(fileStreamProvider, fileStorage);
        GlobalScope globalScope = new GlobalScope(rootMetadataPath);
        globalReader = new RotatingKeyAclProvider(fileStreamProvider, globalScope);
        globalWriter = new KeyAclStoreWriter(
                globalReader,
                fileManager,
                objectWriter,
                versionGenerator,
                clock,
                globalScope
        );
    }

    public RotatingKeyAclProvider getReader(Integer siteId) {
        return new RotatingKeyAclProvider(fileStreamProvider, new SiteScope(rootMetadataPath, siteId));
    }

    public KeyAclStoreWriter getWriter(Integer siteId) {
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

    public KeyAclStoreWriter getGlobalWriter() {
        return globalWriter;
    }
}
