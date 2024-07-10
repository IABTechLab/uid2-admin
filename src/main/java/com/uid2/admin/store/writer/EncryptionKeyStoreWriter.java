package com.uid2.admin.store.writer;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.FileName;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.shared.model.EncryptionKey;
import com.uid2.shared.store.reader.RotatingKeyStore;
import com.uid2.shared.store.reader.RotatingS3KeyProvider;
import com.uid2.shared.store.scope.EncryptedScope;
import com.uid2.shared.store.scope.StoreScope;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.Base64;
import java.util.Collection;

public class EncryptionKeyStoreWriter implements StoreWriter<Collection<EncryptionKey>> {
    private final ScopedStoreWriter writer;

    public EncryptionKeyStoreWriter(RotatingKeyStore provider, FileManager fileManager, VersionGenerator versionGenerator, Clock clock, StoreScope scope) {
        FileName dataFile = new FileName("keys", ".json");
        String dataType = "keys";
        writer = new ScopedStoreWriter(provider, fileManager, versionGenerator, clock, scope, dataFile, dataType);
    }


    public EncryptionKeyStoreWriter(RotatingKeyStore provider,
                                    FileManager fileManager,
                                    VersionGenerator versionGenerator,
                                    Clock clock,
                                    EncryptedScope scope,
                                    RotatingS3KeyProvider s3KeyProvider) {
        FileName dataFile = new FileName("keys", ".json");
        String dataType = "keys";
        this.writer = new EncryptedScopedStoreWriter(provider, fileManager, versionGenerator, clock, scope, dataFile, dataType, s3KeyProvider, scope.getId());
    }

    @Override
    public void upload(Collection<EncryptionKey> data, JsonObject extraMeta) throws Exception {
        final JsonArray jsonKeys = new JsonArray();
        for (EncryptionKey key : data) {
            JsonObject json = new JsonObject();
            json.put("id", key.getId());
            json.put("site_id", key.getSiteId());
            json.put("created", key.getCreated().getEpochSecond());
            json.put("activates", key.getActivates().getEpochSecond());
            json.put("expires", key.getExpires().getEpochSecond());
            json.put("secret", Base64.getEncoder().encodeToString(key.getKeyBytes()));
            jsonKeys.add(json);
        }
        String content = jsonKeys.encodePrettily();
        writer.upload(content, extraMeta);
    }

    public void upload(Collection<EncryptionKey> data, Integer newMaxKeyId) throws Exception {
        upload(data, maxKeyMeta(newMaxKeyId));
    }

    @Override
    public void rewriteMeta() throws Exception {
        writer.rewriteMeta();
    }

    public static JsonObject maxKeyMeta(Integer newMaxKeyId) {
        return new JsonObject().put("max_key_id", newMaxKeyId);
    }
}
