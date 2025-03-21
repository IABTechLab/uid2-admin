package com.uid2.admin.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uid2.shared.model.CloudEncryptionKey;

import java.time.Instant;

public record CloudEncryptionKeySummary(
        @JsonProperty int id,
        @JsonProperty int siteId,
        @JsonProperty String activates,
        @JsonProperty String created
) {
    public static CloudEncryptionKeySummary fromFullKey(CloudEncryptionKey key) {
        return new CloudEncryptionKeySummary(
                key.getId(),
                key.getSiteId(),
                toIsoTimestamp(key.getActivates()),
                toIsoTimestamp(key.getCreated())
        );
    }

    private static String toIsoTimestamp(Long epochSeconds) {
        return Instant.ofEpochSecond(epochSeconds).toString();
    }
}
