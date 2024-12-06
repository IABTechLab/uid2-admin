package com.uid2.admin.job.EncryptionJob;

import com.uid2.admin.job.model.Job;
import com.uid2.admin.model.PrivateSiteDataMap;
import com.uid2.admin.store.MultiScopeStoreWriter;
import com.uid2.admin.util.PrivateSiteUtil;
import com.uid2.admin.util.PublicSiteUtil;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.model.SaltEntry;

import java.util.Collection;

public class SaltEncryptionJob extends Job {
    private final Collection<OperatorKey> globalOperators;
    private final Collection<SaltEntry> saltEntries;
    private final MultiScopeStoreWriter<Collection<SaltEntry>> multiScopeStoreWriter;

    public SaltEncryptionJob(Collection<OperatorKey> globalOperators,
                             Collection<SaltEntry> saltEntries,
                             MultiScopeStoreWriter<Collection<SaltEntry>> multiScopeStoreWriter) {
        this.globalOperators = globalOperators;
        this.saltEntries = saltEntries;
        this.multiScopeStoreWriter = multiScopeStoreWriter;
    }


    @Override
    public String getId() {
        return "cloud-encryption-sync-salts";
    }

    @Override
    public void execute() throws Exception {
        PrivateSiteDataMap<SaltEntry> desiredPrivateState = PrivateSiteUtil.getPrivateSaltEntries(saltEntries, globalOperators);
        multiScopeStoreWriter.uploadPrivateWithEncryption(desiredPrivateState, null);
        PrivateSiteDataMap<SaltEntry> desiredPublicState = PublicSiteUtil.getPublicSaltEntries(saltEntries, globalOperators);
        multiScopeStoreWriter.uploadPublicWithEncryption(desiredPublicState, null);
    }
}
