package com.uid2.admin.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.uid2.shared.auth.IRoleAuthorizable;
import com.uid2.shared.auth.Role;

import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public class AdminUser implements IRoleAuthorizable<Role> {
    private final String key;
    @JsonProperty("key_hash")
    private final String keyHash;
    @JsonProperty("key_salt")
    private final String keySalt;
    private final String name;
    private final String contact;
    private final long created; // epochSeconds
    private Set<Role> roles;
    private boolean disabled;

    @JsonCreator
    public AdminUser(
            @JsonProperty("key") String key,
            @JsonProperty("key_hash") String keyHash,
            @JsonProperty("key_salt") String keySalt,
            @JsonProperty("name") String name,
            @JsonProperty("contact") String contact,
            @JsonProperty("created") long created,
            @JsonProperty("roles") Set<Role> roles,
            @JsonProperty("disabled") boolean disabled) {
        this.key = key;
        this.keyHash = keyHash;
        this.keySalt = keySalt;
        this.name = name;
        this.contact = contact;
        this.created = created;
        this.roles = roles;
        this.disabled = disabled;
    }

    public static AdminUser unknown(String unknown) {
        return new AdminUser(unknown, unknown, unknown, unknown, unknown,
                Instant.now().getEpochSecond(), Collections.emptySet(), false);
    }

    public String getKey() {
        return key;
    }

    @Override
    public String getKeyHash() {
        return keyHash;
    }

    @Override
    public String getKeySalt() {
        return keySalt;
    }

    public String getName() {
        return name;
    }

    @Override
    public String getContact() {
        return contact;
    }

    public long getCreated() {
        return created;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> newRoles) {
        this.roles = newRoles;
    }

    @Override
    public boolean hasRole(Role role) {
        return this.roles.contains(role);
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    @Override
    public Integer getSiteId() {
        return null;
    }

    @Override
    public boolean equals(Object o) {
        // If the object is compared with itself then return true
        if (o == this) return true;

        // If the object is of a different type, return false
        if (!(o instanceof AdminUser)) return false;

        AdminUser b = (AdminUser) o;

        // Compare the data members and return accordingly, intentionally don't compare keys
        return this.key.equals(b.key)
                && this.keyHash.equals(b.keyHash)
                && this.keySalt.equals(b.keySalt)
                && this.name.equals(b.name)
                && this.contact.equals(b.contact)
                && this.created == b.created
                && this.roles.equals(b.roles)
                && this.disabled == b.disabled;
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, keyHash, keySalt, name, contact, created, roles, disabled);
    }
}
