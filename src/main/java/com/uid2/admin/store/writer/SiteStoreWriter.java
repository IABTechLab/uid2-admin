package com.uid2.admin.store.writer;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.model.Site;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.FileName;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.shared.store.reader.IMetadataVersionedStore;
import com.uid2.shared.store.scope.StoreScope;
import io.vertx.core.json.JsonObject;

import java.util.Collection;

public class SiteStoreWriter implements StoreWriter<Collection<Site>> {
    private final ScopedStoreWriter writer;
    private final ObjectWriter jsonWriter;

    public SiteStoreWriter(IMetadataVersionedStore reader, FileManager fileManager, ObjectWriter jsonWriter, VersionGenerator versionGenerator, Clock clock, StoreScope scope) {
        this.jsonWriter = jsonWriter;
        FileName dataFile = new FileName("sites", ".json");
        FileName backupFile = new FileName("sites-old", ".json");
        String dataType = "sites";
        writer = new ScopedStoreWriter(reader, fileManager, versionGenerator, clock, scope, dataFile, backupFile, dataType);
    }

    @Override
    public void upload(Collection<Site> data, JsonObject extraMeta) throws Exception {
        writer.upload(jsonWriter.writeValueAsString(data), extraMeta);
    }
}
