package com.uid2.admin.salt;

import com.uid2.shared.model.SaltEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Comparator;

public class KeyIdGenerator {
    private static final int MAX_KEY_ID = 16777215; // 3 bytes
    private int lastActiveKeyId;

    public KeyIdGenerator(SaltEntry[] salts) {
        this.lastActiveKeyId = getLastActiveKeyId(salts);
    }

    private int getLastActiveKeyId(SaltEntry[] salts) {
        SaltEntry[] sortedSaltsByLastUpdated = Arrays.stream(salts).sorted(Comparator.comparingLong(SaltEntry::lastUpdated)).toArray(SaltEntry[]::new);
        var lastUpdated = sortedSaltsByLastUpdated[sortedSaltsByLastUpdated.length - 1].lastUpdated();

        var sortedKeyIds = Arrays.stream(sortedSaltsByLastUpdated)
                .filter(s -> s.lastUpdated() == lastUpdated)
                .filter(s -> s.currentKey() != null)
                .mapToInt(s -> s.currentKey().id())
                .sorted()
                .toArray();

        if (sortedKeyIds.length == 0) return MAX_KEY_ID;

        if (sortedKeyIds[sortedKeyIds.length - 1] == MAX_KEY_ID) {
            if (sortedKeyIds[0] == 0) {
                for (int i = 1; i < sortedKeyIds.length; i++) {
                    if (sortedKeyIds[i] - sortedKeyIds[i - 1] > 1) {
                        return sortedKeyIds[i - 1];
                    }
                }
                return 0;
            }
            return MAX_KEY_ID;
        }
        return sortedKeyIds[sortedKeyIds.length - 1];
    }

    public int getNextKeyId() {
        this.lastActiveKeyId += 1;
        if (this.lastActiveKeyId > MAX_KEY_ID) {
            this.lastActiveKeyId = 0;
        }
        return this.lastActiveKeyId;
    }
}