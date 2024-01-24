package com.uid2.admin.monitoring;

import com.uid2.shared.model.Service;
import com.uid2.shared.store.reader.IMetadataVersionedStore;
import com.uid2.shared.store.reader.RotatingServiceLinkStore;
import com.uid2.shared.store.reader.RotatingServiceStore;
import io.micrometer.core.instrument.Gauge;

import java.util.Optional;

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
    public static void addDataStoreSnowflakeMetrics(String dataType, RotatingServiceLinkStore serviceLinkStore, RotatingServiceStore serviceStore) {
        Gauge
                .builder("uid2_data_store_entry_count", () -> {
                    try {
                        // Warning: this downloads metadata from the underlying remote data store
                        Optional<Service> snowflakeService = serviceStore.getAllServices().stream().filter(s -> s.getName().equals("snowflake")).findFirst();
                        if (snowflakeService.isEmpty()) { throw new IllegalStateException("snowflake service does not exist, unable to find snowflake accounts"); }
                        long entryCount = serviceLinkStore.getAllServiceLinks().stream()
                                                                    .filter(s -> s.getServiceId() == snowflakeService.get().getServiceId())
                                                                    .count();
                        return entryCount;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .tag("store", dataType)
                .description("entry count from metadata of a data store")
                .register(globalRegistry);
    }
}
