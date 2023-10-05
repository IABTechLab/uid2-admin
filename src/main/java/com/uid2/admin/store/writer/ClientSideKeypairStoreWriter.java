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
            JsonObject json = toJsonWithPrivateKey(keypair);
            jsonKeypairs.add(json);
        }
        String content = jsonKeypairs.encodePrettily();
        writer.upload(content, extraMeta);
    }

    @Override
    public void rewriteMeta() throws Exception {
        writer.rewriteMeta();
    }

    public static JsonObject toJsonWithoutPrivateKey(ClientSideKeypair keypair) {
        return toJson(keypair, false);
    }

    public static JsonObject toJsonWithPrivateKey(ClientSideKeypair keypair) {
        return toJson(keypair, true);
    }

    private static JsonObject toJson(ClientSideKeypair keypair, boolean includePrivateKey) {
        JsonObject jo = new JsonObject();
        jo.put("subscription_id", keypair.getSubscriptionId());
        jo.put("public_key", keypair.encodePublicKeyToString());
        if(includePrivateKey) {
            jo.put("private_key", keypair.encodePrivateKeyToString());
        }
        jo.put("site_id", keypair.getSiteId());
        jo.put("contact", keypair.getContact());
        jo.put("created", keypair.getCreated().getEpochSecond());
        jo.put("disabled", keypair.isDisabled());
        jo.put("name", keypair.getName());
        return jo;
    }

}
