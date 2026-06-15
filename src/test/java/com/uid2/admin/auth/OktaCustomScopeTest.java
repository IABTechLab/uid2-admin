package com.uid2.admin.auth;

import com.uid2.shared.auth.Role;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

public class OktaCustomScopeTest {

    private static Stream<Arguments> testFromNameData() {
        return Stream.of(
            Arguments.of("uid2.admin.ss-portal", OktaCustomScope.SS_PORTAL),
            Arguments.of("uid2.admin.secret-rotation", OktaCustomScope.SECRET_ROTATION),
            Arguments.of("uid2.admin.site-sync", OktaCustomScope.SITE_SYNC),
            Arguments.of("uid2.admin.client-key-issuance", OktaCustomScope.CLIENT_KEY_ISSUANCE),
            Arguments.of("dummy", OktaCustomScope.INVALID)
        );
    }

    @ParameterizedTest
    @MethodSource("testFromNameData")
    public void testFromName(final String name, final OktaCustomScope scope) {
        Assertions.assertEquals(scope, OktaCustomScope.fromName(name));
    }

    @Test
    void euidSsPortalScopeIsRecognised() {
        OktaCustomScope scope = OktaCustomScope.fromName("euid.admin.ss-portal");
        Assertions.assertNotNull(scope);
        Assertions.assertEquals(Role.SHARING_PORTAL, scope.getRole());
    }
}
