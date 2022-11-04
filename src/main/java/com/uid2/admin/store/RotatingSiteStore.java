package com.uid2.admin.store;

import com.uid2.admin.model.Site;
import com.uid2.shared.Utils;
import com.uid2.shared.attest.UidCoreClient;
import com.uid2.shared.cloud.ICloudStorage;
import com.uid2.shared.store.IMetadataVersionedStore;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class RotatingSiteStore implements ISiteStore, IMetadataVersionedStore {
    public static final String SITES_METADATA_PATH = "sites_metadata_path";
    private static final Logger LOGGER = LoggerFactory.getLogger(RotatingSiteStore.class);

    private final ICloudStorage metadataStreamProvider;
    private final ICloudStorage contentStreamProvider;
    private final String metadataPath;
    private AtomicReference<Map<Integer, Site>> latestSnapshot = new AtomicReference<>();

    public RotatingSiteStore(ICloudStorage fileStreamProvider, String metadataPath) {
        this.metadataStreamProvider = fileStreamProvider;
        if (fileStreamProvider instanceof UidCoreClient) {
            this.contentStreamProvider = ((UidCoreClient) fileStreamProvider).getContentStorage();
        } else {
            this.contentStreamProvider = fileStreamProvider;
        }
        this.metadataPath = metadataPath;
    }

    public String getMetadataPath() { return this.metadataPath; }

    @Override
    public Collection<Site> getAllSites() {
        return latestSnapshot.get().values();
    }

    @Override
    public Site getSite(int siteId) {
        return latestSnapshot.get().get(siteId);
    }

    @Override
    public JsonObject getMetadata() throws Exception {
        InputStream s = this.metadataStreamProvider.download(this.metadataPath);
        return Utils.toJsonObject(s);
    }

    @Override
    public long getVersion(JsonObject metadata) {
        return metadata.getLong("version");
    }

    @Override
    public long loadContent(JsonObject metadata) throws Exception {
        final JsonObject sitesMetadata = metadata.getJsonObject("sites");
        final String path = sitesMetadata.getString("location");
        final InputStream inputStream = this.contentStreamProvider.download(path);
        final JsonArray sitesSpec = Utils.toJsonArray(inputStream);
        final HashMap<Integer, Site> sites = new HashMap<>();
        for (int i = 0; i < sitesSpec.size(); ++i) {
            JsonObject siteSpec = sitesSpec.getJsonObject(i);
            final int siteId = siteSpec.getInteger("id");
            final String name = siteSpec.getString("name");
            final Boolean enabled = siteSpec.getBoolean("enabled", false);
            final Site site = new Site(siteId, name, enabled);
            sites.put(site.getId(), site);
        }
        this.latestSnapshot.set(sites);
        LOGGER.info("Loaded " + sites.size() + " sites");
        return sites.size();
    }

    public void loadContent() throws Exception {
        this.loadContent(this.getMetadata());
    }
}
