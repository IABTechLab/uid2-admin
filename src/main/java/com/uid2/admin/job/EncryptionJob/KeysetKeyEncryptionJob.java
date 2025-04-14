package com.uid2.admin.job.EncryptionJob;

import com.uid2.admin.job.model.Job;
import com.uid2.admin.model.PrivateSiteDataMap;
import com.uid2.admin.store.MultiScopeStoreWriter;
import com.uid2.admin.store.writer.KeysetKeyStoreWriter;
import com.uid2.admin.util.PrivateSiteUtil;
import com.uid2.admin.util.PublicSiteUtil;
import com.uid2.shared.auth.Keyset;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.model.KeysetKey;
import io.vertx.core.json.JsonObject;

import java.util.Collection;
import java.util.Map;

public class KeysetKeyEncryptionJob extends Job {
    private final Collection<OperatorKey> globalOperators;
    private final Collection<KeysetKey> globalKeysetKeys;
    private final Map<Integer, Keyset> globalKeysets;
    private final Integer globalMaxKeyId;
    private final Long version;

    private final MultiScopeStoreWriter<Collection<KeysetKey>> multiScopeStoreWriter;

    public KeysetKeyEncryptionJob(Collection<OperatorKey> globalOperators,
                            Collection<KeysetKey> globalKeysetKeys,
                            Map<Integer, Keyset> globalKeysets,
                            Integer globalMaxKeyId,
                            MultiScopeStoreWriter<Collection<KeysetKey>> multiScopeStoreWriter, Long version) {
        this.globalOperators = globalOperators;
        this.globalKeysetKeys = globalKeysetKeys;
        this.globalKeysets = globalKeysets;
        this.globalMaxKeyId = globalMaxKeyId;
        this.multiScopeStoreWriter = multiScopeStoreWriter;
        this.version = version;
    }

    @Override
    public String getId() {
        return "cloud-encryption-sync-keysetKeys";
    }

    @Override
    public void execute() throws Exception {
        PrivateSiteDataMap<KeysetKey> desiredPrivateState = PrivateSiteUtil.getKeysetKeys(globalOperators, globalKeysetKeys, globalKeysets);
        JsonObject extraMeta = KeysetKeyStoreWriter.maxKeyMeta(globalMaxKeyId);
        extraMeta.put("version", this.version);
        multiScopeStoreWriter.uploadPrivateWithEncryption(desiredPrivateState, extraMeta);
        PrivateSiteDataMap<KeysetKey> desiredPublicState = PublicSiteUtil.getPublicKeysetKeys(globalKeysetKeys, globalOperators);
        multiScopeStoreWriter.uploadPublicWithEncryption(desiredPublicState, extraMeta);
    }
}
