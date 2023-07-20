package com.uid2.admin.store.factory;

import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.ClientSideKeypairStoreWriter;
import com.uid2.admin.store.writer.StoreWriter;
import com.uid2.shared.cloud.ICloudStorage;
import com.uid2.shared.model.ClientSideKeypair;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.reader.RotatingClientSideKeypairStore;
import com.uid2.shared.store.scope.GlobalScope;
import com.uid2.shared.store.scope.SiteScope;

import java.util.Collection;

public class ClientSideKeypairStoreFactory implements StoreFactory<Collection<ClientSideKeypair>> {
    private final ICloudStorage fileStreamProvider;
    private final CloudPath rootMetadataPath;
    private final VersionGenerator versionGenerator;
    private final Clock clock;
    private final FileManager fileManager;
    private final RotatingClientSideKeypairStore globalReader;

    public ClientSideKeypairStoreFactory(
            ICloudStorage fileStreamProvider,
            CloudPath rootMetadataPath,
            VersionGenerator versionGenerator,
            Clock clock,
            FileManager fileManager) {
        this.fileStreamProvider = fileStreamProvider;
        this.rootMetadataPath = rootMetadataPath;
        this.versionGenerator = versionGenerator;
        this.clock = clock;
        this.fileManager = fileManager;
        GlobalScope globalScope = new GlobalScope(rootMetadataPath);
        globalReader = new RotatingClientSideKeypairStore(fileStreamProvider, globalScope);
    }

    @Override
    public RotatingClientSideKeypairStore getReader(Integer siteId) {
        return new RotatingClientSideKeypairStore(fileStreamProvider, new SiteScope(rootMetadataPath, siteId));
    }

    @Override
    public StoreWriter<Collection<ClientSideKeypair>> getWriter(Integer siteId) {
        return new ClientSideKeypairStoreWriter(
                getReader(siteId),
                fileManager,
                versionGenerator,
                clock,
                new SiteScope(rootMetadataPath, siteId)
        );
    }

    public RotatingClientSideKeypairStore getGlobalReader() { return globalReader; }
}
