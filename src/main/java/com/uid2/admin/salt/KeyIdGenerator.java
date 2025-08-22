package com.uid2.admin.salt;

import com.uid2.shared.model.SaltEntry;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public class KeyIdGenerator {
    private static final int MAX_KEY_ID = 16777215; // 3 bytes
    private final AtomicInteger lastActiveKeyId;

    public KeyIdGenerator(SaltEntry[] salts) {
        this.lastActiveKeyId = new AtomicInteger(getLastActiveKeyId(salts));
    }

    private int getLastActiveKeyId(SaltEntry[] salts) {
        long maxLastUpdated = Arrays.stream(salts).mapToLong(SaltEntry::lastUpdated).max().orElse(0);
        int[] lastActiveKeyIds = Arrays.stream(salts)
                .filter(s -> s.lastUpdated() == maxLastUpdated && s.currentKey() != null)
                .mapToInt(s -> s.currentKey().id())
                .sorted()
                .toArray();

        if (lastActiveKeyIds.length == 0) return MAX_KEY_ID; // so that next ID will start at 0

        int highestId = lastActiveKeyIds[lastActiveKeyIds.length - 1];

        if (highestId < MAX_KEY_ID) return highestId;

        // Wrapped case - find the last consecutive ID
        for (int i = 0; i < lastActiveKeyIds.length - 1; i++) {
            if (lastActiveKeyIds[i + 1] - lastActiveKeyIds[i] > 1) {
                return lastActiveKeyIds[i];
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