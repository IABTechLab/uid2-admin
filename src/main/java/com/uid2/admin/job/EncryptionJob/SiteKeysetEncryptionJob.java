package com.uid2.admin.job.EncryptionJob;

import com.uid2.admin.job.model.Job;
import com.uid2.admin.store.MultiScopeStoreWriter;
import com.uid2.admin.util.PrivateSiteUtil;
import com.uid2.admin.util.PublicSiteUtil;
import com.uid2.shared.auth.Keyset;
import com.uid2.shared.auth.OperatorKey;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class SiteKeysetEncryptionJob extends Job{

    private final Collection<OperatorKey> globalOperators;
    private final Map<Integer, Keyset> globalKeysets;
    private final MultiScopeStoreWriter<Map<Integer, Keyset>> multiScopeStoreWriter;

    public SiteKeysetEncryptionJob(
            MultiScopeStoreWriter<Map<Integer, Keyset>> multiScopeStoreWriter,
            Collection<OperatorKey> globalOperators,
            Map<Integer, Keyset> globalKeysets) {
        this.globalOperators = globalOperators;
        this.globalKeysets = globalKeysets;
        this.multiScopeStoreWriter = multiScopeStoreWriter;
    }

    @Override
    public String getId() {
        return "s3-encryption-sync-keysets";
    }

    @Override
    public void execute() throws Exception {
        HashMap<Integer, Map<Integer, Keyset>> desiredPrivateState = PrivateSiteUtil.getKeysetForEachSite(globalOperators, globalKeysets);
        multiScopeStoreWriter.uploadPrivateWithEncryption(desiredPrivateState, null);
        HashMap<Integer, Map<Integer, Keyset>> desiredPublicState = PublicSiteUtil.getPublicKeysets(globalKeysets,globalOperators);
        multiScopeStoreWriter.uploadPublicWithEncryption(desiredPublicState, null);
    }
}


