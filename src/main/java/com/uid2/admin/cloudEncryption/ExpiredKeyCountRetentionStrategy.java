package com.uid2.admin.cloudEncryption;

import com.uid2.admin.store.Clock;
import com.uid2.shared.model.CloudEncryptionKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Keep up to `expiredKeysToRetain` most recent expired keys
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
        var activeKey = findActiveKey(keysForSite);
        if (activeKey == null) {
            return keysForSite;
        }

        var expiredKeys = new ArrayList<CloudEncryptionKey>();
        var nonExpiredKeys = new ArrayList<CloudEncryptionKey>();
        for (var key : keysForSite) {
            if (key.getActivates() < activeKey.getActivates()) {
                expiredKeys.add(key);
            } else {
                nonExpiredKeys.add(key);
            }
        }

        var retainedExpiredKeys = pickRetainedExpiredKeys(expiredKeys);
        return Stream.concat(retainedExpiredKeys, nonExpiredKeys.stream()).collect(Collectors.toSet());

    }

    private Stream<CloudEncryptionKey> pickRetainedExpiredKeys(ArrayList<CloudEncryptionKey> expiredKeys) {
        return expiredKeys
                .stream()
                .sorted(Comparator.comparingLong(CloudEncryptionKey::getActivates).reversed())
                .limit(expiredKeysToRetain);
    }

    private CloudEncryptionKey findActiveKey(Set<CloudEncryptionKey> keys) {
        var keysPastActivation = keys.stream().filter(key -> key.getActivates() <= clock.getEpochSecond());
        return keysPastActivation
                .max(Comparator.comparingLong(CloudEncryptionKey::getActivates))
                .orElse(null);
    }
}