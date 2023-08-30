package com.uid2.admin.job.jobsync.keyset;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.AdminConst;
import com.uid2.admin.auth.AdminKeyset;
import com.uid2.admin.job.model.Job;
import com.uid2.admin.managers.KeysetManager;
import com.uid2.admin.store.*;
import com.uid2.admin.store.factory.AdminKeysetStoreFactory;
import com.uid2.admin.store.factory.KeysetStoreFactory;
import com.uid2.admin.store.factory.SiteStoreFactory;
import com.uid2.admin.store.version.EpochVersionGenerator;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.KeysetStoreWriter;
import com.uid2.admin.store.writer.StoreWriter;
import com.uid2.admin.vertx.JsonUtil;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.Const;
import com.uid2.shared.auth.Keyset;
import com.uid2.shared.cloud.CloudUtils;
import com.uid2.shared.cloud.ICloudStorage;
import com.uid2.shared.model.ClientType;
import com.uid2.shared.model.Site;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.reader.RotatingKeysetProvider;
import com.uid2.shared.store.reader.RotatingSiteStore;
import io.vertx.core.json.JsonObject;
import com.uid2.admin.store.reader.RotatingAdminKeysetStore;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ReplaceSharingTypesWithSitesJob extends Job {
    public final JsonObject config;
    private final WriteLock writeLock;
    private final boolean enableKeysets;

    private final RotatingAdminKeysetStore adminKeysetGlobalReader;
    private final RotatingKeysetProvider keysetGlobalReader;
    private final StoreWriter keysetGlobalWriter;
    private final RotatingSiteStore siteGlobalReader;

    public ReplaceSharingTypesWithSitesJob(JsonObject config, WriteLock writeLock) {

        this.config = config;
        this.writeLock = writeLock;
        this.enableKeysets = config.getBoolean(AdminConst.enableKeysetConfigProp);

        ICloudStorage cloudStorage = CloudUtils.createStorage(config.getString(Const.Config.CoreS3BucketProp), config);
        FileStorage fileStorage = new TmpFileStorage();
        ObjectWriter jsonWriter = JsonUtil.createJsonWriter();
        Clock clock = new InstantClock();
        VersionGenerator versionGenerator = new EpochVersionGenerator(clock);
        FileManager fileManager = new FileManager(cloudStorage, fileStorage);

        AdminKeysetStoreFactory adminKeysetStoreFactory = new AdminKeysetStoreFactory(
                cloudStorage,
                new CloudPath(config.getString("admin_keysets_metadata_path")),
                jsonWriter,
                versionGenerator,
                clock,
                fileManager);

        KeysetStoreFactory keysetStoreFactory = new KeysetStoreFactory(
                cloudStorage,
                new CloudPath(config.getString(Const.Config.KeysetsMetadataPathProp)),
                jsonWriter,
                versionGenerator,
                clock,
                fileManager,
                this.enableKeysets);

        SiteStoreFactory siteStoreFactory = new SiteStoreFactory(
                cloudStorage,
                new CloudPath(config.getString(RotatingSiteStore.SITES_METADATA_PATH)),
                jsonWriter,
                versionGenerator,
                clock,
                fileManager);

        this.adminKeysetGlobalReader = adminKeysetStoreFactory.getGlobalReader();
        this.keysetGlobalReader = keysetStoreFactory.getGlobalReader();
        this.keysetGlobalWriter = keysetStoreFactory.getGlobalWriter();
        this.siteGlobalReader = siteStoreFactory.getGlobalReader();
    }

    public ReplaceSharingTypesWithSitesJob(JsonObject config, WriteLock writeLock,
                                           RotatingAdminKeysetStore adminKeysetGlobalReader,
                                           RotatingKeysetProvider keysetGlobalReader,
                                           KeysetStoreWriter keysetGlobalWriter,
                                           RotatingSiteStore siteGlobalReader) {
        this.config = config;
        this.writeLock = writeLock;
        this.enableKeysets = config.getBoolean(AdminConst.enableKeysetConfigProp);
        this.adminKeysetGlobalReader = adminKeysetGlobalReader;
        this.keysetGlobalReader = keysetGlobalReader;
        this.keysetGlobalWriter = keysetGlobalWriter;
        this.siteGlobalReader = siteGlobalReader;
    }
    @Override
    public String getId() {
        return "admin_to_operator_keyset_job";
    }

    @Override
    public void execute() throws Exception {
        synchronized (writeLock) {
            this.adminKeysetGlobalReader.loadContent();
            this.keysetGlobalReader.loadContent();
            this.siteGlobalReader.loadContent();
        }

        Map<Integer, AdminKeyset> allAdminKeysets = this.adminKeysetGlobalReader.getAll();
        Map<ClientType, Set<Integer>> siteIdsByClientType = Map.of(
                ClientType.DSP, new HashSet<>(),
                ClientType.ADVERTISER, new HashSet<>(),
                ClientType.DATA_PROVIDER, new HashSet<>(),
                ClientType.PUBLISHER, new HashSet<>()
        );
        for(Site site: this.siteGlobalReader.getAllSites()) {
            int siteId = site.getId();
            for(ClientType type: site.getClientTypes()) {
                siteIdsByClientType.get(type).add(siteId);
            }
        }

        Map<Integer, Keyset> keysetMap = new HashMap<>();

        for (AdminKeyset adminKeyset : allAdminKeysets.values()) {
            Keyset keyset = KeysetManager.adminKeysetToKeyset(adminKeyset, siteIdsByClientType);
            keysetMap.put(keyset.getKeysetId(), keyset);
        }

        this.keysetGlobalWriter.upload(keysetMap, null);
    }
}
