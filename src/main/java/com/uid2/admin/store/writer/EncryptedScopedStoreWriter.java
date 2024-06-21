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
    private static final Logger LOGGER = LoggerFactory.getLogger(EncryptedScopedStoreWriter.class);

    public EncryptedScopedStoreWriter(StoreReader<Map<Integer, S3Key>> provider, FileManager fileManager,
                                      VersionGenerator versionGenerator, Clock clock, StoreScope scope,
                                      FileName dataFile, String dataType) {
        super(provider, fileManager, versionGenerator, clock, scope, dataFile, dataType);
    }

    public void uploadWithEncryptionKey(String data, JsonObject extraMeta, S3Key encryptionKey) throws Exception {
        // Encrypt data before uploading
        byte[] secret = Base64.getDecoder().decode(encryptionKey.getSecret());
        byte[] encryptedPayload = AesGcm.encrypt(data.getBytes(StandardCharsets.UTF_8), secret);
        JsonObject encryptedJson = new JsonObject()
                .put("key_id", encryptionKey.getId())
                .put("encryption_version", "1.0")
                .put("encrypted_payload", Base64.getEncoder().encodeToString(encryptedPayload));

        super.upload(encryptedJson.encodePrettily(), extraMeta);
    }
}
