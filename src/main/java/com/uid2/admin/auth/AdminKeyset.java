package com.uid2.admin.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uid2.admin.model.ClientType;
import com.uid2.shared.auth.Keyset;

import java.util.Arrays;
import java.util.Set;

public class AdminKeyset extends Keyset {

    @JsonProperty("allowed_types")
    private final Set<ClientType> allowedTypes;

    public AdminKeyset(int keysetId, int siteId, String name, Set<Integer> allowedSites, long created,
                       boolean isEnabled, boolean isDefault, Set<ClientType> allowedTypes) {
        super(keysetId, siteId, name, allowedSites, created, isEnabled, isDefault);
        this.allowedTypes = allowedTypes;
    }

    public Set<ClientType> getAllowedTypes() {
        return allowedTypes;
    }

    @Override
    public boolean equals(Object o) {
        boolean result = super.equals(o);
        if(!result) return false;
        if (!(o instanceof AdminKeyset)) return false;
        AdminKeyset b = (AdminKeyset) o;
        return allowedTypes.equals(b.getAllowedTypes());
    }

    @Override
    public int hashCode() {
        return super.hashCode() + Arrays.hashCode(this.allowedTypes.toArray());
    }
}
