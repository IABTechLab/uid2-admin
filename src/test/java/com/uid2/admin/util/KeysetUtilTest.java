package com.uid2.admin.util;

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
        Map<Integer, Keyset> keysets = Map.of(
                1, new Keyset(1, 1, "", new HashSet<>(), 0, true, true),
                2, new Keyset(2, 2, "", new HashSet<>(), 0, true, false),
                3, new Keyset(3, 2, "", new HashSet<>(), 0, true, true),
                4, new Keyset(4, 3, "", new HashSet<>(), 0, true, true)
        );

        Keyset result = lookUpKeyset(2, keysets);
        assertEquals(result, keysets.get(3));

        result = lookUpKeyset(4, keysets);
        assertNull(result);
    }
}
