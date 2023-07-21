package com.uid2.admin.job.jobsync.keyset;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.auth.AdminKeyset;
import com.uid2.admin.job.model.Job;
import com.uid2.admin.model.ClientType;
import com.uid2.admin.model.Site;
import com.uid2.admin.store.*;
import com.uid2.admin.store.factory.AdminKeysetStoreFactory;
import com.uid2.admin.store.factory.KeysetStoreFactory;
import com.uid2.admin.store.factory.SiteStoreFactory;
import com.uid2.admin.store.reader.RotatingSiteStore;
import com.uid2.admin.store.version.EpochVersionGenerator;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.vertx.JsonUtil;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.Const;
import com.uid2.shared.auth.Keyset;
import com.uid2.shared.cloud.CloudUtils;
import com.uid2.shared.cloud.ICloudStorage;
import com.uid2.shared.store.CloudPath;
import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import static com.uid2.admin.util.KeysetUtil.adminKeysetToKeyset;

public class AdminToOperatorKeysetJob extends Job {
    public final JsonObject config;
    private final WriteLock writeLock;

    public AdminToOperatorKeysetJob(JsonObject config, WriteLock writeLock) {
        this.config = config;
        this.writeLock = writeLock;
    }
    @Override
    public String getId() {
        return "admin_to_operator_keyset_job";
    }

    @Override
    public void execute() throws Exception {
        ICloudStorage cloudStorage = CloudUtils.createStorage(config.getString(Const.Config.CoreS3BucketProp), config);
        FileStorage fileStorage = new TmpFileStorage();
        ObjectWriter jsonWriter = JsonUtil.createJsonWriter();
        Clock clock = new InstantClock();
        VersionGenerator versionGenerator = new EpochVersionGenerator(clock);
        FileManager fileManager = new FileManager(cloudStorage, fileStorage);

        AdminKeysetStoreFactory adminKeysetStoreFactory = new AdminKeysetStoreFactory(
                cloudStorage,
                new CloudPath(config.getString(Const.Config.KeysetsMetadataPathProp)),
                jsonWriter,
                versionGenerator,
                clock,
                fileManager);

        KeysetStoreFactory keysetStoreFactory = new KeysetStoreFactory(
                cloudStorage,
                new CloudPath(config.getString("admin_keyset_metadata_path")),
                jsonWriter,
                versionGenerator,
                clock,
                fileManager);

        SiteStoreFactory siteStoreFactory = new SiteStoreFactory(
                cloudStorage,
                new CloudPath(config.getString(RotatingSiteStore.SITES_METADATA_PATH)),
                jsonWriter,
                versionGenerator,
                clock,
                fileManager);


        synchronized (writeLock) {
            adminKeysetStoreFactory.getGlobalReader().loadContent();
            keysetStoreFactory.getGlobalReader().loadContent();
            siteStoreFactory.getGlobalReader().loadContent();
        }

        Map<Integer, AdminKeyset> allAdminKeysets = adminKeysetStoreFactory.getGlobalReader().getAll();
        Map<ClientType, Set<Integer>> siteIdsByClientType = Map.of(
                ClientType.DSP, new HashSet<>(),
                ClientType.ADVERTISER, new HashSet<>(),
                ClientType.DATA_PROVIDER, new HashSet<>(),
                ClientType.PUBLISHER, new HashSet<>()
        );
        for(Site site: siteStoreFactory.getGlobalReader().getAllSites()) {
            int siteId = site.getId();
            for(ClientType type: site.getTypes()) {
                siteIdsByClientType.get(type).add(siteId);
            }
        }

        Map<Integer, Keyset> keysetMap = new HashMap<>();

        for (AdminKeyset adminKeyset : allAdminKeysets.values()) {
            Keyset keyset = adminKeysetToKeyset(adminKeyset, siteIdsByClientType);
            keysetMap.put(keyset.getKeysetId(), keyset);
        }

        keysetStoreFactory.getGlobalWriter().upload(keysetMap, null);
    }
}
