// Copyright (c) 2021 The Trade Desk, Inc
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice,
//    this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice,
//    this list of conditions and the following disclaimer in the documentation
//    and/or other materials provided with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
// POSSIBILITY OF SUCH DAMAGE.

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
