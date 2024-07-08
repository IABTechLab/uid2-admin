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
        //site id is passed in to look up S3 key to encrypt
        this.siteId = siteId;
    }

    public void upload(String data, JsonObject extraMeta) throws Exception {
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

    private void uploadWithEncryptionKey(String data, JsonObject extraMeta, S3Key encryptionKey) throws Exception {
        byte[] secret = Base64.getDecoder().decode(encryptionKey.getSecret());
        byte[] encryptedPayload = AesGcm.encrypt(data.getBytes(StandardCharsets.UTF_8), secret);
        JsonObject encryptedJson = new JsonObject()
                .put("key_id", encryptionKey.getId())
                .put("encryption_version", "1.0")
                .put("encrypted_payload", Base64.getEncoder().encodeToString(encryptedPayload));

        super.upload(encryptedJson.encodePrettily(), extraMeta);
    }

    public String getDecryptedContent(String encryptedContent) throws Exception {
        JsonObject json = new JsonObject(encryptedContent);
        int keyId = json.getInteger("key_id");
        String encryptedPayload = json.getString("encrypted_payload");

        Map<Integer, S3Key> s3Keys = s3KeyProvider.getAll();
        S3Key decryptionKey = null;

        for (S3Key key : s3Keys.values()) {
            if (key.getSiteId() == siteId && key.getId() == keyId) {
                decryptionKey = key;
                break;
            }
        }

        if (decryptionKey == null) {
            throw new IllegalStateException("No matching S3 key found for decryption for site ID: " + siteId + " and key ID: " + keyId);
        }

        byte[] secret = Base64.getDecoder().decode(decryptionKey.getSecret());
        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedPayload);
        byte[] decryptedBytes = AesGcm.decrypt(encryptedBytes, 0, secret);

        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }
}
