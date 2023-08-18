package com.uid2.admin.managers;

import com.google.common.collect.ImmutableMap;
import com.uid2.admin.secret.IKeysetKeyManager;
import com.uid2.admin.store.writer.KeysetStoreWriter;
import com.uid2.shared.auth.ClientKey;
import com.uid2.shared.auth.Keyset;
import com.uid2.shared.auth.KeysetSnapshot;
import com.uid2.shared.auth.Role;
import com.uid2.shared.store.reader.RotatingKeysetProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;

import static com.uid2.admin.managers.KeysetManager.*;
import static com.uid2.admin.managers.KeysetManager.createDefaultKeyset;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class KeysetManagerTest {
    protected AutoCloseable mocks;

    @Mock protected KeysetStoreWriter keysetStoreWriter;
    @Mock protected RotatingKeysetProvider keysetProvider;
    @Mock
    protected IKeysetKeyManager keysetKeyManager;
    protected KeysetSnapshot keysetSnapshot;

    @BeforeEach
    public void setupMocks() {
        mocks = MockitoAnnotations.openMocks(this);
        when(keysetProvider.getSnapshot()).thenReturn(keysetSnapshot);
    }

    @AfterEach
    public void teardown() throws Exception {
        mocks.close();
    }

    protected void setKeysets(Map<Integer, Keyset> keysets) {
        keysetSnapshot = new KeysetSnapshot(keysets);
        when(keysetProvider.getSnapshot()).thenReturn(keysetSnapshot);
    }

    @Test
    public void testAddOrReplaceKeyset() throws Exception{
        KeysetManager keysetManager = new KeysetManager(keysetProvider, keysetStoreWriter,
                keysetKeyManager, true);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
                put(1, KeysetManager.createDefaultKeyset(3, 1));
                put(2, KeysetManager.createDefaultKeyset(4, 2));
                put(3, KeysetManager.createDefaultKeyset(5, 3));
                put(4, KeysetManager.createDefaultKeyset(6, 4));
            }};

        setKeysets(keysets);
        final int keysetId = 5;
        // add new keyset
        Keyset keyset1 = KeysetManager.createDefaultKeyset(7, keysetId);
        keysetManager.addOrReplaceKeyset(keyset1);
        assertTrue(keysets.containsKey(5));
        assertTrue(keyset1.equals(keysets.get(5)));
        verify(keysetStoreWriter).upload(mapOfSize(5), isNull());

        //Reset between tests so verify works
        reset(keysetStoreWriter);

        // add existing Keyset
        Keyset keyset2 = new Keyset(keysetId, 7, "newKeyset", Set.of(1, 2, 3), 1 , true, true);
        keysetManager.addOrReplaceKeyset(keyset2);
        assertTrue(keysets.containsKey(5));
        assertFalse(keyset1.equals(keysets.get(5)));
        assertTrue(keyset2.equals(keysets.get(5)));
        verify(keysetStoreWriter).upload(mapOfSize(5), isNull());
    }

    @Test
    public void testCreateKeysetForClient() throws Exception {
        KeysetManager keysetManager = new KeysetManager(keysetProvider, keysetStoreWriter,
                keysetKeyManager, true);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
            put(1, KeysetManager.createDefaultKeyset(3, 1));
            put(2, KeysetManager.createDefaultKeyset(4, 2));
            put(3, KeysetManager.createDefaultKeyset(5, 3));
            put(4, KeysetManager.createDefaultKeyset(6, 4));
        }};

        setKeysets(keysets);
        // Sharer makes an empty list
        ClientKey sharer = new ClientKey("", "", "",  "", Instant.now(), Set.of(Role.SHARER), 7, false);
        Keyset returnedKeyset = keysetManager.createKeysetForClient(sharer);
        Keyset sharerKeyset = keysets.get(returnedKeyset.getKeysetId());
        assertTrue(sharerKeyset.equals(returnedKeyset));
        assertEquals(sharerKeyset.getAllowedSites(), Set.of());

        // Generator makes a null list
        ClientKey generator = new ClientKey("", "", "",  "", Instant.now(), Set.of(Role.GENERATOR), 8, false);
        returnedKeyset = keysetManager.createKeysetForClient(generator);
        Keyset generatorKeyset = keysets.get(returnedKeyset.getKeysetId());
        assertTrue(generatorKeyset.equals(returnedKeyset));
        assertNull(generatorKeyset.getAllowedSites());

        // Generator takes priority of sharer
        ClientKey sharerGenerator = new ClientKey("", "", "",  "", Instant.now(), Set.of(Role.SHARER, Role.GENERATOR), 9, false);
        returnedKeyset = keysetManager.createKeysetForClient(sharerGenerator);
        Keyset bothKeyset = keysets.get(returnedKeyset.getKeysetId());
        assertTrue(bothKeyset.equals(returnedKeyset));
        assertNull(bothKeyset.getAllowedSites());

        // If keyset already exists none gets added
        returnedKeyset = keysetManager.createKeysetForClient(sharer);
        assertTrue(sharerKeyset.equals(returnedKeyset));

        //There should only be 7 keysets
        assertEquals(7, keysets.keySet().size());
    }


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
        assertEquals(keyset.getName(), KeysetManager.MasterKeysetName);
        keyset = createDefaultKeyset(-2, 2);
        assertEquals(keyset.getName(), KeysetManager.RefreshKeysetName);
        keyset = createDefaultKeyset(2, 3);
        assertEquals(keyset.getName(), KeysetManager.FallbackPublisherKeysetName);

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

    protected static class MapOfSize implements ArgumentMatcher<Map> {
        private final int expectedSize;

        public MapOfSize(int expectedSize) {
            this.expectedSize = expectedSize;
        }

        public boolean matches(Map c) {
            return c.size() == expectedSize;
        }
    }

    protected static Map mapOfSize(int expectedSize) {
        return argThat(new MapOfSize(expectedSize));
    }
}
