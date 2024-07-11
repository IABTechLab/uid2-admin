package com.uid2.admin.legacy;

import com.uid2.shared.auth.AuthorizableStore;
import com.uid2.shared.auth.IAuthorizable;
import com.uid2.shared.cloud.DownloadCloudStorage;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.EncryptedScopedStoreReader;
import com.uid2.shared.store.ScopedStoreReader;
import com.uid2.shared.store.reader.RotatingS3KeyProvider;
import com.uid2.shared.store.reader.StoreReader;
import com.uid2.shared.store.scope.EncryptedScope;
import com.uid2.shared.store.scope.StoreScope;
import io.vertx.core.json.JsonObject;

import java.util.Collection;
import java.util.Comparator;

/*
  1. metadata.json format

    {
      "version" : <long>,
      "generated" : <unix_epoch_seconds>,
      "client_keys" : {
        "location": "s3_path"
      }
    }

  2. client keys file format
      [
        {
           key = "aksjdkajsdkajsdja",
           name = "ClientName",
           contact = "ClientEmail",
           created = "timestamp",
           site_id = N,
           roles = [],
        },
        ...
      ]
*/
public class RotatingLegacyClientKeyProvider implements ILegacyClientKeyProvider, StoreReader<Collection<LegacyClientKey>> {
    private final ScopedStoreReader<Collection<LegacyClientKey>> reader;
    private final AuthorizableStore<LegacyClientKey> authorizableStore;

    public RotatingLegacyClientKeyProvider(DownloadCloudStorage fileStreamProvider, StoreScope scope) {
        this.reader = new ScopedStoreReader<>(fileStreamProvider, scope, new LegacyClientParser(), "auth keys");
    }

    public RotatingLegacyClientKeyProvider(DownloadCloudStorage fileStreamProvider, EncryptedScope scope, RotatingS3KeyProvider s3KeyProvider) {
        this.reader = new EncryptedScopedStoreReader<>(fileStreamProvider, scope, new LegacyClientParser(), "auth keys", scope.getId(), s3KeyProvider);
    }

    @Override
    public JsonObject getMetadata() throws Exception {
        return reader.getMetadata();
    }

    @Override
    public long getVersion(JsonObject metadata) {
        return metadata.getLong("version");
    }

    @Override
    public long loadContent(JsonObject metadata) throws Exception {
        long version = reader.loadContent(metadata, "client_keys");
        authorizableStore.refresh(getAll());
        return version;
    }

    @Override
    public LegacyClientKey getClientKey(String key) {
        return authorizableStore.getAuthorizableByKey(key);
    }

    @Override
    public LegacyClientKey getClientKeyFromHash(String hash) {
        return authorizableStore.getAuthorizableByHash(hash);
    }

    @Override
    public Collection<LegacyClientKey> getAll() {
        return reader.getSnapshot();
    }

    @Override
    public void loadContent() throws Exception {
        loadContent(getMetadata());
    }

    @Override
    public CloudPath getMetadataPath() {
        return reader.getMetadataPath();
    }

    @Override
    public IAuthorizable get(String key) {
        return getClientKey(key);
    }

    @Override
    public LegacyClientKey getOldestClientKey(int siteId) {
        return this.reader.getSnapshot().stream()
                .filter(k -> k.getSiteId() == siteId) // filter by site id
                .sorted(Comparator.comparing(LegacyClientKey::getCreated)) // sort by key creation timestamp ascending
                .findFirst() // return the oldest key
                .orElse(null);
    }
}
