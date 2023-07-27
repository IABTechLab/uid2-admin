package com.uid2.admin.util;

import com.uid2.shared.auth.Keyset;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

import static java.lang.Math.max;

public class KeysetUtil {
    public static Keyset lookUpKeyset(int siteId, Map<Integer, Keyset> keysets) {
        for (Keyset keyset: keysets.values()) {
            if(keyset.getSiteId() == siteId && keyset.isDefault()) {
                return keyset;
            }
        }
        return null;
    }

    public static Integer getMaxKeyset(Map<Integer, Keyset> keysets) {
        // keyset id 1/2/3 are assigned for master/refresh/default publisher encryption key ids,
        // so we always reserve these 3 keyset ids for them
        if(keysets.isEmpty()) return 3;
        return max(Collections.max(keysets.keySet()), 3);
    }

    public static Keyset createDefaultKeyset(int siteId, int keysetId) {
        return new Keyset(keysetId, siteId, "", null, Instant.now().getEpochSecond(), true, true);
    }
}
