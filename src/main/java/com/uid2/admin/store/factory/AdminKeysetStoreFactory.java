package com.uid2.admin.store.factory;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.auth.AdminKeyset;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.FileName;
import com.uid2.admin.store.reader.RotatingAdminKeysetStore;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.AdminKeysetWriter;
import com.uid2.admin.store.writer.EncryptedScopedStoreWriter;
import com.uid2.admin.store.writer.KeysetStoreWriter;
import com.uid2.admin.store.writer.StoreWriter;
import com.uid2.shared.auth.Keyset;
import com.uid2.shared.store.reader.StoreReader;
import com.uid2.shared.cloud.ICloudStorage;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.reader.RotatingKeysetProvider;
import com.uid2.shared.store.reader.StoreReader;
import com.uid2.shared.store.scope.GlobalScope;
import com.uid2.shared.store.scope.SiteScope;
import com.uid2.shared.store.scope.StoreScope;

import java.util.Map;

public class AdminKeysetStoreFactory implements StoreFactory<Map<Integer, AdminKeyset>> {
    private final ICloudStorage fileStreamProvider;
    private final CloudPath rootMetadataPath;
    private final ObjectWriter objectWriter;
    private final VersionGenerator versionGenerator;
    private final Clock clock;
    private final FileManager fileManager;
    private final RotatingAdminKeysetStore globalReader;

    public AdminKeysetStoreFactory(ICloudStorage fileStreamProvider,
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
        globalReader = new RotatingAdminKeysetStore(fileStreamProvider, globalScope);
    }

    @Override
    public StoreReader<Map<Integer, AdminKeyset>> getReader(Integer siteId) {
        return new RotatingAdminKeysetStore(fileStreamProvider, new SiteScope(rootMetadataPath, siteId));
    }

    @Override
    public StoreWriter<Map<Integer, AdminKeyset>> getWriter(Integer siteId) {
        return new AdminKeysetWriter(
                getReader(siteId),
                fileManager,
                objectWriter,
                versionGenerator,
                clock,
                new SiteScope(rootMetadataPath, siteId)
        );
    }

    public StoreWriter<Map<Integer, AdminKeyset>>  getEncryptedWriter(Integer siteId) {
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

        return new AdminKeysetWriter (encryptedWriter,objectWriter);
    }



    public RotatingAdminKeysetStore getGlobalReader() { return globalReader; }
}
