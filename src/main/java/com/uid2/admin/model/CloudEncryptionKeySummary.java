package com.uid2.admin.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uid2.shared.model.CloudEncryptionKey;

public record CloudEncryptionKeySummary(
    @JsonProperty int id,
    @JsonProperty int siteId,
    @JsonProperty long activates,
    @JsonProperty long created
) {
    public static CloudEncryptionKeySummary fromFullKey(CloudEncryptionKey key) {
        return new CloudEncryptionKeySummary(key.getId(), key.getSiteId(), key.getActivates(), key.getCreated());
    }
}
