package com.uid2.admin.store.writer;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.FileName;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.shared.auth.EncryptionKeyAcl;
import com.uid2.shared.store.reader.RotatingS3KeyProvider;
import com.uid2.shared.store.reader.StoreReader;
import com.uid2.shared.store.scope.EncryptedScope;
import com.uid2.shared.store.scope.StoreScope;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.Map;

public class KeyAclStoreWriter implements StoreWriter<Map<Integer, EncryptionKeyAcl>> {
    private final ScopedStoreWriter writer;

    public KeyAclStoreWriter(StoreReader<Map<Integer, EncryptionKeyAcl>> provider, FileManager fileManager,
                             ObjectWriter jsonWriter, VersionGenerator versionGenerator, Clock clock, StoreScope scope) {
        FileName dataFile = new FileName("keys_acl", ".json");
        String dataType = "keys_acl";
        writer = new ScopedStoreWriter(provider, fileManager, versionGenerator, clock, scope, dataFile, dataType);
    }

    public KeyAclStoreWriter(StoreReader<Map<Integer, EncryptionKeyAcl>> provider,
                             FileManager fileManager,
                             ObjectWriter jsonWriter,
                             VersionGenerator versionGenerator,
                             Clock clock,
                             EncryptedScope scope,
                             RotatingS3KeyProvider s3KeyProvider) {
        FileName dataFile = new FileName("keys_acl", ".json");
        String dataType = "keys_acl";
        this.writer = new EncryptedScopedStoreWriter(provider, fileManager, versionGenerator, clock, scope, dataFile, dataType, s3KeyProvider, scope.getId());
    }

    @Override
    public void upload(Map<Integer, EncryptionKeyAcl> data, JsonObject extraMeta) throws Exception {
        // generate new acls
        JsonArray jsonAcls = new JsonArray();
        for (Map.Entry<Integer, EncryptionKeyAcl> acl : data.entrySet()) {
            JsonObject jsonAcl = new JsonObject();
            jsonAcl.put("site_id", acl.getKey());
            jsonAcl.put((acl.getValue().getIsWhitelist() ? "whitelist" : "blacklist"),
                    new JsonArray(new ArrayList<>(acl.getValue().getAccessList())));
            jsonAcls.add(jsonAcl);
        }
        writer.upload(jsonAcls.encodePrettily(), extraMeta);
    }

    @Override
    public void rewriteMeta() throws Exception {
        writer.rewriteMeta();
    }
}
