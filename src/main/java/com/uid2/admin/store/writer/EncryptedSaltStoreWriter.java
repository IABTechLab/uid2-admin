package com.uid2.admin.store.writer;

import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.shared.cloud.CloudStorageException;
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
    private JsonObject unencryptedSaltProviderMetadata;

    private final List<RotatingSaltProvider.SaltSnapshot> previousSeenSnapshots = new ArrayList<>();

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
    protected void refreshProvider() {
       // we do not need to refresh the provider on encrypted writers
    }

    @Override
    protected List<RotatingSaltProvider.SaltSnapshot> getSnapshots(RotatingSaltProvider.SaltSnapshot data){
    /*
    Since metadata.json is overwritten during the process, we maintain a history of all snapshots seen so far.
    On the final write, we append this history to metadata.json to ensure no snapshots are lost.
    */
        this.previousSeenSnapshots.add(data);
        return this.previousSeenSnapshots;
    }

    @Override
    protected JsonObject getMetadata() throws Exception {
        /*
        *  We maintain a local store of metadata since the Scope is always `EncryptedScope`, which overrides the path to
        * use the encrypted site-specific path, which may not have all entries.
        * This logic allows `extraMeta` to be passed, containing all values of unencrypted metadata.
        * */
        return this.unencryptedSaltProviderMetadata;
    }

    @Override
    public void upload(Object data, JsonObject extraMeta) throws Exception {
        this.unencryptedSaltProviderMetadata = extraMeta;
        for(RotatingSaltProvider.SaltSnapshot saltSnapshot: (Collection<RotatingSaltProvider.SaltSnapshot>) data) {
            super.upload(saltSnapshot);
        }
    }

    @Override
    public void rewriteMeta() throws Exception {

    }
}
