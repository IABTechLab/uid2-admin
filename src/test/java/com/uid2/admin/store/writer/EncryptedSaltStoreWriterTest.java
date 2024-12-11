package com.uid2.admin.store.writer;

import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.writer.EncyptedSaltStoreWriter;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.shared.cloud.CloudStorageException;
import com.uid2.shared.cloud.TaggableCloudStorage;
import com.uid2.shared.model.CloudEncryptionKey;
import com.uid2.shared.model.SaltEntry;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.RotatingSaltProvider;
import com.uid2.shared.store.reader.RotatingCloudEncryptionKeyProvider;
import com.uid2.shared.store.scope.StoreScope;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static com.uid2.shared.util.CloudEncryptionHelpers.decryptInputStream;

public class EncryptedSaltStoreWriterTest {
    private AutoCloseable mocks;

    @Mock
    private FileManager fileManager;

    @Mock
    TaggableCloudStorage taggableCloudStorage;

    @Mock
    RotatingSaltProvider rotatingSaltProvider;

    @Mock
    RotatingCloudEncryptionKeyProvider rotatingCloudEncryptionKeyProvider;

    @Mock
    VersionGenerator versionGenerator;

    @Mock
    StoreScope storeScope;

    CloudEncryptionKey encryptionKey;

    JsonObject config;

    private final Integer siteId = 1;

    @Captor
    private ArgumentCaptor<String> pathCaptor;
    @Captor
    private ArgumentCaptor<String> cloudPathCaptor;

    @BeforeEach
    public void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        config = new JsonObject();
        config.put("salt_snapshot_location_prefix", "test");

        when(versionGenerator.getVersion()).thenReturn(1L);
        when(rotatingSaltProvider.getMetadataPath()).thenReturn("test/path/");
        when(storeScope.resolve(any())).thenReturn(new CloudPath("test/path/"));

        // Setup Cloud Encryption Keys
        byte[] keyBytes = new byte[32];
        new Random().nextBytes(keyBytes);
        String base64Key = Base64.getEncoder().encodeToString(keyBytes);
        encryptionKey = new CloudEncryptionKey(1, 1, 0, 0, base64Key);

        Map<Integer, CloudEncryptionKey> mockKeyMap = new HashMap<>();
        mockKeyMap.put(encryptionKey.getId(), encryptionKey);
        when(rotatingCloudEncryptionKeyProvider.getAll()).thenReturn(mockKeyMap);
        when(rotatingCloudEncryptionKeyProvider.getKey(1)).thenReturn(mockKeyMap.get(1));
        when(rotatingCloudEncryptionKeyProvider.getEncryptionKeyForSite(siteId)).thenReturn(encryptionKey);
    }

    private RotatingSaltProvider.SaltSnapshot makeSnapshot(Instant effective, Instant expires, int nsalts) {
        SaltEntry[] entries = new SaltEntry[nsalts];
        for (int i = 0; i < entries.length; ++i) {
            entries[i] = new SaltEntry(i, "hashed_id", effective.toEpochMilli(), "salt");
        }
        return new RotatingSaltProvider.SaltSnapshot(effective, expires, entries, "test_first_level_salt");
    }

    private void verifyFile(String filelocation, RotatingSaltProvider.SaltSnapshot snapshot) throws IOException {
        InputStream encoded = Files.newInputStream(Paths.get(filelocation));
        String contents = decryptInputStream(encoded, rotatingCloudEncryptionKeyProvider);
        SaltEntry[] entries = snapshot.getAllRotatingSalts();
        Integer idx = 0;
        for (String line : contents.split("\n")) {
            String[] entrySplit = line.split(",");
            assertEquals(entries[idx].getId(), Long.parseLong(entrySplit[0]));
            assertEquals(entries[idx].getSalt(), entrySplit[2]);
            idx++;
        }
    }

    @Test
    public void testUploadNew() throws Exception {
        RotatingSaltProvider.SaltSnapshot snapshot = makeSnapshot(Instant.now(), Instant.ofEpochMilli(Instant.now().toEpochMilli() + 10000), 100);

        when(rotatingSaltProvider.getMetadata()).thenThrow(new CloudStorageException("The specified key does not exist: AmazonS3Exception: test-core-bucket"));
        when(rotatingSaltProvider.getSnapshots()).thenReturn(null);

        when(taggableCloudStorage.list(anyString())).thenReturn(new ArrayList<>());

        EncyptedSaltStoreWriter encryptedSaltStoreWriter = new EncyptedSaltStoreWriter(config, rotatingSaltProvider,
                fileManager, taggableCloudStorage, versionGenerator, storeScope, rotatingCloudEncryptionKeyProvider, siteId);

        encryptedSaltStoreWriter.upload(snapshot);

        verify(taggableCloudStorage).upload(pathCaptor.capture(), cloudPathCaptor.capture(), any());
        assertEquals(cloudPathCaptor.getValue(), "test/path");

        verifyFile(pathCaptor.getValue(), snapshot);
    }
}
