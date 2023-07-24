package com.uid2.admin.util;

import com.uid2.admin.auth.AdminKeyset;
import com.uid2.admin.model.ClientType;
import com.uid2.shared.auth.Keyset;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.lang.Math.max;

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
        if(keysets.isEmpty()) return 3;
        return max(Collections.max(keysets.keySet()), 3);
    }

    public static AdminKeyset createDefaultKeyset(int siteId, int keysetId) {
        return new AdminKeyset(keysetId, siteId, "", null, Instant.now().getEpochSecond(), true, true, new HashSet<>());
    }

    public static Keyset adminKeysetToKeyset(AdminKeyset adminKeyset, Map<ClientType, Set<Integer>> siteIdsByType) {
        Set<Integer> allowedList = adminKeyset.getAllowedSites();
        if(allowedList == null) {
            return adminKeyset;
        }

        for (ClientType type : adminKeyset.getAllowedTypes()) {
            allowedList.addAll(siteIdsByType.get(type));
        }
        return new Keyset(adminKeyset.getKeysetId(), adminKeyset.getSiteId(), adminKeyset.getName(), allowedList,
                adminKeyset.getCreated(), adminKeyset.isEnabled(), adminKeyset.isDefault());
    }
}
