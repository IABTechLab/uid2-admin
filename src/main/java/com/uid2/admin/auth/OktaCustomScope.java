package com.uid2.admin.auth;

import com.uid2.shared.auth.Role;
import lombok.Getter;

import java.util.Arrays;

@Getter
public enum OktaCustomScope {
    SS_PORTAL("uid2.admin.ss-portal", Role.SHARING_PORTAL),
    SECRET_ROTATION("uid2.admin.secret-rotation", Role.SECRET_ROTATION),
    SITE_SYNC("uid2.admin.site-sync", Role.PRIVATE_OPERATOR_SYNC),
    INVALID("invalid", Role.UNKNOWN);
    private final String name;
    private final Role role;

    OktaCustomScope(final String name, final Role role) {
        this.name = name;
        this.role = role;
    }
    public static OktaCustomScope fromName(final String name) {
        return Arrays.stream(OktaCustomScope.values())
            .filter(scope -> scope.getName().equalsIgnoreCase(name.toLowerCase()))
            .findFirst()
            .orElse(INVALID);
    }
}
