package com.uid2.admin.store.factory;

import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.FileName;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.EncryptedScopedStoreWriter;
import com.uid2.admin.store.writer.EncryptionKeyStoreWriter;
import com.uid2.admin.store.writer.KeysetStoreWriter;
import com.uid2.admin.store.writer.StoreWriter;
import com.uid2.shared.auth.Keyset;
import com.uid2.shared.cloud.ICloudStorage;
import com.uid2.shared.model.EncryptionKey;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.reader.RotatingKeyStore;
import com.uid2.shared.store.scope.GlobalScope;
import com.uid2.shared.store.scope.SiteScope;
import com.uid2.shared.store.scope.StoreScope;

import java.util.Collection;
import java.util.Map;

public class EncryptionKeyStoreFactory implements StoreFactory<Collection<EncryptionKey>> {
    private final ICloudStorage fileStreamProvider;
    private final CloudPath rootMetadataPath;
    private final VersionGenerator versionGenerator;
    private final Clock clock;
    private final FileManager fileManager;
    private final RotatingKeyStore globalReader;

    public EncryptionKeyStoreFactory(
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
        globalReader = new RotatingKeyStore(fileStreamProvider, globalScope);
    }

    @Override
    public RotatingKeyStore getReader(Integer siteId) {
        return new RotatingKeyStore(fileStreamProvider, new SiteScope(rootMetadataPath, siteId));
    }

    @Override
    public EncryptionKeyStoreWriter getWriter(Integer siteId) {
        return new EncryptionKeyStoreWriter(
                getReader(siteId),
                fileManager,
                versionGenerator,
                clock,
                new SiteScope(rootMetadataPath, siteId)
        );
    }

    public EncryptionKeyStoreWriter getEncryptedWriter(Integer siteId) {
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

        return new EncryptionKeyStoreWriter(encryptedWriter);
    }


    public RotatingKeyStore getGlobalReader() {
        return globalReader;
    }
}
