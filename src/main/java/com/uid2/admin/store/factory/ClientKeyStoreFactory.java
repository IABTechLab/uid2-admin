package com.uid2.admin.store.factory;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.FileStorage;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.ClientKeyStoreWriter;
import com.uid2.admin.store.writer.StoreWriter;
import com.uid2.shared.auth.ClientKey;
import com.uid2.shared.cloud.ICloudStorage;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.reader.RotatingClientKeyProvider;
import com.uid2.shared.store.scope.GlobalScope;
import com.uid2.shared.store.scope.SiteScope;

import java.util.Collection;

public class ClientKeyStoreFactory implements StoreFactory<Collection<ClientKey>> {
    private final ICloudStorage fileStreamProvider;
    private final CloudPath rootMetadataPath;
    private final ObjectWriter objectWriter;
    private final VersionGenerator versionGenerator;
    private final Clock clock;
    private final FileManager fileManager;
    private final RotatingClientKeyProvider globalReader;
    private final ClientKeyStoreWriter globalWriter;

    public ClientKeyStoreFactory(
            ICloudStorage fileStreamProvider,
            CloudPath rootMetadataPath,
            FileStorage fileStorage,
            ObjectWriter objectWriter,
            VersionGenerator versionGenerator,
            Clock clock)  {
        this.fileStreamProvider = fileStreamProvider;
        this.rootMetadataPath = rootMetadataPath;
        this.objectWriter = objectWriter;
        this.versionGenerator = versionGenerator;
        this.clock = clock;
        fileManager = new FileManager(fileStreamProvider, fileStorage);
        GlobalScope globalScope = new GlobalScope(rootMetadataPath);
        globalReader = new RotatingClientKeyProvider(fileStreamProvider, globalScope);
        globalWriter = new ClientKeyStoreWriter(
                globalReader,
                fileManager,
                objectWriter,
                versionGenerator,
                clock,
                globalScope
        );
    }

    public RotatingClientKeyProvider getReader(Integer siteId) {
        return new RotatingClientKeyProvider(fileStreamProvider, new SiteScope(rootMetadataPath, siteId));
    }

    public ClientKeyStoreWriter getWriter(Integer siteId) {
        return new ClientKeyStoreWriter(
                getReader(siteId),
                fileManager,
                objectWriter,
                versionGenerator,
                clock,
                new SiteScope(rootMetadataPath, siteId)
        );
    }

    public RotatingClientKeyProvider getGlobalReader() {
        return globalReader;
    }

    public StoreWriter<Collection<ClientKey>> getGlobalWriter() {
        return globalWriter;
    }
}
