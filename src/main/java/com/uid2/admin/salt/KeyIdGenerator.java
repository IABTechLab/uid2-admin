package com.uid2.admin.salt;

import com.uid2.shared.model.SaltEntry;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *  Assumptions:
 *  - The latest assigned key ids are from the latest updated buckets
 *  - Key ids from these buckets will always be monotonically increasing (apart from wraparound) as they have not rotated again after last assignment
 *
 * Intended outcomes of KeyIdGenerator:
 * - Key ids are always monotonically increasing, starting from 0
 * - When the last allocated key id reaches 16777215, the next key id will wrap around to 0
 * - Continuing to increment from the highest key id will result in monotonic incrementation of key ids for all newly rotated buckets
 **/
public class KeyIdGenerator {
    private static final int MAX_KEY_ID = 16777215; // 3 bytes
    private final AtomicInteger nextActiveKeyId;

    public KeyIdGenerator(SaltEntry[] buckets) {
        this.nextActiveKeyId = new AtomicInteger(getNextActiveKeyId(buckets));
    }

    private static int getNextActiveKeyId(SaltEntry[] buckets) {
        long lastUpdatedTimestampWithKey = Arrays.stream(buckets).filter(s -> s.currentKey() != null).mapToLong(SaltEntry::lastUpdated).max().orElse(0);
        if (lastUpdatedTimestampWithKey == 0) return 0;

        int[] lastActiveKeyIdsSorted = Arrays.stream(buckets)
                .filter(s -> s.lastUpdated() == lastUpdatedTimestampWithKey && s.currentKey() != null)
                .mapToInt(s -> s.currentKey().id())
                .sorted()
                .toArray();

        int highestId = lastActiveKeyIdsSorted[lastActiveKeyIdsSorted.length - 1];

        int nextKeyId = highestId + 1;
        if (nextKeyId <= MAX_KEY_ID) return nextKeyId;

        // Wrapped case - find the last consecutive ID from 0
        for (int i = 0; i < lastActiveKeyIdsSorted.length - 1; i++) {
            if (lastActiveKeyIdsSorted[i + 1] - lastActiveKeyIdsSorted[i] > 1) {
                return lastActiveKeyIdsSorted[i] + 1;
            }
        }

        return 0;
    }

    public int getNextKeyId() {
        return nextActiveKeyId.getAndUpdate(id ->
                id + 1 > MAX_KEY_ID ? 0 : id + 1
        );
    }
}