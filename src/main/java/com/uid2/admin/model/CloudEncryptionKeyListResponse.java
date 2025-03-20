package com.uid2.admin.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record CloudEncryptionKeyListResponse(
        @JsonProperty List<CloudEncryptionKeySummary> cloudEncryptionKeys
) {}

