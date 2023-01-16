package com.uid2.admin.store.reader;

import com.uid2.shared.store.reader.IMetadataVersionedStore;
import io.vertx.core.json.JsonObject;

import java.util.Collection;

public interface StoreReader<T> extends IMetadataVersionedStore {
    Collection<T> getAll();
    void loadContent() throws Exception;
    JsonObject getMetadata() throws Exception;
}
