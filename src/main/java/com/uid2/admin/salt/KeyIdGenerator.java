package com.uid2.admin.salt;

import com.uid2.shared.model.SaltEntry;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Intended outcomes of KeyIdGenerator:
 * - Key ids are always consecutive, starting from 0
 * - When the last allocated key id reaches 16777215, the next key id will wrap around to 0
 * - Continuing to increment from the highest key id will result in monotonic incrementation of key ids for all newly rotated buckets
 *
 * Assumptions:
 * - The latest assigned key ids are from the latest updated buckets
 * - Key ids from these buckets will always be consecutive (apart from wraparound) as they have not rotated again after last assignment
 */
public class KeyIdGenerator {
    private static final int MAX_KEY_ID = 16777215; // 3 bytes
    private final AtomicInteger lastActiveKeyId;

    public KeyIdGenerator(SaltEntry[] buckets) {
        this.lastActiveKeyId = new AtomicInteger(getLastActiveKeyId(buckets));
    }

    private static int getLastActiveKeyId(SaltEntry[] buckets) {
        long maxLastUpdated = Arrays.stream(buckets).mapToLong(SaltEntry::lastUpdated).max().orElse(0);
        int[] lastActiveKeyIdsSorted = Arrays.stream(buckets)
                .filter(s -> s.lastUpdated() == maxLastUpdated && s.currentKey() != null)
                .mapToInt(s -> s.currentKey().id())
                .sorted()
                .toArray();

        if (lastActiveKeyIdsSorted.length == 0) return MAX_KEY_ID; // so that next ID will start at 0

        int highestId = lastActiveKeyIdsSorted[lastActiveKeyIdsSorted.length - 1];

        if (highestId < MAX_KEY_ID) return highestId;

        // Wrapped case - find the last consecutive ID from 0
        for (int i = 0; i < lastActiveKeyIdsSorted.length - 1; i++) {
            if (lastActiveKeyIdsSorted[i + 1] - lastActiveKeyIdsSorted[i] > 1) {
                return lastActiveKeyIdsSorted[i];
            }
        }

        return highestId;
    }

    public int getNextKeyId() {
        return lastActiveKeyId.updateAndGet(id ->
                id + 1 > MAX_KEY_ID ? 0 : id + 1
        );
    }
}