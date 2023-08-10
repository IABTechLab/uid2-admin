package com.uid2.admin.job.jobsync;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.job.jobsync.acl.KeyAclSyncJob;
import com.uid2.admin.job.jobsync.client.ClientKeySyncJob;
//import com.uid2.admin.job.jobsync.key.ClientSideKeypairSyncJob;
import com.uid2.admin.job.jobsync.key.EncryptionKeySyncJob;
import com.uid2.admin.job.jobsync.key.KeysetKeySyncJob;
import com.uid2.admin.job.jobsync.keyset.KeysetSyncJob;
import com.uid2.admin.job.jobsync.site.SiteSyncJob;
import com.uid2.admin.job.model.Job;
import com.uid2.admin.model.Site;
import com.uid2.admin.store.*;
import com.uid2.admin.store.factory.*;
import com.uid2.admin.store.reader.RotatingSiteStore;
import com.uid2.admin.store.version.EpochVersionGenerator;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.vertx.JsonUtil;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.Const;
import com.uid2.shared.auth.*;
import com.uid2.shared.cloud.CloudUtils;
import com.uid2.shared.cloud.ICloudStorage;
//import com.uid2.shared.model.ClientSideKeypair;
import com.uid2.shared.model.EncryptionKey;
import com.uid2.shared.model.KeysetKey;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.scope.GlobalScope;
import io.vertx.core.json.JsonObject;

import java.util.Collection;
import java.util.Map;

import static com.uid2.admin.AdminConst.enableKeysetConfigProp;

/*
 * The single job that would refresh private sites data for Site/Client/EncryptionKey/KeyAcl data type
 */
public class PrivateSiteDataSyncJob extends Job {
    public final JsonObject config;
    private final WriteLock writeLock;

    public PrivateSiteDataSyncJob(JsonObject config, WriteLock writeLock) {
        this.config = config;
        this.writeLock = writeLock;
    }

    @Override
    public String getId() {
        return "global-to-site-scope-sync-private-site-data";
    }

