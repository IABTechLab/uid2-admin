package com.uid2.admin.cloudencryption;

import com.uid2.shared.model.CloudEncryptionKey;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class ExpiredKeyCountRetentionStrategyTest {
    private final long past1 = 100L;
    private final long past2 = 200L;
    private final long now = 300L;
    private ExpiredKeyCountRetentionStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new ExpiredKeyCountRetentionStrategy(2);
    }

    @Test
    void selectKeysToRetain_withNoKeys() {
        var originalKeys = new HashSet<CloudEncryptionKey>();

        var actual = strategy.selectKeysToRetain(originalKeys);

        assertThat(actual).isEqualTo(originalKeys);
    }

    @Test
    void selectKeysToRetain_withLessThanNKeys() {
        var originalKeys = Set.of(
                new CloudEncryptionKey(1, 1, past1, past1, "secret 1")
        );

        var actual = strategy.selectKeysToRetain(originalKeys);

        AssertionsForClassTypes.assertThat(actual).isEqualTo(originalKeys);
    }

    @Test
    void selectKeysToRetain_keepsNMostRecentNonFutureKeys() {
        var oldestExpiredKey = new CloudEncryptionKey(1, 1, past1, past1, "secret 1"); // Don't retain
        var expiredKey2 = new CloudEncryptionKey(2, 1, past2, past1, "secret 2");
        var activeKey = new CloudEncryptionKey(3, 1, now, past1, "secret 3");
        var originalKeys = Set.of(oldestExpiredKey, expiredKey2, activeKey);

        var expected = Set.of(expiredKey2, activeKey);

        var actual = strategy.selectKeysToRetain(originalKeys);

        assertThat(actual).isEqualTo(expected);
    }
}