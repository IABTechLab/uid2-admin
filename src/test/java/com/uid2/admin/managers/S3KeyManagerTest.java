package com.uid2.admin.managers;

import ch.qos.logback.classic.Logger;
import com.uid2.admin.store.writer.S3KeyStoreWriter;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.model.S3Key;
import com.uid2.shared.secret.IKeyGenerator;
import com.uid2.shared.store.reader.RotatingS3KeyProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class S3KeyManagerTest {

    private RotatingS3KeyProvider s3KeyProvider;
    private S3KeyStoreWriter s3KeyStoreWriter;
    private IKeyGenerator keyGenerator;
    private S3KeyManager s3KeyManager;

    @BeforeEach
    void setUp() {
        s3KeyProvider = mock(RotatingS3KeyProvider.class);
        s3KeyStoreWriter = mock(S3KeyStoreWriter.class);
        keyGenerator = mock(IKeyGenerator.class);
        s3KeyManager = new S3KeyManager(s3KeyProvider, s3KeyStoreWriter,keyGenerator);
    }

    @Test
    void testGenerateS3Key() throws Exception {
        when(keyGenerator.generateRandomKeyString(32)).thenReturn("randomKeyString");

        S3Key s3Key = s3KeyManager.generateS3Key(1, 1000L, 2000L);

        assertNotNull(s3Key);
        assertEquals(1, s3Key.getSiteId());
        assertEquals(1000L, s3Key.getActivates());
        assertEquals(2000L, s3Key.getCreated());
        assertEquals("randomKeyString", s3Key.getSecret());
    }

    @Test
    void testAddS3KeyToEmpty() throws Exception {
        S3Key s3Key = new S3Key(1, 1, 1000L, 2000L, "randomKeyString");

        Map<Integer, S3Key> existingKeys = new HashMap<>();
        when(s3KeyProvider.getAll()).thenReturn(existingKeys);

        s3KeyManager.addS3Key(s3Key);

        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(s3KeyStoreWriter).upload(captor.capture(), isNull());

        Map<Integer, S3Key> capturedKeys = captor.getValue();
        assertEquals(1, capturedKeys.size());
        assertEquals(s3Key, capturedKeys.get(1));
    }

    @Test
    void testAddS3KeyToExisting() throws Exception {
        S3Key s3Key = new S3Key(3, 1, 1000L, 2000L, "randomKeyString");

        Map<Integer, S3Key> existingKeys = new HashMap<>();
        S3Key existingKey1 = new S3Key(1, 1, 500L, 1500L, "existingSecret1");
        S3Key existingKey2 = new S3Key(2, 1, 600L, 1600L, "existingSecret2");
        existingKeys.put(1, existingKey1);
        existingKeys.put(2, existingKey2);

        when(s3KeyProvider.getAll()).thenReturn(existingKeys);

        s3KeyManager.addS3Key(s3Key);

        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(s3KeyStoreWriter).upload(captor.capture(), isNull());

        Map<Integer, S3Key> capturedKeys = captor.getValue();

        assertEquals(3, capturedKeys.size());
        assertEquals(existingKey1, capturedKeys.get(1));
        assertEquals(existingKey2, capturedKeys.get(2));
        assertEquals(s3Key, capturedKeys.get(3));
    }

    @Test
    void testGetNextKeyId() {
        Map<Integer, S3Key> existingKeys = new HashMap<>();
        existingKeys.put(1, new S3Key(1, 1, 500L, 1500L, "existingSecret1"));
        when(s3KeyProvider.getAll()).thenReturn(existingKeys);

        int nextKeyId = s3KeyManager.getNextKeyId();

        assertEquals(2, nextKeyId);
    }

    @Test
    void testGetS3Key() {
        S3Key s3Key = new S3Key(1, 1, 500L, 1500L, "existingSecret1");
        Map<Integer, S3Key> existingKeys = new HashMap<>();
        existingKeys.put(1, s3Key);
        when(s3KeyProvider.getAll()).thenReturn(existingKeys);

        S3Key result = s3KeyManager.getS3KeyByKeyIdentifier(1);

        assertEquals(s3Key, result);
    }

    @Test
    void testGetAllS3Keys() {
        Map<Integer, S3Key> existingKeys = new HashMap<>();
        S3Key existingKey1 = new S3Key(1, 1, 500L, 1500L, "existingSecret1");
        S3Key existingKey2 = new S3Key(2, 1, 600L, 1600L, "existingSecret2");
        existingKeys.put(1, existingKey1);
        existingKeys.put(2, existingKey2);

        when(s3KeyProvider.getAll()).thenReturn(existingKeys);

        Map<Integer, S3Key> result = s3KeyManager.getAllS3Keys();

        assertEquals(existingKeys, result);
    }

    @Test
    void testAddS3Key() throws Exception {
        S3Key s3Key = new S3Key(1, 1, 1000L, 2000L, "randomKeyString");

        Map<Integer, S3Key> existingKeys = new HashMap<>();
        when(s3KeyProvider.getAll()).thenReturn(existingKeys);

        s3KeyManager.addS3Key(s3Key);

        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(s3KeyStoreWriter).upload(captor.capture(), isNull());

        Map<Integer, S3Key> capturedKeys = captor.getValue();
        assertEquals(1, capturedKeys.size());
        assertEquals(s3Key, capturedKeys.get(1));
    }

    @Test
    void testGetS3KeyBySiteId() {
        S3Key key1 = new S3Key(1, 100, 0, 0, "secret1");
        S3Key key2 = new S3Key(2, 200, 0, 0, "secret2");
        Map<Integer, S3Key> keys = new HashMap<>();
        keys.put(1, key1);
        keys.put(2, key2);

        when(s3KeyProvider.getAll()).thenReturn(keys);

        Optional<S3Key> result = s3KeyManager.getS3KeyBySiteId(100);
        assertTrue(result.isPresent());
        assertEquals(key1, result.get());
    }

    @Test
    void testGetAllS3KeysBySiteId() {
        S3Key key1 = new S3Key(1, 100, 0, 0, "secret1");
        S3Key key2 = new S3Key(2, 100, 0, 0, "secret2");
        S3Key key3 = new S3Key(3, 200, 0, 0, "secret3");
        Map<Integer, S3Key> keys = new HashMap<>();
        keys.put(1, key1);
        keys.put(2, key2);
        keys.put(3, key3);

        when(s3KeyProvider.getAll()).thenReturn(keys);

        List<S3Key> result = s3KeyManager.getAllS3KeysBySiteId(100);
        assertEquals(2, result.size());
        assertTrue(result.contains(key1));
        assertTrue(result.contains(key2));
    }

    @Test
    void testCreateAndAddImmediateS3Key() throws Exception {
        when(s3KeyProvider.getAll()).thenReturn(new HashMap<>());
        when(keyGenerator.generateRandomKeyString(32)).thenReturn("generatedSecret");

        S3Key newKey = s3KeyManager.createAndAddImmediate3Key(100);

        assertNotNull(newKey);
        assertEquals(100, newKey.getSiteId());
        assertEquals("generatedSecret", newKey.getSecret());

        verify(s3KeyStoreWriter, times(1)).upload(any(Map.class), eq(null));
    }

    @Test
    public void testDoesSiteHaveKeys_SiteHasKeys() {
        int siteId = 1;
        S3Key s3Key = new S3Key(siteId, siteId, 0L, 0L, "key");
        Map<Integer, S3Key> allKeys = new HashMap<>();
        allKeys.put(1, s3Key);

        when(s3KeyProvider.getAll()).thenReturn(allKeys);

        boolean result = s3KeyManager.doesSiteHaveKeys(siteId);
        assertTrue(result);
    }
    @Test
    public void testDoesSiteHaveKeys_SiteDoesNotHaveKeys() {
        int siteId = 1;
        Map<Integer, S3Key> allKeys = new HashMap<>();

        when(s3KeyProvider.getAll()).thenReturn(allKeys);

        boolean result = s3KeyManager.doesSiteHaveKeys(siteId);
        assertFalse(result);
    }

    @Test
    public void testDoesSiteHaveKeys_AllKeysNull() {
        int siteId = 1;

        when(s3KeyProvider.getAll()).thenReturn(null);

        boolean result = s3KeyManager.doesSiteHaveKeys(siteId);
        assertFalse(result);
    }

    @Test
    public void testDoesSiteHaveKeys_MultipleKeysDifferentSiteIds() {
        S3Key s3Key1 = new S3Key(1, 1, 0L, 0L, "key1");
        S3Key s3Key2 = new S3Key(2, 2, 0L, 0L, "key2");
        Map<Integer, S3Key> allKeys = new HashMap<>();
        allKeys.put(1, s3Key1);
        allKeys.put(2, s3Key2);

        when(s3KeyProvider.getAll()).thenReturn(allKeys);

        assertTrue(s3KeyManager.doesSiteHaveKeys(1));
        assertTrue(s3KeyManager.doesSiteHaveKeys(2));
        assertFalse(s3KeyManager.doesSiteHaveKeys(3)); // Site ID 3 does not exist
    }

    @Test
    public void testDoesSiteHaveKeys_SameSiteIdMultipleKeys() {
        int siteId = 1;
        S3Key s3Key1 = new S3Key(siteId, siteId, 0L, 0L, "key1");
        S3Key s3Key2 = new S3Key(siteId, siteId, 0L, 0L, "key2");
        Map<Integer, S3Key> allKeys = new HashMap<>();
        allKeys.put(1, s3Key1);
        allKeys.put(2, s3Key2);

        when(s3KeyProvider.getAll()).thenReturn(allKeys);

        boolean result = s3KeyManager.doesSiteHaveKeys(siteId);
        assertTrue(result);
    }

    @Test
    public void testDoesSiteHaveKeys_LargeNumberOfKeys() {
        Map<Integer, S3Key> allKeys = new HashMap<>();
        for (int i = 1; i <= 1000; i++) {
            S3Key s3Key = new S3Key(i, i, 0L, 0L, "key" + i);
            allKeys.put(i, s3Key);
        }

        when(s3KeyProvider.getAll()).thenReturn(allKeys);

        for (int i = 1; i <= 1000; i++) {
            assertTrue(s3KeyManager.doesSiteHaveKeys(i));
        }
        assertFalse(s3KeyManager.doesSiteHaveKeys(1001)); // Site ID 1001 does not exist
    }

    @Test
    public void testDoesSiteHaveKeys_EmptyKeys() {
        when(s3KeyProvider.getAll()).thenReturn(new HashMap<>());

        assertFalse(s3KeyManager.doesSiteHaveKeys(1));
    }

    @Test
    void testCountKeysForSite() {
        Map<Integer, S3Key> testKeys = new HashMap<>();
        testKeys.put(1, new S3Key(1, 1, 1000L, 900L, "key1"));
        testKeys.put(2, new S3Key(2, 1, 1100L, 1000L, "key2"));
        testKeys.put(3, new S3Key(3, 2, 1200L, 1100L, "key3"));
        testKeys.put(4, new S3Key(4, 1, 1300L, 1200L, "key4"));

        when(s3KeyProvider.getAll()).thenReturn(testKeys);

        int countForSite1 = s3KeyManager.countKeysForSite(1);
        int countForSite2 = s3KeyManager.countKeysForSite(2);
        int countForSite3 = s3KeyManager.countKeysForSite(3);

        assertEquals(3, countForSite1);
        assertEquals(1, countForSite2);
        assertEquals(0, countForSite3);
    }

    @Test
    void testGenerateKeysForOperators() throws Exception {
        Collection<OperatorKey> operatorKeys = Arrays.asList(
                createOperatorKey("hash1", 100),
                createOperatorKey("hash2", 100),
                createOperatorKey("hash3", 200)
        );
        long keyActivateInterval = 3600; // 1 hour
        int keyCountPerSite = 3;

        Map<Integer, S3Key> existingKeys = new HashMap<>();
        existingKeys.put(1, new S3Key(1, 100, 1000L, 900L, "existingKey1"));
        when(s3KeyProvider.getAll()).thenReturn(existingKeys);

        when(keyGenerator.generateRandomKeyString(32)).thenReturn("generatedSecret");

        s3KeyManager.generateKeysForOperators(operatorKeys, keyActivateInterval, keyCountPerSite);

        verify(s3KeyProvider, times(1)).loadContent();

        ArgumentCaptor<Map<Integer, S3Key>> mapCaptor = ArgumentCaptor.forClass(Map.class);
        // 6 keys needed - 1 existed keys = 5 new keys
        verify(s3KeyStoreWriter, times(5)).upload(mapCaptor.capture(), isNull());
    }

    @Test
    void testGenerateKeysForOperators_NoNewKeysNeeded() throws Exception {
        Collection<OperatorKey> operatorKeys = Collections.singletonList(
                createOperatorKey("hash1", 100)
        );
        long keyActivateInterval = 3600;
        int keyCountPerSite = 3;

        Map<Integer, S3Key> existingKeys = new HashMap<>();
        existingKeys.put(1, new S3Key(1, 100, 1000L, 900L, "existingKey1"));
        existingKeys.put(2, new S3Key(2, 100, 2000L, 1900L, "existingKey2"));
        existingKeys.put(3, new S3Key(3, 100, 3000L, 2900L, "existingKey3"));
        when(s3KeyProvider.getAll()).thenReturn(existingKeys);

        s3KeyManager.generateKeysForOperators(operatorKeys, keyActivateInterval, keyCountPerSite);

        verify(s3KeyStoreWriter, never()).upload(any(), any());
    }

    @Test
    void testGenerateKeysForOperators_EmptyOperatorKeys() {
        Collection<OperatorKey> operatorKeys = Collections.emptyList();
        long keyActivateInterval = 3600;
        int keyCountPerSite = 3;

        assertThrows(IllegalArgumentException.class, () ->
                s3KeyManager.generateKeysForOperators(operatorKeys, keyActivateInterval, keyCountPerSite)
        );
    }

    @Test
    void testGenerateKeysForOperators_InvalidKeyActivateInterval() {
        Collection<OperatorKey> operatorKeys = Collections.singletonList(
                createOperatorKey("hash1", 100)
        );
        long keyActivateInterval = 0;
        int keyCountPerSite = 3;

        assertThrows(IllegalArgumentException.class, () ->
                s3KeyManager.generateKeysForOperators(operatorKeys, keyActivateInterval, keyCountPerSite)
        );
    }

    @Test
    void testGenerateKeysForOperators_InvalidKeyCountPerSite() {
        Collection<OperatorKey> operatorKeys = Collections.singletonList(
                createOperatorKey("hash1", 100)
        );
        long keyActivateInterval = 3600;
        int keyCountPerSite = 0;

        assertThrows(IllegalArgumentException.class, () ->
                s3KeyManager.generateKeysForOperators(operatorKeys, keyActivateInterval, keyCountPerSite)
        );
    }

    @Test
    void testGenerateKeysForOperators_MultipleSitesWithVaryingExistingKeys() throws Exception {
        Collection<OperatorKey> operatorKeys = Arrays.asList(
                createOperatorKey("hash1", 100),
                createOperatorKey("hash2", 200),
                createOperatorKey("hash3", 300)
        );
        long keyActivateInterval = 3600;
        int keyCountPerSite = 3;

        Map<Integer, S3Key> existingKeys = new HashMap<>();
        existingKeys.put(1, new S3Key(1, 100, 1000L, 900L, "existingKey1"));
        existingKeys.put(2, new S3Key(2, 200, 2000L, 1900L, "existingKey2"));
        existingKeys.put(3, new S3Key(3, 200, 3000L, 2900L, "existingKey3"));
        when(s3KeyProvider.getAll()).thenReturn(existingKeys);

        when(keyGenerator.generateRandomKeyString(32)).thenReturn("generatedSecret");

        s3KeyManager.generateKeysForOperators(operatorKeys, keyActivateInterval, keyCountPerSite);

        ArgumentCaptor<Map<Integer, S3Key>> mapCaptor = ArgumentCaptor.forClass(Map.class);
        // 9 keys needed - 3 existed keys = 6 new keys
        verify(s3KeyStoreWriter, times(6)).upload(mapCaptor.capture(), isNull());
    }

    private OperatorKey createOperatorKey(String keyHash, int siteId) {
        return new OperatorKey(
                keyHash,
                "salt",
                "name",
                "contact",
                "protocol",
                System.currentTimeMillis(),
                false,
                siteId,
                Collections.emptySet(),
                null,
                "keyId"
        );
    }
}