    @Override
    public void execute() throws Exception {
        ICloudStorage cloudStorage = CloudUtils.createStorage(config.getString(Const.Config.CoreS3BucketProp), config);
        FileStorage fileStorage = new TmpFileStorage();
        ObjectWriter jsonWriter = JsonUtil.createJsonWriter();
        Clock clock = new InstantClock();
        VersionGenerator versionGenerator = new EpochVersionGenerator(clock);
        FileManager fileManager = new FileManager(cloudStorage, fileStorage);

        SiteStoreFactory siteStoreFactory = new SiteStoreFactory(
                cloudStorage,
                new CloudPath(config.getString(RotatingSiteStore.SITES_METADATA_PATH)),
                jsonWriter,
                versionGenerator,
                clock,
                fileManager);

        ClientKeyStoreFactory clientKeyStoreFactory = new ClientKeyStoreFactory(
                cloudStorage,
                new CloudPath(config.getString(Const.Config.ClientsMetadataPathProp)),
                jsonWriter,
                versionGenerator,
                clock,
                fileManager);

        EncryptionKeyStoreFactory encryptionKeyStoreFactory = new EncryptionKeyStoreFactory(
                cloudStorage,
                new CloudPath(config.getString(Const.Config.KeysMetadataPathProp)),
                versionGenerator,
                clock,
                fileManager);

        KeyAclStoreFactory keyAclStoreFactory = new KeyAclStoreFactory(
                cloudStorage,
                new CloudPath(config.getString(Const.Config.KeysAclMetadataPathProp)),
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
                config.getBoolean(enableKeysetConfigProp));

        KeysetKeyStoreFactory keysetKeyStoreFactory = new KeysetKeyStoreFactory(
                cloudStorage,
                new CloudPath(config.getString(Const.Config.KeysetKeysMetadataPathProp)),
                versionGenerator,
                clock,
                fileManager,
                config.getBoolean(enableKeysetConfigProp));
        // disabled for private operator
//        ClientSideKeypairStoreFactory keypairStoreFactory = new ClientSideKeypairStoreFactory(
//                cloudStorage,
//                new CloudPath(config.getString(Const.Config.ClientSideKeypairsMetadataPathProp)),
//                versionGenerator,
//                clock,
//                fileManager
//        );

        CloudPath operatorMetadataPath = new CloudPath(config.getString(Const.Config.OperatorsMetadataPathProp));
        GlobalScope operatorScope = new GlobalScope(operatorMetadataPath);
        RotatingOperatorKeyProvider operatorKeyProvider = new RotatingOperatorKeyProvider(cloudStorage, cloudStorage, operatorScope);

        // so that we will get a single consistent version of everything before generating private site data
        synchronized (writeLock) {
            operatorKeyProvider.loadContent(operatorKeyProvider.getMetadata());
            siteStoreFactory.getGlobalReader().loadContent(siteStoreFactory.getGlobalReader().getMetadata());
            clientKeyStoreFactory.getGlobalReader().loadContent();
            encryptionKeyStoreFactory.getGlobalReader().loadContent();
            keyAclStoreFactory.getGlobalReader().loadContent();
//            keypairStoreFactory.getGlobalReader().loadContent(); // disabled for private operator
            if(config.getBoolean(enableKeysetConfigProp)) {
                keysetStoreFactory.getGlobalReader().loadContent();
                keysetKeyStoreFactory.getGlobalReader().loadContent();
            }
        }

        Collection<OperatorKey> globalOperators = operatorKeyProvider.getAll();
        Collection<Site> globalSites = siteStoreFactory.getGlobalReader().getAllSites();
        Collection<ClientKey> globalClients = clientKeyStoreFactory.getGlobalReader().getAll();
        Collection<EncryptionKey> globalEncryptionKeys = encryptionKeyStoreFactory.getGlobalReader().getSnapshot().getActiveKeySet();
        Integer globalMaxKeyId = encryptionKeyStoreFactory.getGlobalReader().getMetadata().getInteger("max_key_id");
        Map<Integer, EncryptionKeyAcl> globalKeyAcls = keyAclStoreFactory.getGlobalReader().getSnapshot().getAllAcls();
//        Collection<ClientSideKeypair> globalKeypairs = keypairStoreFactory.getGlobalReader().getSnapshot().getEnabledKeypairs(); // disabled for private operators

        MultiScopeStoreWriter<Collection<Site>> siteWriter = new MultiScopeStoreWriter<>(
                fileManager,
                siteStoreFactory,
                MultiScopeStoreWriter::areCollectionsEqual);
        MultiScopeStoreWriter<Collection<ClientKey>> clientWriter = new MultiScopeStoreWriter<>(
                fileManager,
                clientKeyStoreFactory,
                MultiScopeStoreWriter::areCollectionsEqual);
        MultiScopeStoreWriter<Collection<EncryptionKey>> encryptionKeyWriter = new MultiScopeStoreWriter<>(
                fileManager,
                encryptionKeyStoreFactory,
                MultiScopeStoreWriter::areCollectionsEqual);
        MultiScopeStoreWriter<Map<Integer, EncryptionKeyAcl>> keyAclWriter = new MultiScopeStoreWriter<>(
                fileManager,
                keyAclStoreFactory,
                MultiScopeStoreWriter::areMapsEqual);
        // disabled for private operators
//        MultiScopeStoreWriter<Collection<ClientSideKeypair>> keypairWriter = new MultiScopeStoreWriter<>(
//                fileManager,
//                keypairStoreFactory,
//                MultiScopeStoreWriter::areCollectionsEqual);

        SiteSyncJob siteSyncJob = new SiteSyncJob(siteWriter, globalSites, globalOperators);
        ClientKeySyncJob clientSyncJob = new ClientKeySyncJob(clientWriter, globalClients, globalOperators);
        EncryptionKeySyncJob encryptionKeySyncJob = new EncryptionKeySyncJob(
                globalEncryptionKeys,
                globalClients,
                globalOperators,
                globalKeyAcls,
                globalMaxKeyId,
                encryptionKeyWriter
        );
        KeyAclSyncJob keyAclSyncJob = new KeyAclSyncJob(keyAclWriter, globalOperators, globalKeyAcls);
//        ClientSideKeypairSyncJob keypairSyncJob = new ClientSideKeypairSyncJob(globalOperators, globalKeypairs, keypairWriter); // disabled for private opeartor

        siteSyncJob.execute();
        clientSyncJob.execute();
        encryptionKeySyncJob.execute();
        keyAclSyncJob.execute();
//        keypairSyncJob.execute(); Keypair sync Job should not be executed until we want to enable CSTG for private operators
        if(config.getBoolean(enableKeysetConfigProp)) {
            Map<Integer, Keyset> globalKeysets = keysetStoreFactory.getGlobalReader().getSnapshot().getAllKeysets();
            Collection<KeysetKey> globalKeysetKeys = keysetKeyStoreFactory.getGlobalReader().getSnapshot().getActiveKeysetKeys();
            Integer globalMaxKeysetKeyId = keysetKeyStoreFactory.getGlobalReader().getMetadata().getInteger("max_key_id");
            MultiScopeStoreWriter<Map<Integer, Keyset>> keysetWriter = new MultiScopeStoreWriter<>(
                    fileManager,
                    keysetStoreFactory,
                    MultiScopeStoreWriter::areMapsEqual);
            MultiScopeStoreWriter<Collection<KeysetKey>> keysetKeyWriter = new MultiScopeStoreWriter<>(
                    fileManager,
                    keysetKeyStoreFactory,
                    MultiScopeStoreWriter::areCollectionsEqual);
            KeysetSyncJob keysetSyncJob = new KeysetSyncJob(keysetWriter, globalOperators, globalKeysets);
            KeysetKeySyncJob keysetKeySyncJob = new KeysetKeySyncJob(globalOperators, globalKeysetKeys, globalKeysets, globalMaxKeysetKeyId, keysetKeyWriter);

            keysetSyncJob.execute();
            keysetKeySyncJob.execute();
        }
    }
}
