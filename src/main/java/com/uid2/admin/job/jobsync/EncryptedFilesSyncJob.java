package com.uid2.admin.job.jobsync;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.job.EncryptionJob.*;
import com.uid2.admin.job.model.Job;
import com.uid2.admin.store.*;
import com.uid2.admin.store.factory.*;
import com.uid2.admin.store.version.EpochVersionGenerator;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.vertx.JsonUtil;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.Const;
import com.uid2.shared.auth.EncryptionKeyAcl;
import com.uid2.shared.auth.Keyset;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.auth.RotatingOperatorKeyProvider;
import com.uid2.shared.cloud.CloudUtils;
import com.uid2.shared.cloud.ICloudStorage;
import com.uid2.shared.model.EncryptionKey;
import com.uid2.shared.model.KeysetKey;
import com.uid2.shared.model.Site;
import com.uid2.shared.store.CloudPath;
import com.uid2.admin.legacy.LegacyClientKey;
import com.uid2.shared.store.reader.RotatingCloudEncryptionKeyProvider;
import com.uid2.shared.store.scope.GlobalScope;
import io.vertx.core.json.JsonObject;

import java.util.*;

import static com.uid2.admin.AdminConst.enableKeysetConfigProp;

public class EncryptedFilesSyncJob extends Job {
    private final JsonObject config;
    private final WriteLock writeLock;
    private final RotatingCloudEncryptionKeyProvider RotatingCloudEncryptionKeyProvider;

    public EncryptedFilesSyncJob(JsonObject config, WriteLock writeLock, RotatingCloudEncryptionKeyProvider RotatingCloudEncryptionKeyProvider) {
        this.config = config;
        this.writeLock = writeLock;
        this.RotatingCloudEncryptionKeyProvider = RotatingCloudEncryptionKeyProvider;
    }

    @Override
    public String getId() {
        return "encrypted-files-sync-job";
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
                new CloudPath(config.getString(Const.Config.SitesMetadataPathProp)),
                jsonWriter,
                versionGenerator,
                clock,
                RotatingCloudEncryptionKeyProvider,
                fileManager);

        ClientKeyStoreFactory clientKeyStoreFactory = new ClientKeyStoreFactory(
                cloudStorage,
                new CloudPath(config.getString(Const.Config.ClientsMetadataPathProp)),
                jsonWriter,
                versionGenerator,
                clock,
                RotatingCloudEncryptionKeyProvider,
                fileManager);

        EncryptionKeyStoreFactory encryptionKeyStoreFactory = new EncryptionKeyStoreFactory(
                cloudStorage,
                new CloudPath(config.getString(Const.Config.KeysMetadataPathProp)),
                versionGenerator,
                clock,
                RotatingCloudEncryptionKeyProvider,
                fileManager);

        KeyAclStoreFactory keyAclStoreFactory = new KeyAclStoreFactory(
                cloudStorage,
                new CloudPath(config.getString(Const.Config.KeysAclMetadataPathProp)),
                jsonWriter,
                versionGenerator,
                clock,
                RotatingCloudEncryptionKeyProvider,
                fileManager);

        KeysetStoreFactory keysetStoreFactory = new KeysetStoreFactory(
                cloudStorage,
                new CloudPath(config.getString(Const.Config.KeysetsMetadataPathProp)),
                jsonWriter,
                versionGenerator,
                clock,
                fileManager,
                RotatingCloudEncryptionKeyProvider,
                config.getBoolean(enableKeysetConfigProp));

        KeysetKeyStoreFactory keysetKeyStoreFactory = new KeysetKeyStoreFactory(
                cloudStorage,
                new CloudPath(config.getString(Const.Config.KeysetKeysMetadataPathProp)),
                versionGenerator,
                clock,
                fileManager,
                RotatingCloudEncryptionKeyProvider,
                config.getBoolean(enableKeysetConfigProp));

        CloudPath operatorMetadataPath = new CloudPath(config.getString(Const.Config.OperatorsMetadataPathProp));
        GlobalScope operatorScope = new GlobalScope(operatorMetadataPath);
        RotatingOperatorKeyProvider operatorKeyProvider = new RotatingOperatorKeyProvider(cloudStorage, cloudStorage, operatorScope);

