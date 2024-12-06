package com.uid2.admin.store.writer;

import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.shared.cloud.TaggableCloudStorage;
import com.uid2.shared.encryption.AesGcm;
import com.uid2.shared.model.CloudEncryptionKey;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.RotatingSaltProvider;
import com.uid2.shared.store.reader.RotatingCloudEncryptionKeyProvider;
import com.uid2.shared.store.scope.StoreScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.core.json.JsonObject;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class EncyptedSaltStoreWriter extends SaltStoreWriter implements StoreWriter {
    private StoreScope scope;
    private RotatingCloudEncryptionKeyProvider cloudEncryptionKeyProvider;
    private Integer siteId;

    private static final Logger LOGGER = LoggerFactory.getLogger(EncyptedSaltStoreWriter.class);
    public EncyptedSaltStoreWriter(JsonObject config, RotatingSaltProvider provider, FileManager fileManager,
                                   TaggableCloudStorage cloudStorage, VersionGenerator versionGenerator, StoreScope scope,
                                   RotatingCloudEncryptionKeyProvider cloudEncryptionKeyProvider, Integer siteId) {
        super(config, provider, fileManager, cloudStorage, versionGenerator);
        this.scope = scope;
        this.cloudEncryptionKeyProvider = cloudEncryptionKeyProvider;
        this.siteId = siteId;
    }

    @Override
    protected java.lang.String getSaltSnapshotLocation(RotatingSaltProvider.SaltSnapshot snapshot) {
        return scope.resolve(new CloudPath(saltSnapshotLocationPrefix + snapshot.getEffective().toEpochMilli())).toString();
    }

    @Override
    protected void upload(String data, String location) throws Exception {
        if (siteId == null) {
            throw new IllegalStateException("Site ID is not set.");
        }

        CloudEncryptionKey encryptionKey = null;
        try {
            encryptionKey = cloudEncryptionKeyProvider.getEncryptionKeyForSite(siteId);
        } catch (IllegalStateException e) {
            LOGGER.error("Error: No Cloud Encryption keys available for encryption for site ID: {}", siteId, e);
        }
        JsonObject encryptedJson = new JsonObject();
        if (encryptionKey != null) {
            byte[] secret = Base64.getDecoder().decode(encryptionKey.getSecret());
            byte[] encryptedPayload = AesGcm.encrypt(data.getBytes(StandardCharsets.UTF_8), secret);
            encryptedJson.put("key_id", encryptionKey.getId())
                         .put("encryption_version", "1.0")
                         .put("encrypted_payload", Base64.getEncoder().encodeToString(encryptedPayload));
        } else {
            throw new IllegalStateException("No Cloud Encryption keys available for encryption for site ID: " + siteId);
        }


        super.upload(encryptedJson.encodePrettily(), location);
    }

    @Override
    public void upload(Object data, JsonObject extraMeta) throws Exception {
        super.upload((RotatingSaltProvider.SaltSnapshot) data);
    }

    @Override
    public void rewriteMeta() throws Exception {

    }
}
