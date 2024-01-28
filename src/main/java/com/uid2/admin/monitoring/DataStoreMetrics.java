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

    public static void addDataStoreServiceLinkEntryCount(String serviceName, RotatingServiceLinkStore serviceLinkStore, RotatingServiceStore serviceStore) {
        try {
            // Warning: this downloads metadata from the underlying remote data store
            serviceStore.loadContent(serviceStore.getMetadata());
            serviceLinkStore.loadContent(serviceLinkStore.getMetadata());
            Optional<Service> service = serviceStore.getAllServices().stream().filter(s -> s.getName().equals(serviceName)).findFirst();

            service.ifPresent(targetService -> Gauge
                    .builder("uid2_data_store_entry_count", () -> {
                        long entryCount = serviceLinkStore.getAllServiceLinks().stream()
                                .filter(s -> s.getServiceId() == targetService.getServiceId())
                                .count();
                        return entryCount;
                    })
                    .tag("store", serviceName.toLowerCase())
                    .description("entry count of a data store")
                    .register(globalRegistry));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
