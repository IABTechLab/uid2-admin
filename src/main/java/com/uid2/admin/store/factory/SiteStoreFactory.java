package com.uid2.admin.store.factory;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.FileName;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.EncryptedScopedStoreWriter;
import com.uid2.admin.store.writer.KeysetStoreWriter;
import com.uid2.admin.store.writer.SiteStoreWriter;
import com.uid2.admin.store.writer.StoreWriter;
import com.uid2.shared.auth.Keyset;
import com.uid2.shared.cloud.ICloudStorage;
import com.uid2.shared.model.Site;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.reader.RotatingS3KeyProvider;
import com.uid2.shared.store.reader.RotatingSiteStore;
import com.uid2.shared.store.reader.StoreReader;
import com.uid2.shared.store.scope.EncryptedScope;
import com.uid2.shared.store.scope.GlobalScope;
import com.uid2.shared.store.scope.SiteScope;
import com.uid2.shared.store.scope.StoreScope;

import java.util.Collection;
import java.util.Map;

public class SiteStoreFactory implements EncryptedStoreFactory<Collection<Site>> {
    private final ICloudStorage fileStreamProvider;
    private final CloudPath rootMetadataPath;
    private final ObjectWriter objectWriter;
    private final VersionGenerator versionGenerator;
    private final Clock clock;
    private final FileManager fileManager;
    private final RotatingSiteStore globalReader;
    private final SiteStoreWriter globalWriter;
    private final RotatingS3KeyProvider s3KeyProvider;


    public SiteStoreFactory(
            ICloudStorage fileStreamProvider,
            CloudPath rootMetadataPath,
            ObjectWriter objectWriter,
            VersionGenerator versionGenerator,
            Clock clock,
            FileManager fileManager) {
        this(fileStreamProvider, rootMetadataPath, objectWriter, versionGenerator, clock, null, fileManager);
    }

    public SiteStoreFactory(
            ICloudStorage fileStreamProvider,
            CloudPath rootMetadataPath,
            ObjectWriter objectWriter,
            VersionGenerator versionGenerator,
            Clock clock,
            RotatingS3KeyProvider s3KeyProvider,
            FileManager fileManager) {
        this.fileStreamProvider = fileStreamProvider;
        this.rootMetadataPath = rootMetadataPath;
        this.objectWriter = objectWriter;
        this.versionGenerator = versionGenerator;
        this.clock = clock;
        this.fileManager = fileManager;
        this.s3KeyProvider = s3KeyProvider;

        GlobalScope globalScope = new GlobalScope(rootMetadataPath);
        this.globalReader = new RotatingSiteStore(fileStreamProvider, globalScope);
        this.globalWriter = new SiteStoreWriter(
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

    public StoreReader<Collection<Site>> getEncryptedReader(Integer siteId) {
        return new RotatingSiteStore(fileStreamProvider, new EncryptedScope(rootMetadataPath, siteId));
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

    public StoreWriter<Collection<Site>> getEncryptedWriter(Integer siteId) {
        StoreScope encryptedScope = new EncryptedScope(rootMetadataPath, siteId);
        return new SiteStoreWriter(
                getEncryptedReader(siteId),
                fileManager,
                objectWriter,
                versionGenerator,
                clock,
                encryptedScope,
                s3KeyProvider
        );
    }


    public RotatingS3KeyProvider getS3Provider() {
        return this.s3KeyProvider;
    }


    public RotatingSiteStore getGlobalReader() {
        return globalReader;
    }

    public StoreWriter<Collection<Site>> getGlobalWriter() {
        return globalWriter;
    }
}
