package com.uid2.admin.managers;

import ch.qos.logback.classic.Logger;
import com.uid2.admin.store.writer.S3KeyStoreWriter;
import com.uid2.shared.model.S3Key;
import com.uid2.shared.secret.IKeyGenerator;
import com.uid2.shared.store.reader.RotatingS3KeyProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        s3KeyManager = new S3KeyManager(s3KeyProvider, s3KeyStoreWriter, keyGenerator);
    }

    @Test
    void testGenerateS3Key() throws Exception {
        when(keyGenerator.generateRandomKeyString(32)).thenReturn("randomKeyString");

        S3Key s3Key = s3KeyManager.generateS3Key(1, 1000L, 2000L);
        System.out.println(s3Key);

        assertNotNull(s3Key);
        assertEquals(1, s3Key.getSiteId());
        assertEquals(1000L, s3Key.getActivated());
        assertEquals(2000L, s3Key.getCreated());
        assertEquals("randomKeyString", s3Key.getSecret());
    }

    @Test
    void testAddS3Key() throws Exception {
        S3Key s3Key = new S3Key();
        s3Key.setId(1);
        s3Key.setSiteId(1);
        s3Key.setActivated(1000L);
        s3Key.setCreated(2000L);
        s3Key.setSecret("randomKeyString");

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
    void testGetNextKeyId() {
        Map<Integer, S3Key> existingKeys = new HashMap<>();
        existingKeys.put(1, new S3Key());
        when(s3KeyProvider.getAll()).thenReturn(existingKeys);

        int nextKeyId = s3KeyManager.getNextKeyId();

        assertEquals(2, nextKeyId);
    }

    @Test
    void testGetS3Key() {
        S3Key s3Key = new S3Key();
        s3Key.setId(1);
        Map<Integer, S3Key> existingKeys = new HashMap<>();
        existingKeys.put(1, s3Key);
        when(s3KeyProvider.getAll()).thenReturn(existingKeys);

        S3Key result = s3KeyManager.getS3Key(1);

        assertEquals(s3Key, result);
    }

    @Test
    void testGetAllS3Keys() {
        Map<Integer, S3Key> existingKeys = new HashMap<>();
        when(s3KeyProvider.getAll()).thenReturn(existingKeys);

        Map<Integer, S3Key> result = s3KeyManager.getAllS3Keys();

        assertEquals(existingKeys, result);
    }

    @Test
    void testAddOrUpdateS3Key() throws Exception {
        S3Key s3Key = new S3Key();
        s3Key.setId(1);
        s3Key.setSiteId(1);
        s3Key.setActivated(1000L);
        s3Key.setCreated(2000L);
        s3Key.setSecret("randomKeyString");

        Map<Integer, S3Key> existingKeys = new HashMap<>();
        when(s3KeyProvider.getAll()).thenReturn(existingKeys);

        s3KeyManager.addOrUpdateS3Key(s3Key);

        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(s3KeyStoreWriter).upload(captor.capture(), isNull());

        Map<Integer, S3Key> capturedKeys = captor.getValue();
        assertEquals(1, capturedKeys.size());
        assertEquals(s3Key, capturedKeys.get(1));
    }
}

