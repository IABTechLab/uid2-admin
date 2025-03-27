package com.uid2.admin.store.writer;

import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.version.VersionGenerator;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;

public class EncryptedSaltStoreWriter extends SaltStoreWriter implements StoreWriter {
    private StoreScope scope;
    private RotatingCloudEncryptionKeyProvider cloudEncryptionKeyProvider;
    private Integer siteId;

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

    @Override
    protected void uploadSaltsSnapshot(RotatingSaltProvider.SaltSnapshot snapshot, String location) throws Exception {
        if (siteId == null) {
            throw new IllegalStateException("Site ID is not set.");
        }

        if (!cloudStorage.list(location).isEmpty()) {
            // update the tags on the file to ensure it is still marked as current
            this.setStatusTagToCurrent(location);
            return;
        }

        StringBuilder stringBuilder = new StringBuilder();

        for (SaltEntry entry: snapshot.getAllRotatingSalts()) {
            stringBuilder.append(entry.getId()).append(",").append(entry.getLastUpdated()).append(",").append(entry.getSalt()).append("\n");
        }

        String data = stringBuilder.toString();

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

        final Path newSaltsFile = Files.createTempFile("salts", ".txt");
        try (BufferedWriter w = Files.newBufferedWriter(newSaltsFile)) {
            w.write(encryptedJson.encodePrettily());
        }

        this.upload(newSaltsFile.toString(), location);
    }

    @Override
    public void upload(Object data, JsonObject extraMeta) throws Exception {
        @SuppressWarnings("unchecked")
        List<RotatingSaltProvider.SaltSnapshot> snapshots = new ArrayList<>((Collection<RotatingSaltProvider.SaltSnapshot>) data);
        this.buildAndUploadMetadata(extraMeta, this.uploadASnapshotsAndGetMetadata(snapshots));
    }

    @Override
    public void rewriteMeta() throws Exception {

    }
}
