package com.uid2.admin.store.writer;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.FileName;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.shared.model.ServiceLink;
import com.uid2.shared.store.reader.IMetadataVersionedStore;
import com.uid2.shared.store.scope.StoreScope;
import io.vertx.core.json.JsonObject;

import java.util.Collection;

public class ServiceLinkStoreWriter implements StoreWriter<Collection<ServiceLink>> {

    private final ScopedStoreWriter writer;
    private final ObjectWriter jsonWriter;

    public ServiceLinkStoreWriter(IMetadataVersionedStore reader, FileManager fileManager, ObjectWriter jsonWriter, VersionGenerator versionGenerator, Clock clock, StoreScope scope) {
        this.jsonWriter = jsonWriter;
        FileName dataFile = new FileName("service_links", ".json");
        String dataType = "service_links";
        writer = new ScopedStoreWriter(reader, fileManager, versionGenerator, clock, scope, dataFile, dataType);
    }

    @Override
    public void upload(Collection<ServiceLink> data, JsonObject extraMeta) throws Exception {
        writer.upload(jsonWriter.writeValueAsString(data), extraMeta);
    }

    @Override
    public void rewriteMeta() throws Exception {
        writer.rewriteMeta();
    }
}
