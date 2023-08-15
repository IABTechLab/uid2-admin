package com.uid2.admin.managers;

import com.uid2.admin.secret.IKeysetKeyManager;
import com.uid2.admin.store.writer.KeysetStoreWriter;
import com.uid2.admin.util.KeysetUtil;
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
import java.util.Set;
import java.util.Map;

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
    @Mock protected KeysetSnapshot keysetSnapshot;

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
        when(keysetSnapshot.getAllKeysets()).thenReturn(keysets);
    }

    @Test
    public void testAddOrReplaceKeyset() throws Exception{
        KeysetManager keysetManager = new KeysetManager(keysetProvider, keysetStoreWriter,
                keysetKeyManager, true);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>() {{
                put(1, KeysetUtil.createDefaultKeyset(3, 1));
                put(2, KeysetUtil.createDefaultKeyset(4, 2));
                put(3, KeysetUtil.createDefaultKeyset(5, 3));
                put(4, KeysetUtil.createDefaultKeyset(6, 4));
            }};

        setKeysets(keysets);

        // add new keyset
        Keyset keyset1 = KeysetUtil.createDefaultKeyset(7, 5);
        keysetManager.addOrReplaceKeyset(keyset1);
        assertTrue(keysets.containsKey(5));
        assertTrue(keyset1.equals(keysets.get(5)));
        verify(keysetStoreWriter).upload(mapOfSize(5), isNull());

        //Reset between tests so verify works
        reset(keysetStoreWriter);

        // add existing Keyset
        Keyset keyset2 = new Keyset(5, 7, "newKeyset", Set.of(1, 2, 3), 1 , true, true);
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
            put(1, KeysetUtil.createDefaultKeyset(3, 1));
            put(2, KeysetUtil.createDefaultKeyset(4, 2));
            put(3, KeysetUtil.createDefaultKeyset(5, 3));
            put(4, KeysetUtil.createDefaultKeyset(6, 4));
        }};

        setKeysets(keysets);
        // Sharer makes an empty list
        ClientKey sharer = new ClientKey("", "", "",  "", Instant.now(), Set.of(Role.SHARER), 7, false);
        keysetManager.createKeysetForClient(sharer);
        Keyset sharerKeyset = keysets.get(5);
        assertEquals(sharerKeyset.getAllowedSites(), Set.of());
        // Generator makes a null list
        ClientKey generator = new ClientKey("", "", "",  "", Instant.now(), Set.of(Role.GENERATOR), 8, false);
        keysetManager.createKeysetForClient(generator);
        Keyset generatorKeyset = keysets.get(6);
        assertNull(generatorKeyset.getAllowedSites());
        // Generator takes priority of sharer
        ClientKey sharerGenerator = new ClientKey("", "", "",  "", Instant.now(), Set.of(Role.SHARER, Role.GENERATOR), 9, false);
        keysetManager.createKeysetForClient(sharerGenerator);
        Keyset bothKeyset = keysets.get(7);
        assertNull(bothKeyset.getAllowedSites());
        // If keyset already exists none gets added
        keysetManager.createKeysetForClient(sharer);
        assertFalse(keysets.containsKey(8));
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
