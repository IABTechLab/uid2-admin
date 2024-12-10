package com.uid2.admin.job.EncryptionJob;

import com.uid2.admin.job.model.Job;
import com.uid2.admin.model.PrivateSiteDataMap;
import com.uid2.admin.store.MultiScopeStoreWriter;
import com.uid2.admin.util.PublicSiteUtil;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.model.ClientSideKeypair;

import java.util.Collection;

public class ClientSideKeypairEncryptionJob extends Job {
    private final Collection<OperatorKey> globalOperators;
    private final Collection<ClientSideKeypair> globalClientSideKeypairs;

    private final MultiScopeStoreWriter<Collection<ClientSideKeypair>> multiScopeStoreWriter;

    public ClientSideKeypairEncryptionJob(Collection<OperatorKey> globalOperators, Collection<ClientSideKeypair> globalClientSideKeypairs,
                                          MultiScopeStoreWriter<Collection<ClientSideKeypair>> multiScopeStoreWriter) {
        this.globalOperators = globalOperators;
        this.globalClientSideKeypairs = globalClientSideKeypairs;
        this.multiScopeStoreWriter = multiScopeStoreWriter;
    }

    @Override
    public String getId() {
        return "cloud-encryption-sync-clientside-keypair";
    }

    @Override
    public void execute() throws Exception {
        // Only public operators support clientside keypair
        PrivateSiteDataMap<ClientSideKeypair> desiredPublicState = PublicSiteUtil.getPublicClientKeypairs(globalClientSideKeypairs, globalOperators);
        multiScopeStoreWriter.uploadPublicWithEncryption(desiredPublicState, null);
    }
}
