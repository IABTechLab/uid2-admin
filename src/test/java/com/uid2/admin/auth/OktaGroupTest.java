package com.uid2.admin.auth;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

public class OktaGroupTest {

    private static Stream<Arguments> testFromNameData() {
        return Stream.of(
            Arguments.of("developer", OktaGroup.DEVELOPER),
            Arguments.of("developer-elevated", OktaGroup.DEVELOPER_ELEVATED),
            Arguments.of("infra-admin", OktaGroup.INFRA_ADMIN),
            Arguments.of("admin", OktaGroup.ADMIN),
            Arguments.of("dummy", OktaGroup.INVALID)
        );
    }

    @ParameterizedTest
    @MethodSource("testFromNameData")
    public void testFromName(final String name, final OktaGroup group) {
        Assertions.assertEquals(group, OktaGroup.fromName(name));
    }
}
