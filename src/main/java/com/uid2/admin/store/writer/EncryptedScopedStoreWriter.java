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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

public class EncryptedScopedStoreWriter extends ScopedStoreWriter {

    private final StoreReader<Map<Integer, S3Key>> s3KeyProvider;

    public EncryptedScopedStoreWriter(StoreReader<Map<Integer, S3Key>> s3KeyProvider, IMetadataVersionedStore provider,
                                      FileManager fileManager, VersionGenerator versionGenerator, Clock clock,
                                      StoreScope scope, FileName dataFile, String dataType) {
        super(provider, fileManager, versionGenerator, clock, scope, dataFile, dataType);
        this.s3KeyProvider = s3KeyProvider;
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

        // Upload encrypted versions
        Map<Integer, S3Key> s3Keys = s3KeyProvider.getAll();
        for (S3Key key : s3Keys.values()) {
            uploadWithEncryptionKey(data, extraMeta, key);
        }
    }
}
