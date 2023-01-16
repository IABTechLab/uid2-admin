package com.uid2.admin.store.writer;

import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.FileName;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.shared.model.EncryptionKey;
import com.uid2.shared.store.reader.RotatingKeyStore;
import com.uid2.shared.store.scope.StoreScope;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.Collection;

public class EncryptionKeyStoreWriter implements StoreWriter<Collection<EncryptionKey>> {
    private final ScopedStoreWriter writer;

    public EncryptionKeyStoreWriter(RotatingKeyStore provider, FileManager fileManager, VersionGenerator versionGenerator, Clock clock, StoreScope scope) {
        FileName dataFile = new FileName("keys", ".json");
        FileName backupFile = new FileName("keys-old", ".json");
        String dataType = "keys";
        writer = new ScopedStoreWriter(provider, fileManager, versionGenerator, clock, scope, dataFile, backupFile, dataType);
    }

    public void upload(Collection<EncryptionKey> data, Integer newMaxKeyId) throws Exception {
        JsonObject extraMeta = new JsonObject();
        if (newMaxKeyId != null) {
            extraMeta.put("max_key_id", newMaxKeyId);
        }

        final JsonArray jsonKeys = new JsonArray();
        for (EncryptionKey key : data) {
            JsonObject json = new JsonObject();
            json.put("id", key.getId());
            json.put("site_id", key.getSiteId());
            json.put("created", key.getCreated().getEpochSecond());
            json.put("activates", key.getActivates().getEpochSecond());
            json.put("expires", key.getExpires().getEpochSecond());
            json.put("secret", key.getKeyBytes());
            jsonKeys.add(json);
        }
        String content = jsonKeys.encodePrettily();
        writer.upload(content, extraMeta);
    }

    @Override
    public void upload(Collection<EncryptionKey> data) throws Exception {
        upload(data, null);
    }
}
