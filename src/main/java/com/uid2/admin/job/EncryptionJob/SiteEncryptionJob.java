package com.uid2.admin.job.EncryptionJob;

import com.uid2.admin.job.model.Job;
import com.uid2.admin.model.PrivateSiteDataMap;
import com.uid2.admin.store.MultiScopeStoreWriter;
import com.uid2.admin.util.PrivateSiteUtil;
import com.uid2.admin.util.PublicSiteUtil;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.model.Site;
import io.vertx.core.json.JsonObject;

import java.util.Collection;

public class SiteEncryptionJob extends Job {
    private final Collection<OperatorKey> globalOperators;
    private final Collection<Site> globalSites;
    private final MultiScopeStoreWriter<Collection<Site>> multiScopeStoreWriter;
    private final Long version;

    public SiteEncryptionJob(
            MultiScopeStoreWriter<Collection<Site>> multiScopeStoreWriter, Collection<Site> globalSites,
            Collection<OperatorKey> globalOperators, Long version) {
        this.globalSites = globalSites;
        this.globalOperators = globalOperators;
        this.multiScopeStoreWriter = multiScopeStoreWriter;
        this.version = version;
    }

    @Override
    public String getId() {
        return "cloud-encryption-sync-sites";
    }

    @Override
    public void execute() throws Exception {
        PrivateSiteDataMap<Site> desiredPrivateState = PrivateSiteUtil.getSites(globalSites, globalOperators);
        JsonObject extraMeta = new JsonObject();
        extraMeta.put("version", this.version);
        multiScopeStoreWriter.uploadPrivateWithEncryption(desiredPrivateState, extraMeta);
        PrivateSiteDataMap<Site> desiredPublicState = PublicSiteUtil.getPublicSites(globalSites, globalOperators);
        multiScopeStoreWriter.uploadPublicWithEncryption(desiredPublicState, extraMeta);
    }
}
