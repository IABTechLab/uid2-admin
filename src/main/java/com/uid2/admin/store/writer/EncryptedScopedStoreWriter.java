package com.uid2.admin.store.writer;

import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.FileName;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.shared.encryption.AesGcm;
import com.uid2.shared.model.CloudEncryptionKey;
import com.uid2.shared.store.reader.IMetadataVersionedStore;
import com.uid2.shared.store.scope.StoreScope;
import io.vertx.core.json.JsonObject;
import com.uid2.shared.store.reader.RotatingCloudEncryptionKeyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class EncryptedScopedStoreWriter extends ScopedStoreWriter {

    private final RotatingCloudEncryptionKeyProvider cloudEncryptionKeyProvider;
    private Integer siteId;
    private static final Logger LOGGER = LoggerFactory.getLogger(EncryptedScopedStoreWriter.class);
    private String storeName;

    public EncryptedScopedStoreWriter(IMetadataVersionedStore provider,
                                      FileManager fileManager, VersionGenerator versionGenerator, Clock clock,
                                      StoreScope scope, FileName dataFile, String dataType, RotatingCloudEncryptionKeyProvider cloudEncryptionKeyProvider, Integer siteId) {
        super(provider, fileManager, versionGenerator, clock, scope, dataFile, dataType);
        this.cloudEncryptionKeyProvider = cloudEncryptionKeyProvider;
        //site id is passed in to look up S3 key to encrypt
        this.siteId = siteId;
        this.storeName = dataType; // for logging purposes
    }

    @Override
    public void upload(String data, JsonObject extraMeta) throws Exception {
        if (siteId == null) {
            throw new IllegalStateException("Site ID is not set.");
        }

        CloudEncryptionKey encryptionKey = null;
        try {
             encryptionKey = cloudEncryptionKeyProvider.getEncryptionKeyForSite(siteId);
        } catch (IllegalStateException e) {
            LOGGER.error("Error: No Cloud Encryption keys available for encryption for site ID: {}", siteId, e);
        }

        if (encryptionKey != null) {
            uploadWithEncryptionKey(data, extraMeta, encryptionKey);
            LOGGER.info("File encryption completed for site_id={} key_id={} store={}", siteId, encryptionKey.getId(), storeName);
        } else {
            throw new IllegalStateException("No Cloud Encryption keys available for encryption for site ID: " + siteId);
        }
    }

    private void uploadWithEncryptionKey(String data, JsonObject extraMeta, CloudEncryptionKey encryptionKey) throws Exception {
        byte[] secret = Base64.getDecoder().decode(encryptionKey.getSecret());
        byte[] encryptedPayload = AesGcm.encrypt(data.getBytes(StandardCharsets.UTF_8), secret);
        JsonObject encryptedJson = new JsonObject()
                .put("key_id", encryptionKey.getId())
                .put("encryption_version", "1.0")
                .put("encrypted_payload", Base64.getEncoder().encodeToString(encryptedPayload));

        super.upload(encryptedJson.encodePrettily(), extraMeta);
    }
}
