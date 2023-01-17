package com.uid2.admin.job.jobsync;

import com.uid2.admin.job.model.Job;
import com.uid2.admin.model.PrivateSiteDataMap;
import com.uid2.admin.store.MultiScopeStoreWriter;
import com.uid2.admin.store.writer.EncryptionKeyStoreWriter;
import com.uid2.admin.util.PrivateSiteUtil;
import com.uid2.shared.auth.ClientKey;
import com.uid2.shared.auth.EncryptionKeyAcl;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.model.EncryptionKey;

import java.util.Collection;
import java.util.Map;

public class EncryptionKeySyncJob implements Job {
    private final Collection<OperatorKey> globalOperators;
    private final Collection<EncryptionKey> globalEncryptionKeys;
    private final Collection<ClientKey> globalClientKeys;
    private final Map<Integer, EncryptionKeyAcl> globalAcls;
    private final Integer globalMaxKeyId;

    private final MultiScopeStoreWriter<Collection<EncryptionKey>> multiScopeStoreWriter;

    public EncryptionKeySyncJob(
            Collection<EncryptionKey> globalEncryptionKeys,
            Collection<ClientKey> globalClientKeys,
            Collection<OperatorKey> globalOperators,
            Map<Integer, EncryptionKeyAcl> globalAcls,
            Integer globalMaxKeyId,
            MultiScopeStoreWriter<Collection<EncryptionKey>> multiScopeStoreWriter) {
        this.globalEncryptionKeys = globalEncryptionKeys;
        this.globalClientKeys = globalClientKeys;
        this.globalOperators = globalOperators;
        this.globalAcls = globalAcls;
        this.globalMaxKeyId = globalMaxKeyId;
        this.multiScopeStoreWriter = multiScopeStoreWriter;
    }

    @Override
    public String getId() {
        return "global-to-site-scope-sync-encryptionKeys";
    }

    @Override
    public void execute() throws Exception {
        PrivateSiteDataMap<EncryptionKey> desiredState = PrivateSiteUtil.getEncryptionKeys(globalOperators, globalEncryptionKeys, globalAcls, globalClientKeys);
        multiScopeStoreWriter.uploadIfChanged(desiredState, EncryptionKeyStoreWriter.maxKeyMeta(globalMaxKeyId));
    }
}
