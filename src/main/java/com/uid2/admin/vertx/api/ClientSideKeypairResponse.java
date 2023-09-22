package com.uid2.admin.vertx.api;

import com.uid2.shared.model.ClientSideKeypair;
import lombok.AllArgsConstructor;

import java.time.Instant;

@AllArgsConstructor
public class ClientSideKeypairResponse {
    public int siteId;
    public String subscriptionId;
    public String publicKey;
    public Instant created;
    public boolean disabled;

    static ClientSideKeypairResponse fromClientSiteKeypair(ClientSideKeypair keypair) {
        return new ClientSideKeypairResponse(keypair.getSiteId(), keypair.getSubscriptionId(), keypair.encodePublicKeyToString(), keypair.getCreated(), keypair.isDisabled());
    }
}
