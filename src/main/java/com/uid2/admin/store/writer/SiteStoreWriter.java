package com.uid2.admin.store.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.FileName;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.shared.model.Site;
import com.uid2.shared.store.reader.IMetadataVersionedStore;
import com.uid2.shared.store.scope.StoreScope;
import io.vertx.core.json.JsonObject;

import java.util.Collection;

public class SiteStoreWriter implements StoreWriter<Collection<Site>> {
    private final ScopedStoreWriter writer;
    private final ObjectWriter jsonWriter;
    private final EncryptedScopedStoreWriter encryptedWriter;

    // Constructor for regular writer
    public SiteStoreWriter(IMetadataVersionedStore reader, FileManager fileManager, ObjectWriter jsonWriter, VersionGenerator versionGenerator, Clock clock, StoreScope scope) {
        this.jsonWriter = jsonWriter;
        FileName dataFile = new FileName("sites", ".json");
        String dataType = "sites";
        writer = new ScopedStoreWriter(reader, fileManager, versionGenerator, clock, scope, dataFile, dataType);
        encryptedWriter = null;
    }

    // Constructor for encrypted writer
    public SiteStoreWriter(EncryptedScopedStoreWriter writer, ObjectWriter jsonWriter) {
        this.writer = null;
        this.encryptedWriter = writer;
        this.jsonWriter = jsonWriter;
    }

    @Override
    public void upload(Collection<Site> data, JsonObject extraMeta) throws Exception {
        String jsonData = jsonWriter.writeValueAsString(data);
        if (encryptedWriter != null) {
            encryptedWriter.upload(jsonData, extraMeta);
        } else {
            writer.upload(jsonData, extraMeta);
        }
    }

    @Override
    public void rewriteMeta() throws Exception {
        if (encryptedWriter != null) {
            encryptedWriter.rewriteMeta();
        } else {
            writer.rewriteMeta();
        }
    }
}
