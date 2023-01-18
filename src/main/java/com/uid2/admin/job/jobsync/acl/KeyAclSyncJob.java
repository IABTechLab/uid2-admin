package com.uid2.admin.job.jobsync.acl;

import com.uid2.admin.job.model.Job;
import com.uid2.admin.store.MultiScopeStoreWriter;
import com.uid2.admin.util.PrivateSiteUtil;
import com.uid2.shared.auth.EncryptionKeyAcl;
import com.uid2.shared.auth.OperatorKey;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class KeyAclSyncJob implements Job {
    private final Collection<OperatorKey> globalOperators;
    private final Map<Integer, EncryptionKeyAcl> globalAcls;
    private final MultiScopeStoreWriter<Map<Integer, EncryptionKeyAcl>> multiScopeStoreWriter;

    public KeyAclSyncJob(
            MultiScopeStoreWriter<Map<Integer, EncryptionKeyAcl>> multiScopeStoreWriter,
            Collection<OperatorKey> globalOperators,
            Map<Integer, EncryptionKeyAcl> globalAcls) {
        this.globalOperators = globalOperators;
        this.globalAcls = globalAcls;
        this.multiScopeStoreWriter = multiScopeStoreWriter;
    }

    @Override
    public String getId() {
        return "global-to-site-scope-sync-keyAcls";
    }

    @Override
    public void execute() throws Exception {
        HashMap<Integer, Map<Integer, EncryptionKeyAcl>> desiredState = PrivateSiteUtil.getEncryptionKeyAclsForEachSite(
                globalOperators,
                globalAcls);
        multiScopeStoreWriter.uploadIfChanged(desiredState, null);
    }
}
