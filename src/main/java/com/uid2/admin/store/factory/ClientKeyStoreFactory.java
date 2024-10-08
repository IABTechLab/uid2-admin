package com.uid2.admin.store.factory;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.legacy.LegacyClientKey;
import com.uid2.admin.legacy.LegacyClientKeyStoreWriter;
import com.uid2.admin.legacy.RotatingLegacyClientKeyProvider;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.FileName;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.ClientKeyStoreWriter;
import com.uid2.admin.store.writer.EncryptedScopedStoreWriter;
import com.uid2.admin.store.writer.KeysetStoreWriter;
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

public class ClientKeyStoreFactory implements EncryptedStoreFactory<Collection<LegacyClientKey>> {
    private final ICloudStorage fileStreamProvider;
    private final CloudPath rootMetadataPath;
    private final ObjectWriter objectWriter;
    private final VersionGenerator versionGenerator;
    private final Clock clock;
    private final FileManager fileManager;
    private final RotatingLegacyClientKeyProvider globalReader;
    private final RotatingS3KeyProvider s3KeyProvider;
    private final LegacyClientKeyStoreWriter globalWriter;

    public ClientKeyStoreFactory(
            ICloudStorage fileStreamProvider,
            CloudPath rootMetadataPath,
            ObjectWriter objectWriter,
            VersionGenerator versionGenerator,
            Clock clock,
            FileManager fileManager) {
        this(fileStreamProvider, rootMetadataPath, objectWriter, versionGenerator, clock, null, fileManager);
    }

    public ClientKeyStoreFactory(
            ICloudStorage fileStreamProvider,
            CloudPath rootMetadataPath,
            ObjectWriter objectWriter,
            VersionGenerator versionGenerator,
            Clock clock,
            RotatingS3KeyProvider s3KeyProvider,
            FileManager fileManager)  {
        this.fileStreamProvider = fileStreamProvider;
        this.rootMetadataPath = rootMetadataPath;
        this.objectWriter = objectWriter;
        this.versionGenerator = versionGenerator;
        this.clock = clock;
        this.s3KeyProvider = s3KeyProvider;
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

    public RotatingLegacyClientKeyProvider getEncryptedReader(Integer siteId, boolean isPublic) {
        return new RotatingLegacyClientKeyProvider(fileStreamProvider, new EncryptedScope(rootMetadataPath, siteId, isPublic),s3KeyProvider);
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

    public LegacyClientKeyStoreWriter getEncryptedWriter(Integer siteId, boolean isPublic) {
        return new LegacyClientKeyStoreWriter(
                getEncryptedReader(siteId,isPublic),
                fileManager,
                objectWriter,
                versionGenerator,
                clock,
                new EncryptedScope(rootMetadataPath, siteId, isPublic),
                s3KeyProvider
        );
    }

    public RotatingS3KeyProvider getS3Provider() {
        return this.s3KeyProvider;
    }


    public RotatingLegacyClientKeyProvider getGlobalReader() {
        return globalReader;
    }

    public StoreWriter<Collection<LegacyClientKey>> getGlobalWriter() {
        return globalWriter;
    }
}
