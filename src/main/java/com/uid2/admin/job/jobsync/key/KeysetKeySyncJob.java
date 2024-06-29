package com.uid2.admin.job.jobsync.key;

import com.uid2.admin.job.model.Job;
import com.uid2.admin.model.PrivateSiteDataMap;
import com.uid2.admin.store.MultiScopeStoreWriter;
import com.uid2.admin.store.writer.KeysetKeyStoreWriter;
import com.uid2.admin.store.writer.KeysetStoreWriter;
import com.uid2.admin.util.PrivateSiteUtil;
import com.uid2.shared.auth.Keyset;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.model.KeysetKey;

import java.util.Collection;
import java.util.Map;

public class KeysetKeySyncJob extends Job {
    private final Collection<OperatorKey> globalOperators;
    private final Collection<KeysetKey> globalKeysetKeys;
    private final Map<Integer, Keyset> globalKeysets;
    private final Integer globalMaxKeyId;

    private final MultiScopeStoreWriter<Collection<KeysetKey>> multiScopeStoreWriter;

    public KeysetKeySyncJob(Collection<OperatorKey> globalOperators,
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
        return "global-to-site-scope-sync-keysetKeys";
    }

    @Override
    public void execute() throws Exception {
        PrivateSiteDataMap<KeysetKey> desiredState = PrivateSiteUtil.getKeysetKeys(globalOperators, globalKeysetKeys, globalKeysets);
        multiScopeStoreWriter.uploadGeneral(desiredState, KeysetKeyStoreWriter.maxKeyMeta(globalMaxKeyId));
    }
}
