package com.uid2.admin.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.uid2.shared.Const;
import com.uid2.shared.auth.Keyset;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;

import static com.uid2.admin.util.KeysetUtil.*;
import static com.uid2.admin.util.KeysetUtil.createDefaultKeyset;
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

    // keyset id 1/2/3 are assigned for master/refresh/default publisher encryption key ids,
    // so we always reserve these 3 keyset ids for them
    @Test
    public void testGetMaxKeyset() {
        Map<Integer, Keyset> emptyKeysets = ImmutableMap.of();
        assertEquals(3, getMaxKeyset(emptyKeysets));


        Map<Integer, Keyset> singleKeyset = Map.of(
                1, new Keyset(1, 1, "", new HashSet<>(), 0, true, true)
        );
        assertEquals(3, getMaxKeyset(singleKeyset));

        Map<Integer, Keyset> twoKeysets = Map.of(
                1, new Keyset(1, -1, "", new HashSet<>(), 0, true, true),
                2, new Keyset(2, -2, "", new HashSet<>(), 0, true, false)
        );
        assertEquals(3, getMaxKeyset(twoKeysets));

        Map<Integer, Keyset> threeKeysets = Map.of(
                1, new Keyset(1, -1, "", new HashSet<>(), 0, true, true),
                2, new Keyset(2, -2, "", new HashSet<>(), 0, true, false),
                3, new Keyset(3, 2, "", new HashSet<>(), 0, true, true)
        );
        assertEquals(3, getMaxKeyset(threeKeysets));

        Map<Integer, Keyset> fourKeysets = Map.of(
                1, new Keyset(1, -1, "", new HashSet<>(), 0, true, true),
                2, new Keyset(2, -2, "", new HashSet<>(), 0, true, false),
                3, new Keyset(3, 2, "", new HashSet<>(), 0, true, true),
                4, new Keyset(4, 3, "", new HashSet<>(), 0, true, true)
        );
        assertEquals(4, getMaxKeyset(fourKeysets));
    }

    @Test
    public void testKeysetNameCreation() {

        //expected cases of special keysets when site id and keyset id match our expectations
        Keyset keyset = createDefaultKeyset(-1, 1);
        assertEquals(keyset.getName(), KeysetUtil.MasterKeysetName);
        keyset = createDefaultKeyset(-2, 2);
        assertEquals(keyset.getName(), KeysetUtil.RefreshKeysetName);
        keyset = createDefaultKeyset(2, 3);
        assertEquals(keyset.getName(), KeysetUtil.FallbackPublisherKeysetName);

        //only site id matches but keyset id aren't the same as what we expected
        keyset = createDefaultKeyset(-1, 3);
        assertEquals(keyset.getName(), "");
        keyset = createDefaultKeyset(-2, 34);
        assertEquals(keyset.getName(), "");
        keyset = createDefaultKeyset(2, 56);
        assertEquals(keyset.getName(), "");

        //only keyset id matches but site Id aren't the same as what we expected
        keyset = createDefaultKeyset(-5, 1);
        assertEquals(keyset.getName(), "");
        keyset = createDefaultKeyset(-3, 2);
        assertEquals(keyset.getName(), "");
        keyset = createDefaultKeyset(20, 3);
        assertEquals(keyset.getName(), "");

        //for any other normal keyset creation
        keyset = createDefaultKeyset(6, 7);
        assertEquals(keyset.getName(), "");
        keyset = createDefaultKeyset(9, 23);
        assertEquals(keyset.getName(), "");
    }
}
