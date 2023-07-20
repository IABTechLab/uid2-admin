package com.uid2.admin.util;

import com.uid2.admin.auth.AdminKeyset;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

public class KeysetUtil {
    public static AdminKeyset lookUpKeyset(int siteId, Map<Integer, AdminKeyset> keysets) {
        for (AdminKeyset keyset: keysets.values()) {
            if(keyset.getSiteId() == siteId && keyset.isDefault()) {
                return keyset;
            }
        }
        return null;
    }

    public static Integer getMaxKeyset(Map<Integer, AdminKeyset> keysets) {
        return keysets.keySet().size();
    }

    public static AdminKeyset createDefaultKeyset(int siteId, int keysetId) {
        return new AdminKeyset(keysetId, siteId, "", null, Instant.now().getEpochSecond(), true, true, new HashSet<>());
    }
}
