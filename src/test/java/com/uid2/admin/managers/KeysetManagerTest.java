package com.uid2.admin.managers;

import com.google.common.collect.ImmutableMap;
import com.uid2.admin.auth.AdminKeyset;
import com.uid2.admin.auth.AdminKeysetSnapshot;
import com.uid2.admin.secret.IKeysetKeyManager;
import com.uid2.admin.store.reader.RotatingAdminKeysetStore;
import com.uid2.admin.store.writer.AdminKeysetWriter;
import com.uid2.shared.auth.ClientKey;
import com.uid2.shared.auth.Keyset;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.ClientType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.*;

import static com.uid2.admin.managers.KeysetManager.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

public class KeysetManagerTest {
    protected AutoCloseable mocks;

    @Mock protected AdminKeysetWriter keysetStoreWriter;
    @Mock protected RotatingAdminKeysetStore keysetProvider;
    @Mock
    protected IKeysetKeyManager keysetKeyManager;
    protected AdminKeysetSnapshot keysetSnapshot;

    @BeforeEach
    public void setupMocks() {
        mocks = MockitoAnnotations.openMocks(this);
        when(keysetProvider.getSnapshot()).thenReturn(keysetSnapshot);
    }

    @AfterEach
    public void teardown() throws Exception {
        mocks.close();
    }

    protected void setKeysets(Map<Integer, AdminKeyset> keysets) {
        keysetSnapshot = new AdminKeysetSnapshot(keysets);
        when(keysetProvider.getSnapshot()).thenReturn(keysetSnapshot);
    }

