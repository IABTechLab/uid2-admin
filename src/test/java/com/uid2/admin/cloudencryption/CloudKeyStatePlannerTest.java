package com.uid2.admin.cloudencryption;

import com.uid2.admin.store.Clock;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.model.CloudEncryptionKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CloudKeyStatePlannerTest {
    private CloudSecretGenerator secretGenerator;
    private Clock clock;
    private ExpiredKeyCountRetentionStrategy retentionStrategy;
    private CloudKeyStatePlanner planner;

    private final int siteId = 1;
    private final String secret1 = "secret 1";
    private final CloudEncryptionKey key1 = new CloudEncryptionKey(1, siteId, 0, 0, secret1);
    private final OperatorKey operatorKey1 = new OperatorKey("hash 1", "salt 1", "name 1", "contact 1", "protocol 1", 0, false, siteId, "one");

    @BeforeEach
    void setUp() {
        secretGenerator = mock(CloudSecretGenerator.class);
        when(secretGenerator.generate()).thenReturn(secret1);
        clock = mock(Clock.class);
        retentionStrategy = new ExpiredKeyCountRetentionStrategy(clock, 3);
        planner = new CloudKeyStatePlanner(secretGenerator, clock, retentionStrategy);
    }

    @Test
    void planBackfill_doesNotRemoveKeysWhenOperatorsForKeyMissing() {
        // We do cleanup in rotation, this job is supposed to be extremely safe and won't delete anything
        var existingCloudKeys = List.of(key1);
        var operatorKeys = List.<OperatorKey>of();
        var expected = Set.of(key1);

        var actual = planner.planBackfill(existingCloudKeys, operatorKeys);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void planBackfill_createsNoNewKeysForOperatorsWithExistingKeys() {
        var existingCloudKeys = List.of(key1);
        var operatorKeys = List.of(operatorKey1);
        var expected = Set.of(key1);

        var actual = planner.planBackfill(existingCloudKeys, operatorKeys);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void planBackfill_createsNewKeysForOperatorsWithNoKeys() {
        var existingCloudKeys = List.<CloudEncryptionKey>of();
        var operatorKeys = List.of(operatorKey1);
        var expected = Set.of(key1);

        var actual = planner.planBackfill(existingCloudKeys, operatorKeys);

        assertThat(actual).isEqualTo(expected);
    }
}