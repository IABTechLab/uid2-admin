package com.uid2.admin.util;

import com.uid2.shared.auth.Keyset;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

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
        return keysets.keySet().size();
    }

    public static Keyset createDefaultKeyset(int siteId, int keysetId) {
        return new Keyset(keysetId, siteId, "", null, Instant.now().getEpochSecond(), true, true);
    }
}
