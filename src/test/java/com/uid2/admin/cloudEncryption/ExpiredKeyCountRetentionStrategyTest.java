package com.uid2.admin.cloudEncryption;

import com.uid2.admin.store.Clock;
import com.uid2.shared.model.CloudEncryptionKey;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExpiredKeyCountRetentionStrategyTest {
    private final long past1 = 100L;
    private final long past2 = 200L;
    private final long past3 = 300L;
    private final long now = 400L;
    private final long future = 500L;
    private ExpiredKeyCountRetentionStrategy strategy;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = mock(Clock.class);
        strategy = new ExpiredKeyCountRetentionStrategy(clock, 2);
        when(clock.getEpochSecond()).thenReturn(now);
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
    void selectKeysToRetain_withMoreThanNNonExpiredKeys() {
        var activeKey = new CloudEncryptionKey(1, 1, now, now, "secret 1");
        var futureKey1 = new CloudEncryptionKey(2, 1, future, now, "secret 2");
        var futureKey2 = new CloudEncryptionKey(3, 1, future, now, "secret 3");
        var futureKey3 = new CloudEncryptionKey(4, 1, future, now, "secret 4");
        var expiredKey1 = new CloudEncryptionKey(5, 1, past1, now, "secret 5");
        var originalKeys = Set.of(activeKey, futureKey1, futureKey2, futureKey3, expiredKey1);

        var actual = strategy.selectKeysToRetain(originalKeys);

        assertThat(actual).isEqualTo(originalKeys);
    }

    @Test
    void selectKeysToRetain_withMoreThanNExpiredKeys() {
        var oldestExpiredKey = new CloudEncryptionKey(1, 1, past1, past1, "secret 1"); // Don't retain
        var expiredKey2 = new CloudEncryptionKey(2, 1, past2, past1, "secret 2");
        var expiredKey3 = new CloudEncryptionKey(3, 1, past3, past1, "secret 3");
        var activeKey = new CloudEncryptionKey(3, 1, now, past1, "secret 4");
        var futureKey = new CloudEncryptionKey(3, 1, now, past1, "secret 5");
        var originalKeys = Set.of(oldestExpiredKey, expiredKey2, expiredKey3, activeKey, futureKey);

        var expected = Set.of(expiredKey2, expiredKey3, activeKey, futureKey);

        var actual = strategy.selectKeysToRetain(originalKeys);

        assertThat(actual).isEqualTo(expected);
    }
}