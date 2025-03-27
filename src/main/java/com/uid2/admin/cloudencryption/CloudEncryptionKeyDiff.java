package com.uid2.admin.cloudencryption;

import com.uid2.shared.model.CloudEncryptionKey;

import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Set;

public record CloudEncryptionKeyDiff(
        int before,
        int after,
        int created,
        int removed,
        int unchanged
) {
   public static CloudEncryptionKeyDiff calculateDiff(Set<CloudEncryptionKey> keysBefore, Set<CloudEncryptionKey> keysAfter) {
        var before = keysBefore.size();
        var after = keysAfter.size();

        var intersection = new HashSet<>(keysBefore);
        intersection.retainAll(keysAfter);
        int unchanged = intersection.size();

        var onlyInLeft = new HashSet<>(keysBefore);
        onlyInLeft.removeAll(keysAfter);
        int removed = onlyInLeft.size();

        var onlyInRight = new HashSet<>(keysAfter);
        onlyInRight.removeAll(keysBefore);
        int created = onlyInRight.size();

       return new CloudEncryptionKeyDiff(before, after, created, removed, unchanged);
    }

    @Override
    public String toString() {
        return MessageFormat.format(
                "before={0}, after={1}, created={2}, removed={3}, unchanged={4}",
                before,
                after,
                created,
                removed,
                unchanged
        );
    }
}