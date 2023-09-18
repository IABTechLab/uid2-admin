package com.uid2.admin.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.uid2.shared.auth.IAuthorizable;

public class RevealedKey<T extends IAuthorizable> {
    private final T authorizable;
    @JsonProperty("plaintext_key")
    private final String plaintextKey;

    @JsonCreator
    public RevealedKey(
            @JsonProperty("authorizable") T authorizable,
            @JsonProperty("plaintext_key") String plaintextKey) {
        this.authorizable = authorizable;
        this.plaintextKey = plaintextKey;
    }

    public T getAuthorizable() {
        return authorizable;
    }

    public String getPlaintextKey() {
        return plaintextKey;
    }
}
