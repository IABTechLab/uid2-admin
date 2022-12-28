package com.uid2.admin.store.writer;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.shared.auth.EncryptionKeyAcl;
import com.uid2.shared.store.reader.RotatingKeyAclProvider;
import com.uid2.admin.store.FileName;
import com.uid2.shared.store.scope.StoreScope;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.Map;

public class KeyAclStoreWriter {
    private final ScopedStoreWriter writer;

    public KeyAclStoreWriter(RotatingKeyAclProvider provider, FileManager fileManager, ObjectWriter jsonWriter, VersionGenerator versionGenerator, Clock clock, StoreScope scope) {
        FileName dataFile = new FileName("keys_acl", ".json");
        FileName backupFile = new FileName("keys_acl-old", ".json");
        String dataType = "keys_acl";
        writer = new ScopedStoreWriter(provider, fileManager, versionGenerator, clock, scope, dataFile, backupFile, dataType);
    }

    public void upload(Map<Integer, EncryptionKeyAcl> data) throws Exception {
        // generate new acls
        JsonArray jsonAcls = new JsonArray();
        for(Map.Entry<Integer, EncryptionKeyAcl> acl : data.entrySet()) {
            JsonObject jsonAcl = new JsonObject();
            jsonAcl.put("site_id", acl.getKey());
            jsonAcl.put((acl.getValue().getIsWhitelist() ? "whitelist" : "blacklist"),
                    new JsonArray(new ArrayList<>(acl.getValue().getAccessList())));
            jsonAcls.add(jsonAcl);
        }
        writer.upload(jsonAcls.encodePrettily());
    }
}