    @Test
    public void testAddOrReplaceKeyset() throws Exception{
        KeysetManager keysetManager = new KeysetManager(keysetProvider, keysetStoreWriter,
                keysetKeyManager, true);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
                put(1, KeysetManager.createDefaultKeyset(3, 1));
                put(2, KeysetManager.createDefaultKeyset(4, 2));
                put(3, KeysetManager.createDefaultKeyset(5, 3));
                put(4, KeysetManager.createDefaultKeyset(6, 4));
            }};

        setKeysets(keysets);
        final int keysetId = 5;
        // add new keyset
        AdminKeyset keyset1 = KeysetManager.createDefaultKeyset(7, keysetId);
        keysetManager.addOrReplaceKeyset(keyset1);
        assertTrue(keysets.containsKey(5));
        assertTrue(keyset1.equals(keysets.get(5)));
        verify(keysetStoreWriter).upload(mapOfSize(5), isNull());

        //Reset between tests so verify works
        reset(keysetStoreWriter);

        // add existing AdminKeyset
        AdminKeyset keyset2 = new AdminKeyset(keysetId, 7, "newKeyset", Set.of(1, 2, 3), 1 , true, true, new HashSet<>());
        keysetManager.addOrReplaceKeyset(keyset2);
        assertTrue(keysets.containsKey(5));
        assertFalse(keyset1.equals(keysets.get(5)));
        assertTrue(keyset2.equals(keysets.get(5)));
        verify(keysetStoreWriter).upload(mapOfSize(5), isNull());
    }

    @Nested
    class createKeysetForSite {
        @Test
        public void createsKeysetWhenNoneExists() throws Exception {
            setKeysets(new HashMap<>());

            final KeysetManager keysetManager = new KeysetManager(keysetProvider, keysetStoreWriter, keysetKeyManager, true);

            final AdminKeyset keysetForSite = keysetManager.createKeysetForSite(1);

            verify(keysetStoreWriter).upload(mapOfSize(1), isNull());

            assertNotNull(keysetForSite);
        }

        @Test
        public void doesNotCreateKeysetWhenOneExists() throws Exception {
            final AdminKeyset keyset = KeysetManager.createDefaultKeyset(1, 1);
            final HashMap<Integer, AdminKeyset> keysets = new HashMap<>();
            keysets.put(1, keyset);

            setKeysets(keysets);

            final KeysetManager keysetManager = new KeysetManager(keysetProvider, keysetStoreWriter, keysetKeyManager, true);

            final AdminKeyset actual = keysetManager.createKeysetForSite(1);

            verifyNoInteractions(keysetStoreWriter);

            assertEquals(keyset, actual);
        }

        @Test
        public void returnsNullWhenKeysetsAreNotEnabled() throws Exception {
            final KeysetManager keysetManager = new KeysetManager(keysetProvider, keysetStoreWriter, keysetKeyManager, false);

            final AdminKeyset actual = keysetManager.createKeysetForSite(1);

            verifyNoInteractions(keysetStoreWriter);

            assertNull(actual);
        }
    }

    @Test
    public void testCreateKeysetForClient() throws Exception {
        KeysetManager keysetManager = new KeysetManager(keysetProvider, keysetStoreWriter,
                keysetKeyManager, true);

        Map<Integer, AdminKeyset> keysets = new HashMap<Integer, AdminKeyset>() {{
            put(1, KeysetManager.createDefaultKeyset(3, 1));
            put(2, KeysetManager.createDefaultKeyset(4, 2));
            put(3, KeysetManager.createDefaultKeyset(5, 3));
            put(4, KeysetManager.createDefaultKeyset(6, 4));
        }};

        setKeysets(keysets);
        // Sharer makes an empty list
        ClientKey sharer = new ClientKey("", "", "",  "", "", "", Instant.now(), Set.of(Role.SHARER), 7, false);
        AdminKeyset returnedKeyset = keysetManager.createKeysetForClient(sharer);
        AdminKeyset sharerKeyset = keysets.get(returnedKeyset.getKeysetId());
        assertTrue(sharerKeyset.equals(returnedKeyset));
        assertEquals(sharerKeyset.getAllowedSites(), Set.of());

        // Generator makes a null list
        ClientKey generator = new ClientKey("", "", "",  "", "", "", Instant.now(), Set.of(Role.GENERATOR), 8, false);
        returnedKeyset = keysetManager.createKeysetForClient(generator);
        AdminKeyset generatorKeyset = keysets.get(returnedKeyset.getKeysetId());
        assertTrue(generatorKeyset.equals(returnedKeyset));
        assertNull(generatorKeyset.getAllowedSites());

        // Generator takes priority of sharer
        ClientKey sharerGenerator = new ClientKey("", "", "",  "", "", "", Instant.now(), Set.of(Role.SHARER, Role.GENERATOR), 9, false);
        keysetManager.createKeysetForClient(sharerGenerator);
        returnedKeyset = keysetManager.createKeysetForClient(sharerGenerator);
        AdminKeyset bothKeyset = keysets.get(returnedKeyset.getKeysetId());
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
        Map<Integer, AdminKeyset> keysets = Map.of(
                1, new AdminKeyset(1, 1, "", new HashSet<>(), 0, true, true, new HashSet<>()),
                2, new AdminKeyset(2, 2, "", new HashSet<>(), 0, true, false, new HashSet<>()),
                3, new AdminKeyset(3, 2, "", new HashSet<>(), 0, true, true, new HashSet<>()),
                4, new AdminKeyset(4, 3, "", new HashSet<>(), 0, true, true, new HashSet<>())
        );

        AdminKeyset result = lookUpKeyset(2, keysets);
        assertEquals(result, keysets.get(3));

        result = lookUpKeyset(4, keysets);
        assertNull(result);
    }

    // keyset id 1/2/3 are assigned for master/refresh/default publisher encryption key ids,
    // so we always reserve these 3 keyset ids for them
    @Test
    public void testGetMaxKeyset() {
        Map<Integer, AdminKeyset> emptyKeysets = ImmutableMap.of();
        assertEquals(3, getMaxKeyset(emptyKeysets));


        Map<Integer, AdminKeyset> singleKeyset = Map.of(
                1, new AdminKeyset(1, 1, "", new HashSet<>(), 0, true, true, new HashSet<>())
        );
        assertEquals(3, getMaxKeyset(singleKeyset));

        Map<Integer, AdminKeyset> twoKeysets = Map.of(
                1, new AdminKeyset(1, -1, "", new HashSet<>(), 0, true, true, new HashSet<>()),
                2, new AdminKeyset(2, -2, "", new HashSet<>(), 0, true, false, new HashSet<>())
        );
        assertEquals(3, getMaxKeyset(twoKeysets));

        Map<Integer, AdminKeyset> threeKeysets = Map.of(
                1, new AdminKeyset(1, -1, "", new HashSet<>(), 0, true, true, new HashSet<>()),
                2, new AdminKeyset(2, -2, "", new HashSet<>(), 0, true, false, new HashSet<>()),
                3, new AdminKeyset(3, 2, "", new HashSet<>(), 0, true, true, new HashSet<>())
        );
        assertEquals(3, getMaxKeyset(threeKeysets));

        Map<Integer, AdminKeyset> fourKeysets = Map.of(
                1, new AdminKeyset(1, -1, "", new HashSet<>(), 0, true, true, new HashSet<>()),
                2, new AdminKeyset(2, -2, "", new HashSet<>(), 0, true, false, new HashSet<>()),
                3, new AdminKeyset(3, 2, "", new HashSet<>(), 0, true, true, new HashSet<>()),
                4, new AdminKeyset(4, 3, "", new HashSet<>(), 0, true, true, new HashSet<>())
        );
        assertEquals(4, getMaxKeyset(fourKeysets));
    }

    @Test
    public void testKeysetNameCreation() {

        //expected cases of special keysets when site id and keyset id match our expectations
        AdminKeyset keyset = createDefaultKeyset(-1, -1);
        assertEquals(keyset.getName(), KeysetManager.MasterKeysetName);
        keyset = createDefaultKeyset(-2, -2);
        assertEquals(keyset.getName(), KeysetManager.RefreshKeysetName);
        keyset = createDefaultKeyset(2, 2);
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
            Keyset result = KeysetManager.adminKeysetToKeyset(adminKeysets.get(i), clientTypeSetMap);
            assertTrue(expectedKeysets.get(i).equals(result));
        }
    }

    @Test
    public void testCreateAdminKeysetsAllNew() throws Exception {
        Map<Integer, Keyset> keysets = Map.of(
                1, new Keyset(1, 1, "", Set.of(1,2,3), 0, true, true),
                2, new Keyset(2, 2, "", Set.of(1,44,45,46), 0, true, false),
                3, new Keyset(3, 3, "", Set.of(1,44,45,46,56,77,89,67,94,35,66,34), 0, true, false),
                4, new Keyset(4, 4, "", Set.of(44,45,46), 0, true, true),
                5, new Keyset(5, 5, "", null, 0, true, true)
        );

        Map<Integer, AdminKeyset> adminKeysetMap = new HashMap<>();
        setKeysets(adminKeysetMap);

        List<AdminKeyset> expectedKeysets =List.of(
                new AdminKeyset(1, 1, "", Set.of(1,2,3), 0, true, true, new HashSet<>()),
                new AdminKeyset(2, 2, "", Set.of(1,44,45,46), 0, true, false, new HashSet<>()),
                new AdminKeyset(3, 3, "", Set.of(1,44,45,46,56,77,89,67,94,35,66,34), 0, true, false, new HashSet<>()),
                new AdminKeyset(4, 4, "", Set.of(44,45,46), 0, true, true, new HashSet<>()),
                new AdminKeyset(5, 5, "", null, 0, true, true, new HashSet<>())
        );

        KeysetManager keysetManager = new KeysetManager(keysetProvider, keysetStoreWriter,
                keysetKeyManager, true);

        keysetManager.createAdminKeysets(keysets);

        verify(keysetStoreWriter).upload(mapOfSize(5), isNull());

        for(AdminKeyset expectedKeyset : expectedKeysets) {
            AdminKeyset result = adminKeysetMap.get(expectedKeyset.getKeysetId());
            assertTrue(expectedKeyset.equals(result));
        }
    }

    @Test
    public void testCreateAdminKeysetsOneNew() throws Exception {
        Map<Integer, Keyset> keysets = Map.of(
                1, new Keyset(1, 1, "", Set.of(1,2,3), 0, true, true),
                2, new Keyset(2, 2, "", Set.of(1,44,45,46), 0, true, false),
                3, new Keyset(3, 3, "", Set.of(1,44,45,46,56,77,89,67,94,35,66,34), 0, true, false),
                4, new Keyset(4, 4, "", Set.of(44,45,46), 0, true, true),
                5, new Keyset(5, 5, "", null, 0, true, true)
        );

        Map<Integer, AdminKeyset> adminKeysetMap = new HashMap<>();

        adminKeysetMap.put(1, new AdminKeyset(1, 1, "", Set.of(1,2,3), 0, true, true, new HashSet<>()));
        adminKeysetMap.put(2, new AdminKeyset(2, 2, "", Set.of(1,44,45,46), 0, true, false, new HashSet<>()));
        adminKeysetMap.put(3, new AdminKeyset(3, 3, "", Set.of(1,44,45,46,56,77,89,67,94,35,66,34), 0, true, false, new HashSet<>()));
        adminKeysetMap.put(4, new AdminKeyset(4, 4, "", Set.of(44,45,46), 0, true, true, new HashSet<>()));

        setKeysets(adminKeysetMap);

        List<AdminKeyset> expectedKeysets =List.of(
                new AdminKeyset(1, 1, "", Set.of(1,2,3), 0, true, true, new HashSet<>()),
                new AdminKeyset(2, 2, "", Set.of(1,44,45,46), 0, true, false, new HashSet<>()),
                new AdminKeyset(3, 3, "", Set.of(1,44,45,46,56,77,89,67,94,35,66,34), 0, true, false, new HashSet<>()),
                new AdminKeyset(4, 4, "", Set.of(44,45,46), 0, true, true, new HashSet<>()),
                new AdminKeyset(5, 5, "", null, 0, true, true, new HashSet<>())
        );

        KeysetManager keysetManager = new KeysetManager(keysetProvider, keysetStoreWriter,
                keysetKeyManager, true);

        keysetManager.createAdminKeysets(keysets);

        verify(keysetStoreWriter).upload(mapOfSize(5), isNull());

        for(AdminKeyset expectedKeyset : expectedKeysets) {
            AdminKeyset result = adminKeysetMap.get(expectedKeyset.getKeysetId());
            assertTrue(expectedKeyset.equals(result));
        }
    }

    @Test
    public void testCreateAdminKeysetsNoNew() throws Exception {
        Map<Integer, Keyset> keysets = Map.of(
                1, new Keyset(1, 1, "", Set.of(1,2,3), 0, true, true),
                2, new Keyset(2, 2, "", Set.of(1,44,45,46), 0, true, false),
                3, new Keyset(3, 3, "", Set.of(1,44,45,46,56,77,89,67,94,35,66,34), 0, true, false),
                4, new Keyset(4, 4, "", Set.of(44,45,46), 0, true, true),
                5, new Keyset(5, 5, "", null, 0, true, true)
        );

        Map<Integer, AdminKeyset> adminKeysetMap = new HashMap<>();

        adminKeysetMap.put(1, new AdminKeyset(1, 1, "", Set.of(1,2,3), 0, true, true, new HashSet<>()));
        adminKeysetMap.put(2, new AdminKeyset(2, 2, "", Set.of(1,44,45,46), 0, true, false, new HashSet<>()));
        adminKeysetMap.put(3, new AdminKeyset(3, 3, "", Set.of(1,44,45,46,56,77,89,67,94,35,66,34), 0, true, false, new HashSet<>()));
        adminKeysetMap.put(4, new AdminKeyset(4, 4, "", Set.of(44,45,46), 0, true, true, new HashSet<>()));
        adminKeysetMap.put(5, new AdminKeyset(5, 5, "", null, 0, true, true, new HashSet<>()));

        setKeysets(adminKeysetMap);

        List<AdminKeyset> expectedKeysets =List.of(
                new AdminKeyset(1, 1, "", Set.of(1,2,3), 0, true, true, new HashSet<>()),
                new AdminKeyset(2, 2, "", Set.of(1,44,45,46), 0, true, false, new HashSet<>()),
                new AdminKeyset(3, 3, "", Set.of(1,44,45,46,56,77,89,67,94,35,66,34), 0, true, false, new HashSet<>()),
                new AdminKeyset(4, 4, "", Set.of(44,45,46), 0, true, true, new HashSet<>()),
                new AdminKeyset(5, 5, "", null, 0, true, true, new HashSet<>())
        );

        KeysetManager keysetManager = new KeysetManager(keysetProvider, keysetStoreWriter,
                keysetKeyManager, true);

        keysetManager.createAdminKeysets(keysets);

        verify(keysetStoreWriter).upload(mapOfSize(5), isNull());

        for(AdminKeyset expectedKeyset : expectedKeysets) {
            AdminKeyset result = adminKeysetMap.get(expectedKeyset.getKeysetId());
            assertTrue(expectedKeyset.equals(result));
        }
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
