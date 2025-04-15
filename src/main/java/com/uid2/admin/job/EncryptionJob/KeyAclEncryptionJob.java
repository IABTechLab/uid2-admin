package com.uid2.admin.job.EncryptionJob;

import com.uid2.admin.job.model.EncryptedJob;
import com.uid2.admin.job.model.Job;
import com.uid2.admin.store.MultiScopeStoreWriter;
import com.uid2.admin.util.PrivateSiteUtil;
import com.uid2.admin.util.PublicSiteUtil;
import com.uid2.shared.auth.EncryptionKeyAcl;
import com.uid2.shared.auth.OperatorKey;
import io.vertx.core.json.JsonObject;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class KeyAclEncryptionJob extends EncryptedJob {
    private final Collection<OperatorKey> globalOperators;
    private final Map<Integer, EncryptionKeyAcl> globalAcls;
    private final MultiScopeStoreWriter<Map<Integer, EncryptionKeyAcl>> multiScopeStoreWriter;

    public KeyAclEncryptionJob(
            MultiScopeStoreWriter<Map<Integer, EncryptionKeyAcl>> multiScopeStoreWriter,
            Collection<OperatorKey> globalOperators,
            Map<Integer, EncryptionKeyAcl> globalAcls, Long version) {
        super(version);
        this.globalOperators = globalOperators;
        this.globalAcls = globalAcls;
        this.multiScopeStoreWriter = multiScopeStoreWriter;
    }

    @Override
    public String getId() {
        return "cloud-encryption-sync-keyAcls";
    }

    @Override
    public void execute() throws Exception {
        HashMap<Integer, Map<Integer, EncryptionKeyAcl>> desiredPrivateState = PrivateSiteUtil.getEncryptionKeyAclsForEachSite(globalOperators, globalAcls);
        multiScopeStoreWriter.uploadPrivateWithEncryption(desiredPrivateState, this.getBaseMetadata());
        HashMap<Integer, Map<Integer, EncryptionKeyAcl>> desiredPublicState = PublicSiteUtil.getPublicKeyAcls(globalAcls,globalOperators);
        multiScopeStoreWriter.uploadPublicWithEncryption(desiredPublicState, this.getBaseMetadata());
    }
}
