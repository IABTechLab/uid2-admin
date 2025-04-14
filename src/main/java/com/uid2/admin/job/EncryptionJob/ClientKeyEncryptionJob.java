package com.uid2.admin.job.EncryptionJob;

import com.uid2.admin.job.model.Job;
import com.uid2.admin.legacy.LegacyClientKey;
import com.uid2.admin.model.PrivateSiteDataMap;
import com.uid2.admin.store.MultiScopeStoreWriter;
import com.uid2.admin.store.writer.KeysetKeyStoreWriter;
import com.uid2.admin.util.PrivateSiteUtil;
import com.uid2.admin.util.PublicSiteUtil;
import com.uid2.shared.auth.OperatorKey;
import io.vertx.core.json.JsonObject;

import java.util.Collection;

public class ClientKeyEncryptionJob extends Job {
    private final Collection<OperatorKey> globalOperators;
    private final Collection<LegacyClientKey> globalClientKeys;
    private final MultiScopeStoreWriter<Collection<LegacyClientKey>> multiScopeStoreWriter;
    private final Long version;

    public ClientKeyEncryptionJob(
            MultiScopeStoreWriter<Collection<LegacyClientKey>> multiScopeStoreWriter,
            Collection<LegacyClientKey> globalClientKeys,
            Collection<OperatorKey> globalOperators,
            Long version) {
        this.globalClientKeys = globalClientKeys;
        this.globalOperators = globalOperators;
        this.multiScopeStoreWriter = multiScopeStoreWriter;
        this.version = version;
    }

    @Override
    public String getId() {
        return "cloud-encryption-sync-clientKeys";
    }

    @Override
    public void execute() throws Exception {
        JsonObject extraMeta = new JsonObject();
        extraMeta.put("version", this.version);
        PrivateSiteDataMap<LegacyClientKey> desiredPrivateState = PrivateSiteUtil.getClientKeys(globalOperators, globalClientKeys);
        multiScopeStoreWriter.uploadPrivateWithEncryption(desiredPrivateState, extraMeta);
        PrivateSiteDataMap<LegacyClientKey> desiredPublicState = PublicSiteUtil.getPublicClients(globalClientKeys, globalOperators);
        multiScopeStoreWriter.uploadPublicWithEncryption(desiredPublicState, extraMeta);
    }
}
