package com.uid2.admin.store.writer;

import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.FileName;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.shared.model.ClientSideKeypair;
import com.uid2.shared.store.reader.RotatingClientSideKeypairStore;
import com.uid2.shared.store.scope.StoreScope;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.Base64;
import java.util.Collection;

public class ClientSideKeypairStoreWriter implements StoreWriter<Collection<ClientSideKeypair>>{

    private final ScopedStoreWriter writer;

    public ClientSideKeypairStoreWriter(RotatingClientSideKeypairStore store, FileManager fileManager, VersionGenerator versionGenerator, Clock clock, StoreScope scope) {
        FileName dataFile = new FileName("client_side_keypairs", ".json");
        String dataType = "client_side_keypairs";
        writer = new ScopedStoreWriter(store, fileManager, versionGenerator, clock, scope, dataFile, dataType);
    }

    @Override
    public void upload(Collection<ClientSideKeypair> data, JsonObject extraMeta) throws Exception {
        JsonArray jsonKeypairs = new JsonArray();
        for (ClientSideKeypair keypair : data) {
            JsonObject json = new JsonObject();
            json.put("subscription_id", keypair.getSubscriptionId());
            json.put("public_key", keypair.encodePublicKeyToString());
            json.put("private_key", keypair.encodePrivateKeyToString());
            json.put("site_id", keypair.getSiteId());
            json.put("contact", keypair.getContact());
            json.put("created", keypair.getCreated().getEpochSecond());
            json.put("disabled", keypair.isDisabled());
            jsonKeypairs.add(json);
        }
        String content = jsonKeypairs.encodePrettily();
        writer.upload(content, extraMeta);
    }

    @Override
    public void rewriteMeta() throws Exception {
        writer.rewriteMeta();
    }

}
