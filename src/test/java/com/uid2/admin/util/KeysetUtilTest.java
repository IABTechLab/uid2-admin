package com.uid2.admin.util;

import com.uid2.admin.auth.AdminKeyset;
import com.uid2.admin.model.ClientType;
import com.uid2.shared.auth.Keyset;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.uid2.admin.util.KeysetUtil.adminKeysetToKeyset;
import static com.uid2.admin.util.KeysetUtil.lookUpKeyset;
import static org.junit.jupiter.api.Assertions.*;

public class KeysetUtilTest {

    @Test
    public void testLookUpKeyset() {
        Map<Integer, AdminKeyset> keysets = Map.of(
                1, new AdminKeyset(1, 1, "", new HashSet<>(), 0, true, true, new HashSet<>()),
                2, new AdminKeyset(2, 2, "", new HashSet<>(), 0, true, false, new HashSet<>()),
                3, new AdminKeyset(3, 2, "", new HashSet<>(), 0, true, true, new HashSet<>()),
                4, new AdminKeyset(4, 3, "", new HashSet<>(), 0, true, true, new HashSet<>())
        );

        Keyset result = lookUpKeyset(2, keysets);
        assertEquals(result, keysets.get(3));

        result = lookUpKeyset(4, keysets);
        assertNull(result);
    }

    @Test
    public void testAdminKeysetToKeyset() {
        Map<ClientType, Set<Integer>> clientTypeSetMap = Map.of(
                ClientType.DSP, Set.of(44,45,46),
                ClientType.ADVERTISER, Set.of(56,77,89),
                ClientType.PUBLISHER, Set.of(67,94,35),
                ClientType.DATA_PROVIDER, Set.of(66,34)
        );

        List<AdminKeyset> adminKeysets = List.of(
                new AdminKeyset(1, 1, "", Set.of(1,2,3), 0, true, true, new HashSet<>()),
                new AdminKeyset(2, 2, "", Set.of(1), 0, true, false, Set.of(ClientType.DSP)),
                new AdminKeyset(3, 3, "", Set.of(1), 0, true, false, Set.of(ClientType.DSP, ClientType.ADVERTISER, ClientType.PUBLISHER, ClientType.DATA_PROVIDER)),
                new AdminKeyset(4, 4, "", new HashSet<>(), 0, true, true, Set.of(ClientType.DSP)),
                new AdminKeyset(5, 5, "", null, 0, true, true, new HashSet<>())
        );

        List<Keyset> expectedKeysets = List.of(
                new Keyset(1, 1, "", Set.of(1,2,3), 0, true, true),
                new Keyset(2, 2, "", Set.of(1,44,45,46), 0, true, false),
                new Keyset(3, 3, "", Set.of(1,44,45,46,56,77,89,67,94,35,66,34), 0, true, false),
                new Keyset(4, 4, "", Set.of(44,45,46), 0, true, true),
                new Keyset(5, 5, "", null, 0, true, true)
        );

        for(int i = 0; i < adminKeysets.size(); i++) {
            Keyset result = adminKeysetToKeyset(adminKeysets.get(0), clientTypeSetMap);
            assertTrue(expectedKeysets.get(0).equals(result));
        }
    }
}
