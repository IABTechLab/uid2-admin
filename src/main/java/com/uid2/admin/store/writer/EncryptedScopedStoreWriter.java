package com.uid2.admin.store.writer;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.FileName;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.shared.encryption.AesGcm;
import com.uid2.shared.model.S3Key;
import com.uid2.shared.store.reader.IMetadataVersionedStore;
import com.uid2.shared.store.reader.StoreReader;
import com.uid2.shared.store.scope.StoreScope;
import io.vertx.core.json.JsonObject;
import com.uid2.shared.store.reader.RotatingS3KeyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

public class EncryptedScopedStoreWriter extends ScopedStoreWriter {

    private final RotatingS3KeyProvider s3KeyProvider;
    private Integer siteId;

    public EncryptedScopedStoreWriter(IMetadataVersionedStore provider,
                                      FileManager fileManager, VersionGenerator versionGenerator, Clock clock,
                                      StoreScope scope, FileName dataFile, String dataType, RotatingS3KeyProvider s3KeyProvider, Integer siteId) {
        super(provider, fileManager, versionGenerator, clock, scope, dataFile, dataType);
        this.s3KeyProvider = s3KeyProvider;
        this.siteId = siteId;
    }

    String constructEncryptedFileName(FileName dataFile) {
        String originalFileName = dataFile.toString();
        int dotIndex = originalFileName.lastIndexOf(".");
        if (dotIndex == -1) {
            return originalFileName + "_encrypted";
        } else {
            return originalFileName.substring(0, dotIndex) + "_encrypted" + originalFileName.substring(dotIndex);
        }
    }


    public void uploadWithEncryptionKey(String data, JsonObject extraMeta, S3Key encryptionKey) throws Exception {
        byte[] secret = Base64.getDecoder().decode(encryptionKey.getSecret());
        byte[] encryptedPayload = AesGcm.encrypt(data.getBytes(StandardCharsets.UTF_8), secret);
        JsonObject encryptedJson = new JsonObject()
                .put("key_id", encryptionKey.getId())
                .put("encryption_version", "1.0")
                .put("encrypted_payload", Base64.getEncoder().encodeToString(encryptedPayload));

        this.upload(encryptedJson.encodePrettily(), extraMeta);
    }

    @Override
    public void upload(String data, JsonObject extraMeta) throws Exception {
        // Check if the data is already encrypted
        if (isEncrypted(data)) {
            // If it's encrypted, call the superclass upload method
            super.upload(data, extraMeta);
        } else {
            // If it's not encrypted, perform encryption and then upload
            if (siteId == null) {
                throw new IllegalStateException("Site ID is not set.");
            }

            Map<Integer, S3Key> s3Keys = s3KeyProvider.getAll();
            S3Key largestKey = null;

            for (S3Key key : s3Keys.values()) {
                if (key.getSiteId() == siteId) {
                    if (largestKey == null || key.getId() > largestKey.getId()) {
                        largestKey = key;
                    }
                }
            }

            if (largestKey != null) {
                uploadWithEncryptionKey(data, extraMeta, largestKey);
            } else {
                throw new IllegalStateException("No S3 keys available for encryption for site ID: " + siteId);
            }
        }
    }

    // Helper method to check if the data is already encrypted
    private boolean isEncrypted(String data) {
        try {
            JsonObject json = new JsonObject(data);
            return json.containsKey("key_id") &&
                    json.containsKey("encryption_version") &&
                    json.containsKey("encrypted_payload");
        } catch (Exception e) {
            return false;
        }
    }
}
