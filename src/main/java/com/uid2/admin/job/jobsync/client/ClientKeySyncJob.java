package com.uid2.admin.job.jobsync.client;

import com.uid2.admin.job.model.Job;
import com.uid2.admin.legacy.LegacyClientKey;
import com.uid2.admin.model.PrivateSiteDataMap;
import com.uid2.admin.store.MultiScopeStoreWriter;
import com.uid2.admin.util.PrivateSiteUtil;
import com.uid2.admin.util.PublicSiteUtil;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.model.Site;

import java.util.Collection;

public class ClientKeySyncJob extends Job {
    private final Collection<OperatorKey> globalOperators;
    private final Collection<LegacyClientKey> globalClientKeys;
    private final MultiScopeStoreWriter<Collection<LegacyClientKey>> multiScopeStoreWriter;

    public ClientKeySyncJob(
            MultiScopeStoreWriter<Collection<LegacyClientKey>> multiScopeStoreWriter,
            Collection<LegacyClientKey> globalClientKeys,
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
        PrivateSiteDataMap<LegacyClientKey> desiredPrivateState = PrivateSiteUtil.getClientKeys(globalOperators, globalClientKeys);
        multiScopeStoreWriter.uploadWithEncryptionOrChanges(desiredPrivateState, null);
        PrivateSiteDataMap<LegacyClientKey> desiredPublicState = PublicSiteUtil.getPublicClients(globalClientKeys,globalOperators);
        multiScopeStoreWriter.uploadPublicWithEncryption(desiredPublicState, null);
    }
}
