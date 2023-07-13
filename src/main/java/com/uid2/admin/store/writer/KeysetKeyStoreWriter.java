package com.uid2.admin.store.writer;

import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.FileName;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.shared.model.KeysetKey;
import com.uid2.shared.store.reader.RotatingKeysetKeyStore;
import com.uid2.shared.store.scope.StoreScope;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.Base64;
import java.util.Collection;

public class KeysetKeyStoreWriter implements StoreWriter<Collection<KeysetKey>> {
    private final ScopedStoreWriter writer;

    public KeysetKeyStoreWriter(RotatingKeysetKeyStore provider, FileManager fileManager, VersionGenerator versionGenerator, Clock clock, StoreScope scope) {
        FileName dataFile = new FileName("keyset_keys", ".json");
        String dataType = "keyset_keys";
        writer = new ScopedStoreWriter(provider, fileManager, versionGenerator, clock, scope, dataFile, dataType);
    }

    @Override
    public void upload(Collection<KeysetKey> data, JsonObject extraMeta) throws Exception {
        final JsonArray jsonKeys = new JsonArray();
        for (KeysetKey key : data) {
            JsonObject json = new JsonObject();
            json.put("id", key.getId());
            json.put("keyset_id", key.getKeysetId());
            json.put("created", key.getCreated().getEpochSecond());
            json.put("activates", key.getActivates().getEpochSecond());
            json.put("expires", key.getExpires().getEpochSecond());
            json.put("secret", Base64.getEncoder().encodeToString(key.getKeyBytes()));
            jsonKeys.add(json);
        }
        String content = jsonKeys.encodePrettily();
        writer.upload(content, extraMeta);
    }

    public void upload(Collection<KeysetKey> data, Integer newMaxKeyId) throws Exception {
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
