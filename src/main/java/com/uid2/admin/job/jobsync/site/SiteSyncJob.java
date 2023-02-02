package com.uid2.admin.job.jobsync.site;

import com.uid2.admin.job.model.Job;
import com.uid2.admin.model.PrivateSiteDataMap;
import com.uid2.admin.model.Site;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.MultiScopeStoreWriter;
import com.uid2.admin.util.PrivateSiteUtil;
import com.uid2.shared.auth.OperatorKey;

import java.util.Collection;

public class SiteSyncJob extends Job {
    private final Collection<OperatorKey> globalOperators;
    private final Collection<Site> globalSites;
    private final MultiScopeStoreWriter<Collection<Site>> multiScopeStoreWriter;

    public SiteSyncJob(
            MultiScopeStoreWriter<Collection<Site>> multiScopeStoreWriter, Collection<Site> globalSites,
            Collection<OperatorKey> globalOperators) {
        this.globalSites = globalSites;
        this.globalOperators = globalOperators;
        this.multiScopeStoreWriter = multiScopeStoreWriter;
    }

    @Override
    public String getId() {
        return "global-to-site-scope-sync-sites";
    }

    @Override
    public void execute() throws Exception {
        PrivateSiteDataMap<Site> desiredState = PrivateSiteUtil.getSites(globalSites, globalOperators);
        multiScopeStoreWriter.uploadIfChanged(desiredState, null);
    }
}
