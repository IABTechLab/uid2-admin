package com.uid2.admin.job.jobsync;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.job.model.Job;
import com.uid2.admin.model.Site;
import com.uid2.admin.store.*;
import com.uid2.admin.store.reader.RotatingSiteStore;
import com.uid2.admin.store.version.EpochVersionGenerator;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.vertx.JsonUtil;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.Const;
import com.uid2.shared.auth.ClientKey;
import com.uid2.shared.auth.EncryptionKeyAcl;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.auth.RotatingOperatorKeyProvider;
import com.uid2.shared.cloud.CloudUtils;
import com.uid2.shared.cloud.ICloudStorage;
import com.uid2.shared.model.EncryptionKey;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.scope.GlobalScope;
import io.vertx.core.json.JsonObject;

import java.util.Collection;
import java.util.Map;

/*
 * The single job that would refresh private sites data for Site/Client/EncryptionKey/KeyAcl) data type
 */
public class OverallSyncJob implements Job {

    public final JsonObject config;

    private final WriteLock writeLock;

    public OverallSyncJob(JsonObject config, WriteLock writeLock) {
        this.config = config;
        this.writeLock = writeLock;
    }

    @Override
    public String getId() {
        return "global-to-site-scope-sync-overall";
    }

    @Override
    public void execute() throws Exception {
        ICloudStorage cloudStorage = CloudUtils.createStorage(config.getString(Const.Config.CoreS3BucketProp), config);
        FileStorage fileStorage = new TmpFileStorage();
        ObjectWriter jsonWriter = JsonUtil.createJsonWriter();
        FileManager fileManager = new FileManager(cloudStorage, fileStorage);
        Clock clock = new InstantClock();
        VersionGenerator versionGenerator = new EpochVersionGenerator(clock);

        SiteStoreFactory siteStoreFactory = new SiteStoreFactory(cloudStorage,
                new CloudPath(config.getString(RotatingSiteStore.SITES_METADATA_PATH)),
                fileStorage,
                jsonWriter,
                versionGenerator,
                clock);

        ClientKeyStoreFactory clientKeyStoreFactory = new ClientKeyStoreFactory(cloudStorage,
                new CloudPath(config.getString(Const.Config.ClientsMetadataPathProp)),
                fileStorage,
                jsonWriter,
                versionGenerator,
                clock);

        EncryptionKeyStoreFactory encryptionKeyStoreFactory = new EncryptionKeyStoreFactory(cloudStorage,
                new CloudPath(config.getString(Const.Config.KeysMetadataPathProp)),
                fileStorage,
                jsonWriter,
                versionGenerator,
                clock);

        KeyAclStoreFactory keyAclStoreFactory = new KeyAclStoreFactory(cloudStorage,
                new CloudPath(config.getString(Const.Config.KeysAclMetadataPathProp)),
                fileStorage,
                jsonWriter,
                versionGenerator,
                clock);

        CloudPath operatorMetadataPath = new CloudPath(config.getString(Const.Config.OperatorsMetadataPathProp));
        GlobalScope operatorScope = new GlobalScope(operatorMetadataPath);
        RotatingOperatorKeyProvider operatorKeyProvider = new RotatingOperatorKeyProvider(cloudStorage, cloudStorage, operatorScope);

        // TODO: if we put a global write lock (com.uid2.admin.vertx.service.AdminKeyService.writeLock)
        // so that we will get a single consistent version of everything before generating private site data
        synchronized (writeLock)
        {
            operatorKeyProvider.loadContent(operatorKeyProvider.getMetadata());
            siteStoreFactory.getGlobalReader().loadContent(siteStoreFactory.getGlobalReader().getMetadata());
            clientKeyStoreFactory.getGlobalReader().loadContent();
            encryptionKeyStoreFactory.getGlobalReader().loadContent();
            keyAclStoreFactory.getGlobalReader().loadContent();
        }

        Collection<OperatorKey> globalOperators = operatorKeyProvider.getAll();
        Collection<Site> globalSites = siteStoreFactory.getGlobalReader().getAllSites();
        Collection<ClientKey> globalClients = clientKeyStoreFactory.getGlobalReader().getAll();
        Collection<EncryptionKey> globalEncryptionKeys = encryptionKeyStoreFactory.getGlobalReader().getSnapshot().getActiveKeySet();
        Map<Integer, EncryptionKeyAcl> globalKeyAcls = keyAclStoreFactory.getGlobalReader().getSnapshot().getAllAcls();

        SiteSyncJob siteSyncJob = new SiteSyncJob(siteStoreFactory, globalSites, globalOperators);
        ClientKeySyncJob clientSyncJob = new ClientKeySyncJob(clientKeyStoreFactory, globalClients, globalOperators);
        EncryptionKeySyncJob encryptionKeySyncJob = new EncryptionKeySyncJob(encryptionKeyStoreFactory, globalEncryptionKeys, globalClients, globalOperators, globalKeyAcls);
        KeyAclSyncJob keyAclSyncJob = new KeyAclSyncJob(keyAclStoreFactory, globalOperators, globalKeyAcls);

        siteSyncJob.execute();
        clientSyncJob.execute();
        encryptionKeySyncJob.execute();
        keyAclSyncJob.execute();
    }
}
