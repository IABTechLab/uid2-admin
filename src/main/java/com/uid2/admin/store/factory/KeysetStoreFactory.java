package com.uid2.admin.store.factory;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.FileName;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.EncryptedScopedStoreWriter;
import com.uid2.admin.store.writer.KeysetStoreWriter;
import com.uid2.admin.store.writer.StoreWriter;
import com.uid2.shared.auth.Keyset;
import com.uid2.shared.cloud.ICloudStorage;
import com.uid2.shared.model.S3Key;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.reader.IMetadataVersionedStore;
import com.uid2.shared.store.reader.RotatingKeysetProvider;
import com.uid2.shared.store.reader.RotatingS3KeyProvider;
import com.uid2.shared.store.reader.StoreReader;
import com.uid2.shared.store.scope.GlobalScope;
import com.uid2.shared.store.scope.SiteScope;
import com.uid2.shared.store.scope.StoreScope;

import java.util.Map;

public class KeysetStoreFactory implements StoreFactory<Map<Integer, Keyset>> {
    private final ICloudStorage fileStreamProvider;
    private final CloudPath rootMetadataPath;
    private final ObjectWriter objectWriter;
    private final VersionGenerator versionGenerator;
    private final Clock clock;
    private final FileManager fileManager;
    private final RotatingKeysetProvider globalReader;
    private final KeysetStoreWriter globalWriter;
    private final boolean enableKeysets;


    public KeysetStoreFactory(ICloudStorage fileStreamProvider,
                              CloudPath rootMetadataPath,
                              ObjectWriter objectWriter,
                              VersionGenerator versionGenerator,
                              Clock clock,
                              FileManager fileManager,
                              boolean enableKeysets
) {
        this.fileStreamProvider = fileStreamProvider;
        this.rootMetadataPath = rootMetadataPath;
        this.objectWriter = objectWriter;
        this.versionGenerator = versionGenerator;
        this.clock = clock;
        this.fileManager = fileManager;
        GlobalScope globalScope = new GlobalScope(rootMetadataPath);
        globalReader = new RotatingKeysetProvider(fileStreamProvider, globalScope);
        globalWriter = new KeysetStoreWriter(
                globalReader,
                this.fileManager,
                objectWriter,
                versionGenerator,
                clock,
                globalScope,
                enableKeysets
        );
        this.enableKeysets = enableKeysets;
    }

    @Override
    public StoreReader<Map<Integer, Keyset>> getReader(Integer siteId) {
        return new RotatingKeysetProvider(fileStreamProvider, new SiteScope(rootMetadataPath, siteId));
    }

    @Override
    public StoreWriter<Map<Integer, Keyset>> getWriter(Integer siteId) {
        return new KeysetStoreWriter(
                getReader(siteId),
                fileManager,
                objectWriter,
                versionGenerator,
                clock,
                new SiteScope(rootMetadataPath, siteId),
                enableKeysets
        );
    }
    //encyptedWriter
    public StoreWriter<Map<Integer, Keyset>> getEncryptedWriter(Integer siteId) {
        CloudPath encryptedPath = new CloudPath(rootMetadataPath.toString() + "/encryption");
        StoreScope encryptedScope = new SiteScope(encryptedPath, siteId);

        EncryptedScopedStoreWriter encryptedWriter = new EncryptedScopedStoreWriter(
                getReader(siteId),
                fileManager,
                versionGenerator,
                clock,
                encryptedScope,
                new FileName("keysets", ".json"),
                "keysets"
        );

        return new KeysetStoreWriter(encryptedWriter,objectWriter);
    }



    public RotatingKeysetProvider getGlobalReader() { return globalReader; }

    public StoreWriter<Map<Integer, Keyset>> getGlobalWriter() { return globalWriter; }
}
