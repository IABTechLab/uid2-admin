package com.uid2.admin.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uid2.admin.model.ClientType;
import com.uid2.shared.auth.Keyset;
import org.checkerframework.checker.units.qual.K;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class AdminKeyset {

    @JsonProperty("allowed_types")
    private final Set<ClientType> allowedTypes;

    private final Keyset keyset;

    public AdminKeyset(int keysetId, int siteId, String name, Set<Integer> allowedSites, long created,
                       boolean isEnabled, boolean isDefault, Set<ClientType> allowedTypes) {
        keyset = new Keyset(keysetId, siteId, name, allowedSites, created, isEnabled, isDefault);
        this.allowedTypes = allowedTypes;
    }

    public AdminKeyset(Keyset keyset) {
       this.keyset = keyset;
       allowedTypes = new HashSet<>();
    }

    public Set<ClientType> getAllowedTypes() {
        return allowedTypes;
    }
    public Keyset getKeyset() { return  keyset; };

    // Keyset Functions
    public int getKeysetId() {
        return keyset.getKeysetId();
    }

    public int getSiteId() {
        return keyset.getSiteId();
    }

    public String getName() {
        return keyset.getName();
    }

    public Set<Integer> getAllowedSites() {
        return keyset.getAllowedSites();
    }

    public long getCreated() {
        return keyset.getCreated();
    }

    public boolean isEnabled() {
        return keyset.isEnabled();
    }

    public boolean isDefault() {
        return keyset.isDefault();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AdminKeyset)) return false;
        AdminKeyset b = (AdminKeyset) o;
        boolean result = keyset.equals(b.keyset);
        if(!result) return false;
        return allowedTypes.equals(b.getAllowedTypes());
    }

    @Override
    public int hashCode() {
        return keyset.hashCode() + Arrays.hashCode(this.allowedTypes.toArray());
    }
}
