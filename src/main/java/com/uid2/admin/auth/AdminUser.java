package com.uid2.admin.auth;

import com.uid2.shared.auth.IRoleAuthorizable;
import com.uid2.shared.auth.Role;
import com.uid2.shared.auth.Roles;
import io.vertx.core.json.JsonObject;

import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public class AdminUser implements IRoleAuthorizable<Role> {
    private String key;
    private final String name;
    private final String contact;
    // epochSeconds
    private final long created;
    private Set<Role> roles;
    private boolean disabled;

    public static AdminUser unknown(String unknown) {
        return new AdminUser(unknown, unknown, unknown,
                Instant.now().getEpochSecond(), Collections.emptySet(), false);
    }

    public AdminUser(String key, String name, String contact, long created, Set<Role> roles, boolean disabled) {
        this.key = key;
        this.name = name;
        this.contact = contact;
        this.created = created;
        this.roles = roles;
        this.disabled = disabled;
    }

    public String getName() {
        return name;
    }

    public long getCreated() {
        return created;
    }

    @Override
    public boolean hasRole(Role role) {
        return this.roles.contains(role);
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public String getContact() {
        return contact;
    }

    @Override
    public Integer getSiteId() {
        return null;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public static AdminUser valueOf(JsonObject json) {
        return new AdminUser(
                json.getString("key"),
                json.getString("name"),
                json.getString("contact"),
                json.getLong("created"),
                Roles.getRoles(Role.class, json),
                json.getBoolean("disabled", false)
        );
    }

    @Override
    public boolean equals(Object o) {
        // If the object is compared with itself then return true
        if (o == this) return true;

        // If the object is of a different type, return false
        if (!(o instanceof AdminUser)) return false;

        AdminUser b = (AdminUser) o;

        // Compare the data members and return accordingly, intentionally don't compare keys
        return this.name.equals(b.name)
                && this.contact.equals(b.contact)
                && this.roles.equals(b.roles)
                && this.created == b.created;
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, name, contact, created);
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> newRoles) {
        this.roles = newRoles;
    }
}
