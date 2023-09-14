package com.uid2.admin.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uid2.shared.auth.IAuthorizable;

public class RevealedKey<T extends IAuthorizable> {
    private final T authorizable;
    @JsonProperty("plaintext_key")
    private final String plaintextKey;

    public RevealedKey(T authorizable, String plaintextKey) {
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
