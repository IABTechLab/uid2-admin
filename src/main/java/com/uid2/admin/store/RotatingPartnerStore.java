package com.uid2.admin.store;

import com.uid2.shared.Utils;
import com.uid2.shared.attest.UidCoreClient;
import com.uid2.shared.cloud.ICloudStorage;
import com.uid2.shared.store.IMetadataVersionedStore;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.InputStream;

public class RotatingPartnerStore implements IMetadataVersionedStore {
    public static final String PARTNERS_METADATA_PATH = "partners_metadata_path";
    private static final Logger LOGGER = LoggerFactory.getLogger(RotatingPartnerStore.class);

    private final ICloudStorage metadataStreamProvider;
    private final ICloudStorage contentStreamProvider;
    private final String metadataPath;
    private String configJson;

    public RotatingPartnerStore(ICloudStorage fileStreamProvider, String metadataPath) {
        this.metadataStreamProvider = fileStreamProvider;
        if (fileStreamProvider instanceof UidCoreClient) {
            this.contentStreamProvider = ((UidCoreClient) fileStreamProvider).getContentStorage();
        } else {
            this.contentStreamProvider = fileStreamProvider;
        }
        this.metadataPath = metadataPath;
    }

    public String getMetadataPath() { return this.metadataPath; }

    public String getConfig() {
        return configJson;
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
        final JsonObject sitesMetadata = metadata.getJsonObject("partners");
        final String path = sitesMetadata.getString("location");
        final InputStream inputStream = this.contentStreamProvider.download(path);
        this.configJson = Utils.readToEndAsString(inputStream);
        LOGGER.info("Loaded partner config: " + this.configJson.length() + " chars");
        return this.configJson.length();
    }

    public void loadContent() throws Exception {
        this.loadContent(this.getMetadata());
    }
}
