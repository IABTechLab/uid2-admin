package com.uid2.admin.job.EncryptionJob;

import com.uid2.admin.job.model.Job;
import com.uid2.admin.store.MultiScopeStoreWriter;
import com.uid2.admin.util.PrivateSiteUtil;
import com.uid2.admin.util.PublicSiteUtil;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.store.RotatingSaltProvider;

import java.util.Collection;
import java.util.List;

public class SaltEncryptionJob extends Job {
    private final Collection<OperatorKey> globalOperators;
    private final Collection<RotatingSaltProvider.SaltSnapshot> saltEntries;
    private final MultiScopeStoreWriter<Collection<RotatingSaltProvider.SaltSnapshot>> multiScopeStoreWriter;

    public SaltEncryptionJob(Collection<OperatorKey> globalOperators,
                             Collection<RotatingSaltProvider.SaltSnapshot> saltEntries,
                             MultiScopeStoreWriter<Collection<RotatingSaltProvider.SaltSnapshot>> multiScopeStoreWriter) {
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
        List<Integer> desiredPrivateState = PrivateSiteUtil.getPrivateSaltSites(globalOperators);
        multiScopeStoreWriter.uploadPrivateWithEncryption(desiredPrivateState, saltEntries, null);
        List<Integer> desiredPublicState = PublicSiteUtil.getPublicSaltSites(globalOperators);
        multiScopeStoreWriter.uploadPublicWithEncryption(desiredPublicState, saltEntries, null);
    }
}
