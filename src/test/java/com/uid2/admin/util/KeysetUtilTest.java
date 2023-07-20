package com.uid2.admin.util;

import com.uid2.admin.auth.AdminKeyset;
import com.uid2.shared.auth.Keyset;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;

import static com.uid2.admin.util.KeysetUtil.lookUpKeyset;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
