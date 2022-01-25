// Copyright (c) 2021 The Trade Desk, Inc
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice,
//    this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice,
//    this list of conditions and the following disclaimer in the documentation
//    and/or other materials provided with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
// POSSIBILITY OF SUCH DAMAGE.

package com.uid2.admin.auth;

import com.uid2.shared.auth.ClientKey;
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

    public static AdminUser unknown(String unkownUser) {
        return new AdminUser(unkownUser, unkownUser, unkownUser,
                Instant.now().getEpochSecond(), Collections.<Role>emptySet(), false);
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

    public void setKey(String newKey) {
        this.key = newKey;
    }
}
