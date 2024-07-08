package com.uid2.admin.job.EncryptionJob;

import com.uid2.admin.job.model.Job;
import com.uid2.admin.model.PrivateSiteDataMap;
import com.uid2.admin.store.MultiScopeStoreWriter;
import com.uid2.admin.util.PrivateSiteUtil;
import com.uid2.admin.util.PublicSiteUtil;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.model.Site;

import java.util.Collection;

public class SiteEncryptionJob extends Job {
    private final Collection<OperatorKey> globalOperators;
    private final Collection<Site> globalSites;
    private final MultiScopeStoreWriter<Collection<Site>> multiScopeStoreWriter;

    public SiteEncryptionJob(
            MultiScopeStoreWriter<Collection<Site>> multiScopeStoreWriter, Collection<Site> globalSites,
            Collection<OperatorKey> globalOperators) {
        this.globalSites = globalSites;
        this.globalOperators = globalOperators;
        this.multiScopeStoreWriter = multiScopeStoreWriter;
    }

    @Override
    public String getId() {
        return "s3-encryption-sync-sites";
    }

    @Override
    public void execute() throws Exception {
        PrivateSiteDataMap<Site> desiredPrivateState = PrivateSiteUtil.getSites(globalSites, globalOperators);
        multiScopeStoreWriter.uploadEncrypted(desiredPrivateState, null);
        PrivateSiteDataMap<Site> desiredPublicState = PublicSiteUtil.getPublicSites(globalSites, globalOperators);
        multiScopeStoreWriter.uploadPublicWithEncryption(desiredPublicState, null);
    }
}
