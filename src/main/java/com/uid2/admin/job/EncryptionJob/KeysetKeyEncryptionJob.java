package com.uid2.admin.job.EncryptionJob;

import com.uid2.admin.job.model.Job;
import com.uid2.admin.model.PrivateSiteDataMap;
import com.uid2.admin.store.MultiScopeStoreWriter;
import com.uid2.admin.store.writer.KeysetKeyStoreWriter;
import com.uid2.admin.util.PrivateSiteUtil;
import com.uid2.admin.util.PublicSiteUtil;
import com.uid2.shared.auth.Keyset;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.model.KeysetKey;

import java.util.Collection;
import java.util.Map;

public class KeysetKeyEncryptionJob extends Job {
    private final Collection<OperatorKey> globalOperators;
    private final Collection<KeysetKey> globalKeysetKeys;
    private final Map<Integer, Keyset> globalKeysets;
    private final Integer globalMaxKeyId;

    private final MultiScopeStoreWriter<Collection<KeysetKey>> multiScopeStoreWriter;

    public KeysetKeyEncryptionJob(Collection<OperatorKey> globalOperators,
                            Collection<KeysetKey> globalKeysetKeys,
                            Map<Integer, Keyset> globalKeysets,
                            Integer globalMaxKeyId,
                            MultiScopeStoreWriter<Collection<KeysetKey>> multiScopeStoreWriter) {
        this.globalOperators = globalOperators;
        this.globalKeysetKeys = globalKeysetKeys;
        this.globalKeysets = globalKeysets;
        this.globalMaxKeyId = globalMaxKeyId;
        this.multiScopeStoreWriter = multiScopeStoreWriter;
    }

    @Override
    public String getId() {
        return "s3-encryption-sync-keysetKeys";
    }

    @Override
    public void execute() throws Exception {
        PrivateSiteDataMap<KeysetKey> desiredPrivateState = PrivateSiteUtil.getKeysetKeys(globalOperators, globalKeysetKeys, globalKeysets);
        multiScopeStoreWriter.uploadEncrypted(desiredPrivateState, KeysetKeyStoreWriter.maxKeyMeta(globalMaxKeyId));
        PrivateSiteDataMap<KeysetKey> desiredPublicState = PublicSiteUtil.getPublicKeysetKeys(globalKeysetKeys, globalOperators);
        multiScopeStoreWriter.uploadPublicWithEncryption(desiredPublicState, null);
    }
}
