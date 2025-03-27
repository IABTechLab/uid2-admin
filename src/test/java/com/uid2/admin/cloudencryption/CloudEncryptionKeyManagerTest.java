package com.uid2.admin.cloudencryption;

import com.uid2.admin.store.writer.CloudEncryptionKeyStoreWriter;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.model.CloudEncryptionKey;
import com.uid2.shared.store.reader.RotatingCloudEncryptionKeyProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CloudEncryptionKeyManagerTest {
    private RotatingCloudEncryptionKeyProvider cloudEncryptionKeyProvider;
    private CloudEncryptionKeyStoreWriter cloudEncryptionKeyStoreWriter;
    private CloudSecretGenerator keyGenerator;
    private CloudEncryptionKeyManager cloudEncryptionKeyManager;

    private final long keyActivateInterval = 3600; // 1 hour
    private final int keyCountPerSite = 3;
    private final int siteId = 1;

    @BeforeEach
    void setUp() {
        cloudEncryptionKeyProvider = mock(RotatingCloudEncryptionKeyProvider.class);
        cloudEncryptionKeyStoreWriter = mock(CloudEncryptionKeyStoreWriter.class);
        keyGenerator = mock(CloudSecretGenerator.class);
        cloudEncryptionKeyManager = new CloudEncryptionKeyManager(cloudEncryptionKeyProvider, cloudEncryptionKeyStoreWriter, keyGenerator);
    }

    @Test
    void testGenerateCloudEncryptionKey() throws Exception {
        when(keyGenerator.generate()).thenReturn("randomKeyString");

        CloudEncryptionKey cloudEncryptionKey = cloudEncryptionKeyManager.generateCloudEncryptionKey(siteId, 1000L, 2000L);

        assertNotNull(cloudEncryptionKey);
        assertEquals(siteId, cloudEncryptionKey.getSiteId());
        assertEquals(1000L, cloudEncryptionKey.getActivates());
        assertEquals(2000L, cloudEncryptionKey.getCreated());
        assertEquals("randomKeyString", cloudEncryptionKey.getSecret());
    }

    @Test
    void testAddCloudEncryptionKeyToEmpty() throws Exception {
        CloudEncryptionKey cloudEncryptionKey = new CloudEncryptionKey(1, siteId, 1000L, 2000L, "randomKeyString");

        Map<Integer, CloudEncryptionKey> existingKeys = new HashMap<>();
        when(cloudEncryptionKeyProvider.getAll()).thenReturn(existingKeys);

        cloudEncryptionKeyManager.addCloudEncryptionKey(cloudEncryptionKey);

        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(cloudEncryptionKeyStoreWriter).upload(captor.capture(), isNull());

        Map<Integer, CloudEncryptionKey> capturedKeys = captor.getValue();
        assertEquals(1, capturedKeys.size());
        assertEquals(cloudEncryptionKey, capturedKeys.get(1));
    }

    @Test
    void testAddCloudEncryptionKeyToExisting() throws Exception {
        CloudEncryptionKey cloudEncryptionKey = new CloudEncryptionKey(3, siteId, 1000L, 2000L, "randomKeyString");

        Map<Integer, CloudEncryptionKey> existingKeys = new HashMap<>();
        CloudEncryptionKey existingKey1 = new CloudEncryptionKey(1, siteId, 500L, 1500L, "existingSecret1");
        CloudEncryptionKey existingKey2 = new CloudEncryptionKey(2, siteId, 600L, 1600L, "existingSecret2");
        existingKeys.put(1, existingKey1);
        existingKeys.put(2, existingKey2);

        when(cloudEncryptionKeyProvider.getAll()).thenReturn(existingKeys);

        cloudEncryptionKeyManager.addCloudEncryptionKey(cloudEncryptionKey);

        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(cloudEncryptionKeyStoreWriter).upload(captor.capture(), isNull());

        Map<Integer, CloudEncryptionKey> capturedKeys = captor.getValue();

        assertEquals(3, capturedKeys.size());
        assertEquals(existingKey1, capturedKeys.get(1));
        assertEquals(existingKey2, capturedKeys.get(2));
        assertEquals(cloudEncryptionKey, capturedKeys.get(3));
    }

    @Test
    void testGetNextKeyId() {
        Map<Integer, CloudEncryptionKey> existingKeys = new HashMap<>();
        existingKeys.put(1, new CloudEncryptionKey(1, siteId, 500L, 1500L, "existingSecret1"));
        when(cloudEncryptionKeyProvider.getAll()).thenReturn(existingKeys);

        int nextKeyId = cloudEncryptionKeyManager.getNextKeyId();

        assertEquals(2, nextKeyId);
    }

    @Test
    void testAddCloudEncryptionKey() throws Exception {
        CloudEncryptionKey cloudEncryptionKey = new CloudEncryptionKey(1, siteId, 1000L, 2000L, "randomKeyString");

        Map<Integer, CloudEncryptionKey> existingKeys = new HashMap<>();
        when(cloudEncryptionKeyProvider.getAll()).thenReturn(existingKeys);

        cloudEncryptionKeyManager.addCloudEncryptionKey(cloudEncryptionKey);

        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(cloudEncryptionKeyStoreWriter).upload(captor.capture(), isNull());

        Map<Integer, CloudEncryptionKey> capturedKeys = captor.getValue();
        assertEquals(1, capturedKeys.size());
        assertEquals(cloudEncryptionKey, capturedKeys.get(1));
    }

    @Test
    void testCountKeysForSite() {
        Map<Integer, CloudEncryptionKey> testKeys = new HashMap<>();
        testKeys.put(1, new CloudEncryptionKey(1, 1, 1000L, 900L, "key1"));
        testKeys.put(2, new CloudEncryptionKey(2, 1, 1100L, 1000L, "key2"));
        testKeys.put(3, new CloudEncryptionKey(3, 2, 1200L, 1100L, "key3"));
        testKeys.put(4, new CloudEncryptionKey(4, 1, 1300L, 1200L, "key4"));

        when(cloudEncryptionKeyProvider.getAll()).thenReturn(testKeys);

        int countForSite1 = cloudEncryptionKeyManager.countKeysForSite(1);
        int countForSite2 = cloudEncryptionKeyManager.countKeysForSite(2);
        int countForSite3 = cloudEncryptionKeyManager.countKeysForSite(3);

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

        Map<Integer, CloudEncryptionKey> existingKeys = new HashMap<>();
        existingKeys.put(1, new CloudEncryptionKey(1, 100, 1000L, 900L, "existingKey1"));
        when(cloudEncryptionKeyProvider.getAll()).thenReturn(existingKeys);

        when(keyGenerator.generate()).thenReturn("generatedSecret");

        cloudEncryptionKeyManager.generateKeysForOperators(operatorKeys, keyActivateInterval, keyCountPerSite);

        verify(cloudEncryptionKeyProvider, times(1)).loadContent();

        ArgumentCaptor<Map<Integer, CloudEncryptionKey>> mapCaptor = ArgumentCaptor.forClass(Map.class);
        // 6 keys needed - 1 existed keys = 5 new keys
        verify(cloudEncryptionKeyStoreWriter, times(5)).upload(mapCaptor.capture(), isNull());
    }

    @Test
    void testGenerateKeysForOperators_NoNewKeysNeeded() throws Exception {
        Collection<OperatorKey> operatorKeys = Collections.singletonList(
                createOperatorKey("hash1", 100)
        );

        Map<Integer, CloudEncryptionKey> existingKeys = new HashMap<>();
        existingKeys.put(1, new CloudEncryptionKey(1, 100, 1000L, 900L, "existingKey1"));
        existingKeys.put(2, new CloudEncryptionKey(2, 100, 2000L, 1900L, "existingKey2"));
        existingKeys.put(3, new CloudEncryptionKey(3, 100, 3000L, 2900L, "existingKey3"));
        when(cloudEncryptionKeyProvider.getAll()).thenReturn(existingKeys);

        cloudEncryptionKeyManager.generateKeysForOperators(operatorKeys, keyActivateInterval, keyCountPerSite);

        verify(cloudEncryptionKeyStoreWriter, never()).upload(any(), any());
    }

    @Test
    void testGenerateKeysForOperators_EmptyOperatorKeys() {
        Collection<OperatorKey> operatorKeys = Collections.emptyList();

        assertThrows(IllegalArgumentException.class, () ->
                cloudEncryptionKeyManager.generateKeysForOperators(operatorKeys, keyActivateInterval, keyCountPerSite)
        );
    }

    @Test
    void testGenerateKeysForOperators_InvalidKeyActivateInterval() {
        Collection<OperatorKey> operatorKeys = Collections.singletonList(
                createOperatorKey("hash1", 100)
        );
        long keyActivateInterval = 0;

        assertThrows(IllegalArgumentException.class, () ->
                cloudEncryptionKeyManager.generateKeysForOperators(operatorKeys, keyActivateInterval, keyCountPerSite)
        );
    }

    @Test
    void testGenerateKeysForOperators_InvalidKeyCountPerSite() {
        Collection<OperatorKey> operatorKeys = Collections.singletonList(
                createOperatorKey("hash1", 100)
        );
        int keyCountPerSite = 0;

        assertThrows(IllegalArgumentException.class, () ->
                cloudEncryptionKeyManager.generateKeysForOperators(operatorKeys, keyActivateInterval, keyCountPerSite)
        );
    }

    @Test
    void testGenerateKeysForOperators_MultipleSitesWithVaryingExistingKeys() throws Exception {
        Collection<OperatorKey> operatorKeys = Arrays.asList(
                createOperatorKey("hash1", 100),
                createOperatorKey("hash2", 200),
                createOperatorKey("hash3", 300)
        );

        Map<Integer, CloudEncryptionKey> existingKeys = new HashMap<>();
        existingKeys.put(1, new CloudEncryptionKey(1, 100, 1000L, 900L, "existingKey1"));
        existingKeys.put(2, new CloudEncryptionKey(2, 200, 2000L, 1900L, "existingKey2"));
        existingKeys.put(3, new CloudEncryptionKey(3, 200, 3000L, 2900L, "existingKey3"));
        when(cloudEncryptionKeyProvider.getAll()).thenReturn(existingKeys);

        when(keyGenerator.generate()).thenReturn("generatedSecret");

        cloudEncryptionKeyManager.generateKeysForOperators(operatorKeys, keyActivateInterval, keyCountPerSite);

        ArgumentCaptor<Map<Integer, CloudEncryptionKey>> mapCaptor = ArgumentCaptor.forClass(Map.class);
        // 9 keys needed - 3 existed keys = 6 new keys
        verify(cloudEncryptionKeyStoreWriter, times(6)).upload(mapCaptor.capture(), isNull());
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
