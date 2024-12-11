package com.uid2.admin.store.factory;

import com.uid2.admin.store.writer.ClientSideKeypairStoreWriter;
import com.uid2.admin.store.writer.StoreWriter;
import com.uid2.shared.model.ClientSideKeypair;
import com.uid2.shared.store.reader.RotatingClientSideKeypairStore;
import com.uid2.shared.store.reader.RotatingCloudEncryptionKeyProvider;
import com.uid2.shared.store.reader.StoreReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.shared.cloud.ICloudStorage;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.scope.EncryptedScope;
import com.uid2.shared.store.scope.GlobalScope;
import com.uid2.shared.store.scope.SiteScope;

import java.util.Collection;

public class ClientSideKeypairStoreFactory implements EncryptedStoreFactory<Collection<ClientSideKeypair>> {
    private final ICloudStorage fileStreamProvider;
    private final CloudPath rootMetadataPath;
    private final VersionGenerator versionGenerator;
    private final Clock clock;
    private final FileManager fileManager;
    private final RotatingClientSideKeypairStore globalReader;
    private final RotatingCloudEncryptionKeyProvider cloudEncryptionKeyProvider;

    public ClientSideKeypairStoreFactory(
            ICloudStorage fileStreamProvider,
            CloudPath rootMetadataPath,
            VersionGenerator versionGenerator,
            Clock clock,
            RotatingCloudEncryptionKeyProvider cloudEncryptionKeyProvider,
            FileManager fileManager)  {
        this.fileStreamProvider = fileStreamProvider;
        this.rootMetadataPath = rootMetadataPath;
        this.versionGenerator = versionGenerator;
        this.clock = clock;
        this.cloudEncryptionKeyProvider = cloudEncryptionKeyProvider;
        this.fileManager = fileManager;
        GlobalScope globalScope = new GlobalScope(rootMetadataPath);
        globalReader = new RotatingClientSideKeypairStore(fileStreamProvider, globalScope);
    }

    public RotatingClientSideKeypairStore getGlobalReader() {
        return globalReader;
    }

    @Override
    public StoreWriter<Collection<ClientSideKeypair>> getEncryptedWriter(Integer siteId, boolean isPublic) {
        return new ClientSideKeypairStoreWriter(getEncryptedReader(siteId, isPublic),
                fileManager,
                versionGenerator,
                clock,
                new EncryptedScope(rootMetadataPath, siteId, isPublic));
    }

    @Override
    public StoreReader<Collection<ClientSideKeypair>> getEncryptedReader(Integer siteId, boolean isPublic) {
        return new RotatingClientSideKeypairStore(fileStreamProvider, new EncryptedScope(rootMetadataPath, siteId, isPublic));
    }

    @Override
    public RotatingCloudEncryptionKeyProvider getCloudEncryptionProvider() {
        return cloudEncryptionKeyProvider;
    }

    @Override
    public StoreReader<Collection<ClientSideKeypair>> getReader(Integer siteId) {
        return new RotatingClientSideKeypairStore(fileStreamProvider, new SiteScope(rootMetadataPath, siteId));
    }

    @Override
    public StoreWriter<Collection<ClientSideKeypair>> getWriter(Integer siteId) {
        return new ClientSideKeypairStoreWriter(getReader(siteId), fileManager, versionGenerator, clock, new SiteScope(rootMetadataPath, siteId));
    }
}
