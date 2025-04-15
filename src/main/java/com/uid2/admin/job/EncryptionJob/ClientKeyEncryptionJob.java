package com.uid2.admin.job.EncryptionJob;

import com.uid2.admin.job.model.EncryptedJob;
import com.uid2.admin.legacy.LegacyClientKey;
import com.uid2.admin.model.PrivateSiteDataMap;
import com.uid2.admin.store.MultiScopeStoreWriter;
import com.uid2.admin.util.PrivateSiteUtil;
import com.uid2.admin.util.PublicSiteUtil;
import com.uid2.shared.auth.OperatorKey;

import java.util.Collection;

public class ClientKeyEncryptionJob extends EncryptedJob {
    private final Collection<OperatorKey> globalOperators;
    private final Collection<LegacyClientKey> globalClientKeys;
    private final MultiScopeStoreWriter<Collection<LegacyClientKey>> multiScopeStoreWriter;

    public ClientKeyEncryptionJob(
            MultiScopeStoreWriter<Collection<LegacyClientKey>> multiScopeStoreWriter,
            Collection<LegacyClientKey> globalClientKeys,
            Collection<OperatorKey> globalOperators,
            Long version) {
        super(version);
        this.globalClientKeys = globalClientKeys;
        this.globalOperators = globalOperators;
        this.multiScopeStoreWriter = multiScopeStoreWriter;
    }

    @Override
    public String getId() {
        return "cloud-encryption-sync-clientKeys";
    }

    @Override
    public void execute() throws Exception {
        PrivateSiteDataMap<LegacyClientKey> desiredPrivateState = PrivateSiteUtil.getClientKeys(globalOperators, globalClientKeys);
        multiScopeStoreWriter.uploadPrivateWithEncryption(desiredPrivateState, this.getBaseMetadata());
        PrivateSiteDataMap<LegacyClientKey> desiredPublicState = PublicSiteUtil.getPublicClients(globalClientKeys, globalOperators);
        multiScopeStoreWriter.uploadPublicWithEncryption(desiredPublicState, this.getBaseMetadata());
    }
}
