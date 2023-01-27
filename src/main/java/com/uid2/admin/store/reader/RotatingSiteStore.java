package com.uid2.admin.store.reader;

import com.uid2.admin.model.Site;
import com.uid2.admin.store.parser.SiteParser;
import com.uid2.shared.cloud.DownloadCloudStorage;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.ScopedStoreReader;
import com.uid2.shared.store.reader.StoreReader;
import com.uid2.shared.store.scope.StoreScope;
import io.vertx.core.json.JsonObject;

import java.util.Collection;
import java.util.Map;

public class RotatingSiteStore implements ISiteStore, StoreReader<Collection<Site>> {
    public static final String SITES_METADATA_PATH = "sites_metadata_path";
    private final ScopedStoreReader<Map<Integer, Site>> reader;

    public RotatingSiteStore(DownloadCloudStorage fileStreamProvider, StoreScope scope) {
        this.reader = new ScopedStoreReader<>(fileStreamProvider, scope, new SiteParser(), "sites");
    }

    @Override
    public CloudPath getMetadataPath() { return reader.getMetadataPath(); }

    @Override
    public Collection<Site> getAllSites() {
        return getAll();
    }

    @Override
    public Collection<Site> getAll() {
        return reader.getSnapshot().values();
    }

    @Override
    public Site getSite(int siteId) {
        return reader.getSnapshot().get(siteId);
    }

    public JsonObject getMetadata() throws Exception {
        return reader.getMetadata();
    }

    @Override
    public long getVersion(JsonObject metadata) {
        return metadata.getLong("version");
    }

    @Override
    public long loadContent(JsonObject metadata) throws Exception {
        return reader.loadContent(metadata, "sites");
    }

    public void loadContent() throws Exception {
        this.loadContent(this.getMetadata());
    }
}
