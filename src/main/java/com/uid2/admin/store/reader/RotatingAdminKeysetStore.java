package com.uid2.admin.store.reader;

import com.uid2.admin.auth.AdminKeyset;
import com.uid2.admin.auth.AdminKeysetSnapshot;
import com.uid2.admin.store.parser.AdminKeysetParser;
import com.uid2.shared.cloud.DownloadCloudStorage;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.ScopedStoreReader;
import com.uid2.shared.store.reader.StoreReader;
import com.uid2.shared.store.scope.StoreScope;
import io.vertx.core.json.JsonObject;

import java.util.Map;

public class RotatingAdminKeysetStore implements StoreReader<Map<Integer, AdminKeyset>> {
    private final ScopedStoreReader<AdminKeysetSnapshot> reader;

    public RotatingAdminKeysetStore(DownloadCloudStorage fileStreamProvider, StoreScope scope) {
        this.reader = new ScopedStoreReader(fileStreamProvider, scope, new AdminKeysetParser(), "keysets");
    }

    @Override
    public Map<Integer, AdminKeyset> getAll() {
        return reader.getSnapshot().getAllKeysets();
    }

    @Override
    public void loadContent() throws Exception {
        this.loadContent(this.getMetadata());
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
        return reader.loadContent(metadata, "keysets");
    }

    @Override
    public CloudPath getMetadataPath() {
        return reader.getMetadataPath();
    }
}
