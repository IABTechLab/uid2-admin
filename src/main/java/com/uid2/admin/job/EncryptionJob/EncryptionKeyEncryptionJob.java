package com.uid2.admin.job.EncryptionJob;

import com.uid2.admin.job.model.EncryptedJob;
import com.uid2.admin.job.model.Job;
import com.uid2.admin.legacy.LegacyClientKey;
import com.uid2.admin.model.PrivateSiteDataMap;
import com.uid2.admin.store.MultiScopeStoreWriter;
import com.uid2.admin.store.writer.EncryptionKeyStoreWriter;
import com.uid2.admin.util.PrivateSiteUtil;
import com.uid2.admin.util.PublicSiteUtil;
import com.uid2.shared.auth.EncryptionKeyAcl;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.model.EncryptionKey;
import io.vertx.core.json.JsonObject;

import java.util.Collection;
import java.util.Map;

public class EncryptionKeyEncryptionJob extends EncryptedJob {
    private final Collection<OperatorKey> globalOperators;
    private final Collection<EncryptionKey> globalEncryptionKeys;
    private final Collection<LegacyClientKey> globalClientKeys;
    private final Map<Integer, EncryptionKeyAcl> globalAcls;
    private final Integer globalMaxKeyId;

    private final MultiScopeStoreWriter<Collection<EncryptionKey>> multiScopeStoreWriter;

    public EncryptionKeyEncryptionJob(
            Collection<EncryptionKey> globalEncryptionKeys,
            Collection<LegacyClientKey> globalClientKeys,
            Collection<OperatorKey> globalOperators,
            Map<Integer, EncryptionKeyAcl> globalAcls,
            Integer globalMaxKeyId,
            MultiScopeStoreWriter<Collection<EncryptionKey>> multiScopeStoreWriter,
            Long version) {
        super(version);
        this.globalEncryptionKeys = globalEncryptionKeys;
        this.globalClientKeys = globalClientKeys;
        this.globalOperators = globalOperators;
        this.globalAcls = globalAcls;
        this.globalMaxKeyId = globalMaxKeyId;
        this.multiScopeStoreWriter = multiScopeStoreWriter;
    }

    @Override
    public String getId() {
        return "cloud-encryption-sync-encryptionKeys";
    }

    @Override
    public void execute() throws Exception {
        JsonObject extraMeta = this.getBaseMetadata();
        extraMeta.put("max_key_id", globalMaxKeyId);
        PrivateSiteDataMap<EncryptionKey> desiredPrivateState = PrivateSiteUtil.getEncryptionKeys(globalOperators, globalEncryptionKeys, globalAcls, globalClientKeys);
        multiScopeStoreWriter.uploadPrivateWithEncryption(desiredPrivateState,extraMeta );
        PrivateSiteDataMap<EncryptionKey> desiredPublicState = PublicSiteUtil.getPublicEncryptionKeys(globalEncryptionKeys, globalOperators);
        multiScopeStoreWriter.uploadPublicWithEncryption(desiredPublicState, extraMeta);
    }
}
