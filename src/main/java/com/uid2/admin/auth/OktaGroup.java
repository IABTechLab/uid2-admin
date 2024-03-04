package com.uid2.admin.auth;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum OktaGroup {
    DEVELOPER("developer"),
    DEVELOPER_ELEVATED("developer-elevated"),
    INFRA_ADMIN("infra-admin"),
    ADMIN("admin"),

    INVALID("invalid");

    private final String name;

    OktaGroup(final String name) {
        this.name = name;
    }

    public static OktaGroup fromName(final String name) {
        return Arrays.stream(OktaGroup.values())
            .filter(oktaGroup -> oktaGroup.getName().equalsIgnoreCase(name.toLowerCase()))
            .findFirst()
            .orElse(INVALID);
    }
}
