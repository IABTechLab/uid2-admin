package com.uid2.admin.store.writer;

import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.shared.Utils;
import com.uid2.shared.cloud.TaggableCloudStorage;
import com.uid2.shared.encryption.AesGcm;
import com.uid2.shared.model.CloudEncryptionKey;
import com.uid2.shared.model.SaltEntry;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.RotatingSaltProvider;
import com.uid2.shared.store.reader.RotatingCloudEncryptionKeyProvider;
import com.uid2.shared.store.scope.StoreScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.core.json.JsonObject;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class EncryptedSaltStoreWriter extends SaltStoreWriter implements StoreWriter {
    private StoreScope scope;
    private RotatingCloudEncryptionKeyProvider cloudEncryptionKeyProvider;
    private Integer siteId;
    private JsonObject unEncryptedMetadataData;

    private static final Logger LOGGER = LoggerFactory.getLogger(EncryptedSaltStoreWriter.class);
    public EncryptedSaltStoreWriter(JsonObject config, RotatingSaltProvider provider, FileManager fileManager,
                                    TaggableCloudStorage cloudStorage, VersionGenerator versionGenerator, StoreScope scope,
                                    RotatingCloudEncryptionKeyProvider cloudEncryptionKeyProvider, Integer siteId) {
        super(config, provider, fileManager, cloudStorage, versionGenerator);
        this.scope = scope;
        this.cloudEncryptionKeyProvider = cloudEncryptionKeyProvider;
        this.siteId = siteId;
    }

    @Override
    protected java.lang.String getSaltSnapshotLocation(RotatingSaltProvider.SaltSnapshot snapshot) {
        return scope.resolve(new CloudPath("salts.txt." + snapshot.getEffective().toEpochMilli())).toString();
    }

    private CloudEncryptionKey getActiveCloudEncryptionKey(){
        try {
            return cloudEncryptionKeyProvider.getEncryptionKeyForSite(siteId);
        } catch (IllegalStateException e) {
            LOGGER.error("Error: No Cloud Encryption keys available for encryption for site ID: {}", siteId, e);
            throw e;
        }
    }

    @Override
    protected JsonObject enrichMetadata(JsonObject metadata){
        metadata.put("key_id", this.getActiveCloudEncryptionKey().getId());
        return metadata;
    }

    /**
     * Attempts to upload the salts snapshot to the specified location.
     * <p>
     * If the snapshot does not exist, it will be created at the given location.
     * If it exists but was encrypted with a different key, the snapshot is
     * re-encrypted and overwritten.
     * </p>
     * @param snapshot The snapshot containing the salts.
     * @param location The target storage location.
     * @return {@code true} if the snapshot was successfully written, {@code false} otherwise.
     */
    @Override
    protected boolean tryUploadSaltsSnapshot(RotatingSaltProvider.SaltSnapshot snapshot, String location) throws Exception {
        if (siteId == null) {
            throw new IllegalStateException("Site ID is not set.");
        }
        CloudEncryptionKey encryptionKey = this.getActiveCloudEncryptionKey();
        boolean fileExist = !cloudStorage.list(location).isEmpty();
         if (fileExist) {
             JsonObject metadata = Utils.toJsonObject(this.cloudStorage.download(this.getMetadataPath().toString()));
             if (Objects.equals(metadata.getInteger("key_id", null), encryptionKey.getId())) {
                 LOGGER.info("Not overwriting salt files for site {} as encryption key is already used before", this.siteId);
                 return false;
             }
         }
        StringBuilder stringBuilder = new StringBuilder();

        for (SaltEntry entry: snapshot.getAllRotatingSalts()) {
            stringBuilder.append(entry.getId()).append(",").append(entry.getLastUpdated()).append(",").append(entry.getSalt()).append("\n");
        }

        String data = stringBuilder.toString();
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

        final Path newSaltsFile = Files.createTempFile("salts", ".txt");
        try (BufferedWriter w = Files.newBufferedWriter(newSaltsFile)) {
            w.write(encryptedJson.encodePrettily());
        }
        this.upload(newSaltsFile.toString(), location);
        LOGGER.info("File encryption completed for site_id={} key_id={} store={}", siteId, encryptionKey.getId(), "salts");
        return true;
    }

    @Override
    protected JsonObject getMetadata(){
        return this.unEncryptedMetadataData;
    }

    @Override
    protected Long getMetadataVersion() throws Exception {
        return this.unEncryptedMetadataData.getLong("version");
    }

    @Override
    public void upload(Object data, JsonObject extraMeta) throws Exception {
        this.unEncryptedMetadataData = extraMeta;
        @SuppressWarnings("unchecked")
        List<RotatingSaltProvider.SaltSnapshot> snapshots = new ArrayList<>((Collection<RotatingSaltProvider.SaltSnapshot>) data);
        this.buildAndUploadMetadata(snapshots);
    }

    @Override
    public void rewriteMeta() throws Exception {

    }
}
