package com.uid2.admin.store.writer;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.model.Site;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.reader.RotatingSiteStore;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.FileName;
import com.uid2.shared.store.scope.StoreScope;

import java.util.Collection;

public class SiteStoreWriter {
    private final ScopedStoreWriter writer;
    private final ObjectWriter jsonWriter;

    public SiteStoreWriter(RotatingSiteStore provider, FileManager fileManager, ObjectWriter jsonWriter, VersionGenerator versionGenerator, Clock clock, StoreScope scope) {
        this.jsonWriter = jsonWriter;
        FileName dataFile = new FileName("sites", ".json");
        FileName backupFile = new FileName("sites-old", ".json");
        String dataType = "sites";
        writer = new ScopedStoreWriter(provider, fileManager, versionGenerator, clock, scope, dataFile, backupFile, dataType);
    }

    public void upload(Collection<Site> data) throws Exception {
        writer.upload(jsonWriter.writeValueAsString(data));
    }
}
