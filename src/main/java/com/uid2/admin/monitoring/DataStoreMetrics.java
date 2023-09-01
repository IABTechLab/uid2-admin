package com.uid2.admin.monitoring;

import com.uid2.shared.store.reader.IMetadataVersionedStore;
import io.micrometer.core.instrument.Gauge;

import static io.micrometer.core.instrument.Metrics.globalRegistry;

public final class DataStoreMetrics {

    public static void addDataStoreMetrics(String dataType, IMetadataVersionedStore dataStore) {
        Gauge
                .builder("uid2_data_store_version", () -> {
                    try {
                        // Warning: this downloads metadata from the underlying remote data store
                        return dataStore.getVersion(dataStore.getMetadata());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .tag("store", dataType)
                .description("version from metadata of a data store")
                .register(globalRegistry);
    }
}
