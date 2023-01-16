package com.uid2.admin.store;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.model.Site;
import com.uid2.admin.store.reader.RotatingSiteStore;
import com.uid2.admin.store.reader.StoreReader;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.SiteStoreWriter;
import com.uid2.shared.cloud.ICloudStorage;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.scope.GlobalScope;
import com.uid2.shared.store.scope.SiteScope;

public class SiteStoreFactory {
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
        globalReader = new RotatingSiteStore(fileStreamProvider, globalScope);
        globalWriter = new SiteStoreWriter(
                globalReader,
                fileManager,
                objectWriter,
                versionGenerator,
                clock,
                globalScope
        );
    }

    public StoreReader<Site> getReader(Integer siteId) {
        return new RotatingSiteStore(fileStreamProvider, new SiteScope(rootMetadataPath, siteId));
    }

    public SiteStoreWriter getWriter(Integer siteId) {
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

    public SiteStoreWriter getGlobalWriter() {
        return globalWriter;
    }
}
