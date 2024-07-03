package com.uid2.admin.job.jobsync.keyset;

import com.uid2.admin.job.model.Job;
import com.uid2.admin.store.MultiScopeStoreWriter;
import com.uid2.admin.util.PrivateSiteUtil;
import com.uid2.shared.auth.Keyset;
import com.uid2.shared.auth.OperatorKey;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class SiteKeysetSyncJob extends Job {
    private final Collection<OperatorKey> globalOperators;
    private final Map<Integer, Keyset> globalKeysets;
    private final MultiScopeStoreWriter<Map<Integer, Keyset>> multiScopeStoreWriter;

    public SiteKeysetSyncJob(
            MultiScopeStoreWriter<Map<Integer, Keyset>> multiScopeStoreWriter,
            Collection<OperatorKey> globalOperators,
            Map<Integer, Keyset> globalKeysets) {
        this.globalOperators = globalOperators;
        this.globalKeysets = globalKeysets;
        this.multiScopeStoreWriter = multiScopeStoreWriter;
    }

    @Override
    public String getId() {
        return "global-to-site-scope-sync-keysets";
    }

    @Override
    public void execute() throws Exception {
        HashMap<Integer, Map<Integer, Keyset>> desiredState = PrivateSiteUtil.getKeysetForEachSite(globalOperators, globalKeysets);
        multiScopeStoreWriter.uploadWithEncryptionOrChanges(desiredState, null);
    }
}
