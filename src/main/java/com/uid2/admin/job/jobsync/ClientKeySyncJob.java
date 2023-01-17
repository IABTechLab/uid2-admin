package com.uid2.admin.job.jobsync;

import com.uid2.admin.job.model.Job;
import com.uid2.admin.model.PrivateSiteDataMap;
import com.uid2.admin.store.MultiScopeStoreWriter;
import com.uid2.admin.util.PrivateSiteUtil;
import com.uid2.shared.auth.ClientKey;
import com.uid2.shared.auth.OperatorKey;

import java.util.Collection;

public class ClientKeySyncJob implements Job {
    private final Collection<OperatorKey> globalOperators;
    private final Collection<ClientKey> globalClientKeys;
    private final MultiScopeStoreWriter<Collection<ClientKey>> multiScopeStoreWriter;

    public ClientKeySyncJob(
            MultiScopeStoreWriter<Collection<ClientKey>> multiScopeStoreWriter,
            Collection<ClientKey> globalClientKeys,
            Collection<OperatorKey> globalOperators) {
        this.globalClientKeys = globalClientKeys;
        this.globalOperators = globalOperators;
        this.multiScopeStoreWriter = multiScopeStoreWriter;
    }

    @Override
    public String getId() {
        return "global-to-site-scope-sync-clientKeys";
    }

    @Override
    public void execute() throws Exception {
        PrivateSiteDataMap<ClientKey> desiredState = PrivateSiteUtil.getClientKeys(globalOperators, globalClientKeys);
        multiScopeStoreWriter.uploadIfChanged(desiredState, null);
    }
}
