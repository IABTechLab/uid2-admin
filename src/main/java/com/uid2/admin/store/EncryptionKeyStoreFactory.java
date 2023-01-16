package com.uid2.admin.store;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.store.reader.RotatingSiteStore;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.ClientKeyStoreWriter;
import com.uid2.admin.store.writer.EncryptionKeyStoreWriter;
import com.uid2.admin.store.writer.SiteStoreWriter;
import com.uid2.shared.cloud.ICloudStorage;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.reader.RotatingClientKeyProvider;
import com.uid2.shared.store.reader.RotatingKeyStore;
import com.uid2.shared.store.scope.GlobalScope;
import com.uid2.shared.store.scope.SiteScope;

public class EncryptionKeyStoreFactory {
    private final ICloudStorage fileStreamProvider;
    private final CloudPath rootMetadataPath;
    private final ObjectWriter objectWriter;
    private final VersionGenerator versionGenerator;
    private final Clock clock;
    private final FileManager fileManager;
    private final RotatingKeyStore globalReader;
    private final EncryptionKeyStoreWriter globalWriter;

    public EncryptionKeyStoreFactory(
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
        globalReader = new RotatingKeyStore(fileStreamProvider, globalScope);
        globalWriter = new EncryptionKeyStoreWriter(
                globalReader,
                fileManager,
                versionGenerator,
                clock,
                globalScope
        );
    }

    public RotatingKeyStore getReader(Integer siteId) {
        return new RotatingKeyStore(fileStreamProvider, new SiteScope(rootMetadataPath, siteId));
    }

    public EncryptionKeyStoreWriter getWriter(Integer siteId) {
        return new EncryptionKeyStoreWriter(
                getReader(siteId),
                fileManager,
                versionGenerator,
                clock,
                new SiteScope(rootMetadataPath, siteId)
        );
    }

    public RotatingKeyStore getGlobalReader() {
        return globalReader;
    }

    public EncryptionKeyStoreWriter getGlobalWriter() {
        return globalWriter;
    }
}
