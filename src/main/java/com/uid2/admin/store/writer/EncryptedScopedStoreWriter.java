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

    private static RotatingS3KeyProvider s3KeyProvider;
    private Integer siteId;

    public static void initializeS3KeyProvider(RotatingS3KeyProvider s3KeyProvider) {
        s3KeyProvider = s3KeyProvider;
    }
    public void setSiteId(Integer siteId) {
        this.siteId = siteId;
    }

    public EncryptedScopedStoreWriter(IMetadataVersionedStore provider,
                                      FileManager fileManager, VersionGenerator versionGenerator, Clock clock,
                                      StoreScope scope, FileName dataFile, String dataType) {
        super(provider, fileManager, versionGenerator, clock, scope, dataFile, dataType);
    }

    private String constructEncryptedFileName(FileName dataFile) {
        String originalFileName = dataFile.toString();
        int dotIndex = originalFileName.lastIndexOf(".");
        if (dotIndex == -1) {
            return originalFileName + "_encrypted";
        } else {
            return originalFileName.substring(0, dotIndex) + "_encrypted" + originalFileName.substring(dotIndex);
        }
    }

    //upload a file with a single S3key
    public void uploadWithEncryptionKey(String data, JsonObject extraMeta, S3Key encryptionKey) throws Exception {
        byte[] secret = Base64.getDecoder().decode(encryptionKey.getSecret());
        byte[] encryptedPayload = AesGcm.encrypt(data.getBytes(StandardCharsets.UTF_8), secret);
        JsonObject encryptedJson = new JsonObject()
                .put("key_id", encryptionKey.getId())
                .put("encryption_version", "1.0")
                .put("encrypted_payload", Base64.getEncoder().encodeToString(encryptedPayload));


        String encryptedFileName = constructEncryptedFileName(super.dataFile);
        super.upload(encryptedJson.encodePrettily(), extraMeta);
    }

    @Override
    public void upload(String data, JsonObject extraMeta) throws Exception {
        // Upload plaintext
        super.upload(data, extraMeta);

        // Find the key with the largest identifier for the current site
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

        // Upload encrypted version using the largest key
        if (largestKey != null) {
            uploadWithEncryptionKey(data, extraMeta, largestKey);
        } else {
            throw new IllegalStateException("No S3 keys available for encryption for site ID: " + siteId);
        }
    }
}
