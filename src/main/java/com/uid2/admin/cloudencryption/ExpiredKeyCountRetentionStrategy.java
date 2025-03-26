package com.uid2.admin.cloudencryption;

import com.uid2.admin.store.Clock;
import com.uid2.shared.model.CloudEncryptionKey;

import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

// Only keep keys past activation time - no future keys allowed
// Keep up to `expiredKeysToRetain` most recent keys
// Considers all keys passed, doesn't do grouping by site (handled upstream)
public class ExpiredKeyCountRetentionStrategy implements CloudKeyRetentionStrategy {
    private final Clock clock;
    private final int expiredKeysToRetain;

    public ExpiredKeyCountRetentionStrategy(Clock clock, int expiredKeysToRetain) {
        this.clock = clock;
        this.expiredKeysToRetain = expiredKeysToRetain;
    }

    @Override
    public Set<CloudEncryptionKey> selectKeysToRetain(Set<CloudEncryptionKey> keysForSite) {
        return keysForSite.stream()
                .filter(key -> key.getActivates() <= clock.getEpochSecond())
                .sorted(Comparator.comparingLong(CloudEncryptionKey::getActivates).reversed())
                .limit(expiredKeysToRetain)
                .collect(Collectors.toSet());
    }
}