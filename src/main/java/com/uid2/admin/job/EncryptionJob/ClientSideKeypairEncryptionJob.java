package com.uid2.admin.job.EncryptionJob;

import com.uid2.admin.job.model.Job;
import com.uid2.admin.model.PrivateSiteDataMap;
import com.uid2.admin.store.MultiScopeStoreWriter;
import com.uid2.admin.store.writer.KeysetKeyStoreWriter;
import com.uid2.admin.util.PublicSiteUtil;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.model.ClientSideKeypair;
import io.vertx.core.json.JsonObject;

import java.util.Collection;

public class ClientSideKeypairEncryptionJob extends Job {
    private final Collection<OperatorKey> globalOperators;
    private final Collection<ClientSideKeypair> globalClientSideKeypairs;

    private final MultiScopeStoreWriter<Collection<ClientSideKeypair>> multiScopeStoreWriter;
    private final Long version;
    public ClientSideKeypairEncryptionJob(Collection<OperatorKey> globalOperators, Collection<ClientSideKeypair> globalClientSideKeypairs,
                                          MultiScopeStoreWriter<Collection<ClientSideKeypair>> multiScopeStoreWriter, Long version) {
        this.globalOperators = globalOperators;
        this.globalClientSideKeypairs = globalClientSideKeypairs;
        this.multiScopeStoreWriter = multiScopeStoreWriter;
        this.version = version;
    }

    @Override
    public String getId() {
        return "cloud-encryption-sync-clientside-keypair";
    }

    @Override
    public void execute() throws Exception {
        // Only public operators support clientside keypair
        JsonObject extraMeta = new JsonObject();
        extraMeta.put("version", this.version);
        PrivateSiteDataMap<ClientSideKeypair> desiredPublicState = PublicSiteUtil.getPublicClientKeypairs(globalClientSideKeypairs, globalOperators);
        multiScopeStoreWriter.uploadPublicWithEncryption(desiredPublicState, extraMeta);
    }
}
