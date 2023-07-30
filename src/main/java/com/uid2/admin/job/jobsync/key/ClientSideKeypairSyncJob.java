package com.uid2.admin.job.jobsync.key;

import com.uid2.admin.job.model.Job;
import com.uid2.admin.model.PrivateSiteDataMap;
import com.uid2.admin.store.MultiScopeStoreWriter;
import com.uid2.admin.store.writer.KeysetKeyStoreWriter;
import com.uid2.admin.util.PrivateSiteUtil;
import com.uid2.shared.auth.Keyset;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.model.ClientSideKeypair;
import com.uid2.shared.model.KeysetKey;

import java.util.Collection;
import java.util.Map;

public class ClientSideKeypairSyncJob extends Job {
    private final Collection<OperatorKey> globalOperators;
    private final Collection<ClientSideKeypair> globalKeypairs;
    private final MultiScopeStoreWriter<Collection<ClientSideKeypair>> multiScopeStoreWriter;

    public ClientSideKeypairSyncJob(Collection<OperatorKey> globalOperators,
                            Collection<ClientSideKeypair> globalKeypairs,
                            MultiScopeStoreWriter<Collection<ClientSideKeypair>> multiScopeStoreWriter) {
        this.globalOperators = globalOperators;
        this.globalKeypairs = globalKeypairs;
        this.multiScopeStoreWriter = multiScopeStoreWriter;
    }

    @Override
    public String getId() {
        return "global-to-site-scope-sync-clientSideKeypairs";
    }

    @Override
    public void execute() throws Exception {
        PrivateSiteDataMap<ClientSideKeypair> desiredState = PrivateSiteUtil.getClientSideKeypairs(globalOperators, globalKeypairs);
        multiScopeStoreWriter.uploadIfChanged(desiredState, null);
    }
}
