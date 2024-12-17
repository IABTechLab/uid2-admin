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
import com.uid2.shared.cloud.TaggableCloudStorage;
import com.uid2.shared.model.ClientSideKeypair;
import com.uid2.shared.model.EncryptionKey;
import com.uid2.shared.model.KeysetKey;
import com.uid2.shared.model.Site;
import com.uid2.shared.store.CloudPath;
import com.uid2.admin.legacy.LegacyClientKey;
import com.uid2.shared.store.EncryptedRotatingSaltProvider;
import com.uid2.shared.store.RotatingSaltProvider;
import com.uid2.shared.store.reader.RotatingCloudEncryptionKeyProvider;
import com.uid2.shared.store.scope.GlobalScope;
import io.vertx.core.json.JsonObject;

import java.util.*;

import static com.uid2.admin.AdminConst.enableKeysetConfigProp;

public class EncryptedFilesSyncJob extends Job {
    private final JsonObject config;
    private final WriteLock writeLock;
    private final RotatingCloudEncryptionKeyProvider rotatingCloudEncryptionKeyProvider;

    public EncryptedFilesSyncJob(JsonObject config, WriteLock writeLock, RotatingCloudEncryptionKeyProvider RotatingCloudEncryptionKeyProvider) {
        this.config = config;
        this.writeLock = writeLock;
        this.rotatingCloudEncryptionKeyProvider = RotatingCloudEncryptionKeyProvider;
    }

    @Override
    public String getId() {
        return "encrypted-files-sync-job";
    }

