package com.uid2.admin.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

public record CloudEncryptionKeyListResponse(
        @JsonProperty Set<CloudEncryptionKeySummary> cloudEncryptionKeys
) {}

