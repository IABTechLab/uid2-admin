package com.uid2.admin.store.factory;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.legacy.LegacyClientKey;
import com.uid2.admin.legacy.LegacyClientKeyStoreWriter;
import com.uid2.admin.legacy.RotatingLegacyClientKeyProvider;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.StoreWriter;
import com.uid2.shared.cloud.ICloudStorage;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.scope.GlobalScope;
import com.uid2.shared.store.scope.SiteScope;

import java.util.Collection;

public class ClientKeyStoreFactory implements StoreFactory<Collection<LegacyClientKey>> {
    private final ICloudStorage fileStreamProvider;
    private final CloudPath rootMetadataPath;
    private final ObjectWriter objectWriter;
    private final VersionGenerator versionGenerator;
    private final Clock clock;
    private final FileManager fileManager;
    private final RotatingLegacyClientKeyProvider globalReader;
    private final LegacyClientKeyStoreWriter globalWriter;

    public ClientKeyStoreFactory(
            ICloudStorage fileStreamProvider,
            CloudPath rootMetadataPath,
            ObjectWriter objectWriter,
            VersionGenerator versionGenerator,
            Clock clock,
            FileManager fileManager)  {
        this.fileStreamProvider = fileStreamProvider;
        this.rootMetadataPath = rootMetadataPath;
        this.objectWriter = objectWriter;
        this.versionGenerator = versionGenerator;
        this.clock = clock;
        this.fileManager = fileManager;
        GlobalScope globalScope = new GlobalScope(rootMetadataPath);
        globalReader = new RotatingLegacyClientKeyProvider(fileStreamProvider, globalScope);
        globalWriter = new LegacyClientKeyStoreWriter(
                globalReader,
                this.fileManager,
                objectWriter,
                versionGenerator,
                clock,
                globalScope
        );
    }

    public RotatingLegacyClientKeyProvider getReader(Integer siteId) {
        return new RotatingLegacyClientKeyProvider(fileStreamProvider, new SiteScope(rootMetadataPath, siteId));
    }

    public LegacyClientKeyStoreWriter getWriter(Integer siteId) {
        return new LegacyClientKeyStoreWriter(
                getReader(siteId),
                fileManager,
                objectWriter,
                versionGenerator,
                clock,
                new SiteScope(rootMetadataPath, siteId)
        );
    }

    public RotatingLegacyClientKeyProvider getGlobalReader() {
        return globalReader;
    }

    public StoreWriter<Collection<LegacyClientKey>> getGlobalWriter() {
        return globalWriter;
    }
}