        synchronized (writeLock) {
            RotatingCloudEncryptionKeyProvider.loadContent();
            operatorKeyProvider.loadContent(operatorKeyProvider.getMetadata());
            siteStoreFactory.getGlobalReader().loadContent(siteStoreFactory.getGlobalReader().getMetadata());
            clientKeyStoreFactory.getGlobalReader().loadContent();
            encryptionKeyStoreFactory.getGlobalReader().loadContent();
            keyAclStoreFactory.getGlobalReader().loadContent();
            if(config.getBoolean(enableKeysetConfigProp)) {
                keysetStoreFactory.getGlobalReader().loadContent();
                keysetKeyStoreFactory.getGlobalReader().loadContent();
            }
        }
        Collection<OperatorKey> globalOperators = operatorKeyProvider.getAll();
        Collection<Site> globalSites = siteStoreFactory.getGlobalReader().getAllSites();
        Collection<LegacyClientKey> globalClients = clientKeyStoreFactory.getGlobalReader().getAll();
        Collection<EncryptionKey> globalEncryptionKeys = encryptionKeyStoreFactory.getGlobalReader().getSnapshot().getActiveKeySet();
        Integer globalMaxKeyId = encryptionKeyStoreFactory.getGlobalReader().getMetadata().getInteger("max_key_id");
        Map<Integer, EncryptionKeyAcl> globalKeyAcls = keyAclStoreFactory.getGlobalReader().getSnapshot().getAllAcls();
        MultiScopeStoreWriter<Collection<Site>> siteWriter = new MultiScopeStoreWriter<>(
                fileManager,
                siteStoreFactory,
                MultiScopeStoreWriter::areCollectionsEqual);
        MultiScopeStoreWriter<Collection<LegacyClientKey>> clientWriter = new MultiScopeStoreWriter<>(
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

        SiteEncryptionJob siteEncryptionSyncJob = new SiteEncryptionJob(siteWriter, globalSites, globalOperators);
        ClientKeyEncryptionJob clientEncryptionSyncJob = new ClientKeyEncryptionJob(clientWriter, globalClients, globalOperators);
        EncryptionKeyEncryptionJob encryptionKeyEncryptionSyncJob = new EncryptionKeyEncryptionJob(
                globalEncryptionKeys,
                globalClients,
                globalOperators,
                globalKeyAcls,
                globalMaxKeyId,
                encryptionKeyWriter
        );
        KeyAclEncryptionJob keyAclEncryptionSyncJob = new KeyAclEncryptionJob(keyAclWriter, globalOperators, globalKeyAcls);
        siteEncryptionSyncJob.execute();
        clientEncryptionSyncJob.execute();
        encryptionKeyEncryptionSyncJob.execute();
        keyAclEncryptionSyncJob.execute();

        if(config.getBoolean(enableKeysetConfigProp)) {
            Map<Integer, Keyset> globalKeysets = keysetStoreFactory.getGlobalReader().getSnapshot().getAllKeysets();
            Collection<KeysetKey> globalKeysetKeys = keysetKeyStoreFactory.getGlobalReader().getSnapshot().getAllKeysetKeys();
            Integer globalMaxKeysetKeyId = keysetKeyStoreFactory.getGlobalReader().getMetadata().getInteger("max_key_id");
            MultiScopeStoreWriter<Map<Integer, Keyset>> keysetWriter = new MultiScopeStoreWriter<>(
                    fileManager,
                    keysetStoreFactory,
                    MultiScopeStoreWriter::areMapsEqual);
            MultiScopeStoreWriter<Collection<KeysetKey>> keysetKeyWriter = new MultiScopeStoreWriter<>(
                    fileManager,
                    keysetKeyStoreFactory,
                    MultiScopeStoreWriter::areCollectionsEqual);
            SiteKeysetEncryptionJob keysetEncryptionSyncJob = new SiteKeysetEncryptionJob(keysetWriter, globalOperators, globalKeysets);
            KeysetKeyEncryptionJob keysetKeyEncryptionSyncJob = new KeysetKeyEncryptionJob(globalOperators, globalKeysetKeys, globalKeysets, globalMaxKeysetKeyId, keysetKeyWriter);

            keysetEncryptionSyncJob.execute();
            keysetKeyEncryptionSyncJob.execute();
        }
    }
}