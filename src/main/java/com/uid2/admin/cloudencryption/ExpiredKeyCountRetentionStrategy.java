package com.uid2.admin.cloudencryption;

import com.uid2.shared.model.CloudEncryptionKey;

import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

// Keep up to `expiredKeysToRetain` most recent keys
// Considers all keys passed, doesn't do grouping by site (handled upstream)
public class ExpiredKeyCountRetentionStrategy implements CloudKeyRetentionStrategy {
    private final int expiredKeysToRetain;

    public ExpiredKeyCountRetentionStrategy(int expiredKeysToRetain) {
        this.expiredKeysToRetain = expiredKeysToRetain;
    }

    @Override
    public Set<CloudEncryptionKey> selectKeysToRetain(Set<CloudEncryptionKey> keysForSite) {
        return keysForSite.stream()
                .sorted(Comparator.comparingLong(CloudEncryptionKey::getActivates).reversed())
                .limit(expiredKeysToRetain)
                .collect(Collectors.toSet());
    }
}