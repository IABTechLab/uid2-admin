package com.uid2.admin.store.factory;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.model.Site;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.reader.RotatingSiteStore;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.SiteStoreWriter;
import com.uid2.admin.store.writer.StoreWriter;
import com.uid2.shared.cloud.ICloudStorage;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.reader.StoreReader;
import com.uid2.shared.store.scope.GlobalScope;
import com.uid2.shared.store.scope.SiteScope;

import java.util.Collection;

public class SiteStoreFactory implements StoreFactory<Collection<Site>> {
    private final ICloudStorage fileStreamProvider;
    private final CloudPath rootMetadataPath;
    private final ObjectWriter objectWriter;
    private final VersionGenerator versionGenerator;
    private final Clock clock;
    private final FileManager fileManager;
    private final RotatingSiteStore globalReader;
    private final SiteStoreWriter globalWriter;

    public SiteStoreFactory(
            ICloudStorage fileStreamProvider,
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
        globalReader = new RotatingSiteStore(fileStreamProvider, globalScope);
        globalWriter = new SiteStoreWriter(
                globalReader,
                this.fileManager,
                objectWriter,
                versionGenerator,
                clock,
                globalScope
        );
    }

    @Override
    public StoreReader<Collection<Site>> getReader(Integer siteId) {
        return new RotatingSiteStore(fileStreamProvider, new SiteScope(rootMetadataPath, siteId));
    }

    @Override
    public StoreWriter<Collection<Site>> getWriter(Integer siteId) {
        return new SiteStoreWriter(
                getReader(siteId),
                fileManager,
                objectWriter,
                versionGenerator,
                clock,
                new SiteScope(rootMetadataPath, siteId)
        );
    }

    public RotatingSiteStore getGlobalReader() {
        return globalReader;
    }

    public StoreWriter<Collection<Site>> getGlobalWriter() {
        return globalWriter;
    }
}
