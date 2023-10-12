package com.uid2.admin.vertx.api.cstg;

import com.uid2.shared.model.ClientSideKeypair;
import lombok.AllArgsConstructor;

import java.time.Instant;

@AllArgsConstructor
public class ClientSideKeypairResponse {
    public final String name;
    public final int siteId;
    public final String subscriptionId;
    public final String publicKey;
    public final Instant created;
    public final boolean disabled;

    static ClientSideKeypairResponse fromClientSiteKeypair(ClientSideKeypair keypair) {
        return new ClientSideKeypairResponse(keypair.getName(), keypair.getSiteId(), keypair.getSubscriptionId(), keypair.encodePublicKeyToString(), keypair.getCreated(), keypair.isDisabled());
    }
}
