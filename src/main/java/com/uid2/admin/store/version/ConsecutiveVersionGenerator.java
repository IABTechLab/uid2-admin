package com.uid2.admin.store.version;

import com.uid2.shared.store.reader.IMetadataVersionedStore;
import io.vertx.core.json.JsonObject;

public class ConsecutiveVersionGenerator implements VersionGenerator {
    private final IMetadataVersionedStore store;

    public ConsecutiveVersionGenerator(IMetadataVersionedStore store) {
        this.store = store;
    }

    @Override
    public Long getVersion() throws Exception {
        JsonObject metadata = store.getMetadata();
        return metadata.getLong("version") + 1;
    }
}