    @Override
    public void execute() throws Exception {
        TaggableCloudStorage cloudStorage = CloudUtils.createStorage(config.getString(Const.Config.CoreS3BucketProp), config);
        FileStorage fileStorage = new TmpFileStorage();
        ObjectWriter jsonWriter = JsonUtil.createJsonWriter();
        Clock clock = new InstantClock();
        VersionGenerator versionGenerator = new EpochVersionGenerator(clock);
        FileManager fileManager = new FileManager(cloudStorage, fileStorage);

        RotatingSaltProvider saltProvider = new RotatingSaltProvider(cloudStorage, config.getString(Const.Config.SaltsMetadataPathProp));

        SiteStoreFactory siteStoreFactory = new SiteStoreFactory(
                cloudStorage,
                new CloudPath(config.getString(Const.Config.SitesMetadataPathProp)),
                jsonWriter,
                versionGenerator,
                clock,
                rotatingCloudEncryptionKeyProvider,
                fileManager);

        ClientKeyStoreFactory clientKeyStoreFactory = new ClientKeyStoreFactory(
                cloudStorage,
                new CloudPath(config.getString(Const.Config.ClientsMetadataPathProp)),
                jsonWriter,
                versionGenerator,
                clock,
                rotatingCloudEncryptionKeyProvider,
                fileManager);

        EncryptionKeyStoreFactory encryptionKeyStoreFactory = new EncryptionKeyStoreFactory(
                cloudStorage,
                new CloudPath(config.getString(Const.Config.KeysMetadataPathProp)),
                versionGenerator,
                clock,
                rotatingCloudEncryptionKeyProvider,
                fileManager);

        KeyAclStoreFactory keyAclStoreFactory = new KeyAclStoreFactory(
                cloudStorage,
                new CloudPath(config.getString(Const.Config.KeysAclMetadataPathProp)),
                jsonWriter,
                versionGenerator,
                clock,
                rotatingCloudEncryptionKeyProvider,
                fileManager);

        KeysetStoreFactory keysetStoreFactory = new KeysetStoreFactory(
                cloudStorage,
                new CloudPath(config.getString(Const.Config.KeysetsMetadataPathProp)),
                jsonWriter,
                versionGenerator,
                clock,
                fileManager,
                rotatingCloudEncryptionKeyProvider,
                config.getBoolean(enableKeysetConfigProp));

        KeysetKeyStoreFactory keysetKeyStoreFactory = new KeysetKeyStoreFactory(
                cloudStorage,
                new CloudPath(config.getString(Const.Config.KeysetKeysMetadataPathProp)),
                versionGenerator,
                clock,
                fileManager,
                rotatingCloudEncryptionKeyProvider,
                config.getBoolean(enableKeysetConfigProp));

        SaltStoreFactory saltStoreFactory = new SaltStoreFactory(
                config,
                new CloudPath(config.getString(Const.Config.SaltsMetadataPathProp)),
                fileManager,
                cloudStorage,
                versionGenerator,
                rotatingCloudEncryptionKeyProvider
        );

        ClientSideKeypairStoreFactory clientSideKeypairStoreFactory = new ClientSideKeypairStoreFactory(
                cloudStorage,
                new CloudPath(config.getString(Const.Config.ClientSideKeypairsMetadataPathProp)),
                versionGenerator,
                clock,
                rotatingCloudEncryptionKeyProvider,
                fileManager
        );

        CloudPath operatorMetadataPath = new CloudPath(config.getString(Const.Config.OperatorsMetadataPathProp));
        GlobalScope operatorScope = new GlobalScope(operatorMetadataPath);
        RotatingOperatorKeyProvider operatorKeyProvider = new RotatingOperatorKeyProvider(cloudStorage, cloudStorage, operatorScope);

        synchronized (writeLock) {
            rotatingCloudEncryptionKeyProvider.loadContent();
            operatorKeyProvider.loadContent(operatorKeyProvider.getMetadata());
            siteStoreFactory.getGlobalReader().loadContent(siteStoreFactory.getGlobalReader().getMetadata());
            clientKeyStoreFactory.getGlobalReader().loadContent();
            encryptionKeyStoreFactory.getGlobalReader().loadContent();
            keyAclStoreFactory.getGlobalReader().loadContent();
            if(config.getBoolean(enableKeysetConfigProp)) {
                keysetStoreFactory.getGlobalReader().loadContent();
                keysetKeyStoreFactory.getGlobalReader().loadContent();
            }
            saltProvider.loadContent();
            clientSideKeypairStoreFactory.getGlobalReader().loadContent();
        }

        Collection<OperatorKey> globalOperators = operatorKeyProvider.getAll();
        Collection<Site> globalSites = siteStoreFactory.getGlobalReader().getAllSites();
        Collection<LegacyClientKey> globalClients = clientKeyStoreFactory.getGlobalReader().getAll();
        Collection<EncryptionKey> globalEncryptionKeys = encryptionKeyStoreFactory.getGlobalReader().getSnapshot().getActiveKeySet();
        Integer globalMaxKeyId = encryptionKeyStoreFactory.getGlobalReader().getMetadata().getInteger("max_key_id");
        Map<Integer, EncryptionKeyAcl> globalKeyAcls = keyAclStoreFactory.getGlobalReader().getSnapshot().getAllAcls();
        Collection<ClientSideKeypair> globalClientSideKeypair = clientSideKeypairStoreFactory.getGlobalReader().getAll();

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
        MultiScopeStoreWriter<Collection<RotatingSaltProvider.SaltSnapshot>> saltWriter = new MultiScopeStoreWriter<>(
                fileManager,
                saltStoreFactory,
                MultiScopeStoreWriter::areCollectionsEqual);
        MultiScopeStoreWriter<Collection<ClientSideKeypair>> clientSideKeypairWriter = new MultiScopeStoreWriter<>(
                fileManager,
                clientSideKeypairStoreFactory,
                MultiScopeStoreWriter::areCollectionsEqual);

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
        SaltEncryptionJob saltEncryptionJob = new SaltEncryptionJob(globalOperators, saltProvider.getSnapshots(), saltWriter);
        ClientSideKeypairEncryptionJob clientSideKeypairEncryptionJob = new ClientSideKeypairEncryptionJob(globalOperators, globalClientSideKeypair, clientSideKeypairWriter);

        siteEncryptionSyncJob.execute();
        clientEncryptionSyncJob.execute();
        encryptionKeyEncryptionSyncJob.execute();
        keyAclEncryptionSyncJob.execute();
        saltEncryptionJob.execute();
        clientSideKeypairEncryptionJob.execute();

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