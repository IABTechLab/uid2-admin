package com.uid2.admin.store.writer;

import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.shared.cloud.CloudStorageException;
import com.uid2.shared.cloud.TaggableCloudStorage;
import com.uid2.shared.model.CloudEncryptionKey;
import com.uid2.shared.model.SaltEntry;
import com.uid2.shared.model.SaltEntry.KeyMaterial;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.salt.RotatingSaltProvider;
import com.uid2.shared.store.reader.RotatingCloudEncryptionKeyProvider;
import com.uid2.shared.store.scope.StoreScope;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static java.lang.Long.parseLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.uid2.shared.util.CloudEncryptionHelpers.decryptInputStream;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class EncryptedSaltStoreWriterTest {
    private static final Integer SITE_ID = 1;
    private final Instant feb26 = Instant.parse("2025-02-26T22:12:18Z");
    private final Instant feb27 = Instant.parse("2025-02-27T22:14:36Z");
    private final Instant mar23 = Instant.parse("2025-03-23T14:52:08Z");
    private final Instant mar25 = Instant.parse("2025-03-25T14:52:08Z");

    @Mock
    private FileManager fileManager;
    @Mock
    private TaggableCloudStorage taggableCloudStorage;
    @Mock
    private RotatingSaltProvider rotatingSaltProvider;
    @Mock
    private RotatingCloudEncryptionKeyProvider rotatingCloudEncryptionKeyProvider;
    @Mock
    private VersionGenerator versionGenerator;
    @Mock
    private StoreScope storeScope;

    @Captor
    private ArgumentCaptor<String> pathCaptor;
    @Captor
    private ArgumentCaptor<String> cloudPathCaptor;

    private JsonObject config;

    @BeforeEach
    public void setup() throws Exception {
        config = new JsonObject();
        config.put("salt_snapshot_location_prefix", "test");

        when(versionGenerator.getVersion()).thenReturn(1L);
        when(rotatingSaltProvider.getMetadataPath()).thenReturn("test/path/");
        when(storeScope.resolve(any())).thenReturn(new CloudPath("test/path/"));

        // Setup Cloud Encryption Keys
        byte[] keyBytes = new byte[32];
        new Random().nextBytes(keyBytes);
        String base64Key = Base64.getEncoder().encodeToString(keyBytes);
        CloudEncryptionKey encryptionKey = new CloudEncryptionKey(1, 1, 0, 0, base64Key);

        Map<Integer, CloudEncryptionKey> mockKeyMap = new HashMap<>();
        mockKeyMap.put(encryptionKey.getId(), encryptionKey);
        when(rotatingCloudEncryptionKeyProvider.getAll()).thenReturn(mockKeyMap);
        when(rotatingCloudEncryptionKeyProvider.getKey(1)).thenReturn(mockKeyMap.get(1));
        when(rotatingCloudEncryptionKeyProvider.getEncryptionKeyForSite(SITE_ID)).thenReturn(encryptionKey);
    }

    @Test
    public void testUploadNew() throws Exception {
        RotatingSaltProvider.SaltSnapshot olderSnapshot = makeSnapshot(
                feb26,
                Instant.now().plus(Duration.ofSeconds(90)),
                100
        );

        RotatingSaltProvider.SaltSnapshot activeSnapshot = makeSnapshot(
                feb27,
                Instant.now().plus(Duration.ofMinutes(2)),
                10
        );


        JsonObject metadata = new JsonObject()
                .put("version", mar23.toEpochMilli())
                .put("generated", mar23.getEpochSecond())
                .put("first_level", "FIRST-LEVEL")
                .put("id_prefix", "a")
                .put("id_secret", "ID-SECRET");
        when(rotatingSaltProvider.getMetadata()).thenThrow(new CloudStorageException("The specified key does not exist: AmazonS3Exception: test-core-bucket"));
        when(rotatingSaltProvider.getSnapshots()).thenReturn(null);

        when(taggableCloudStorage.list(anyString())).thenReturn(new ArrayList<>());

        ArgumentCaptor<JsonObject> metadataCaptor = ArgumentCaptor.forClass(JsonObject.class);
        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<CloudPath> locationCaptor = ArgumentCaptor.forClass(CloudPath.class);

        EncryptedSaltStoreWriter encryptedSaltStoreWriter = new EncryptedSaltStoreWriter(config, rotatingSaltProvider,
                fileManager, taggableCloudStorage, versionGenerator, storeScope, rotatingCloudEncryptionKeyProvider, SITE_ID);

        encryptedSaltStoreWriter.upload(List.of(olderSnapshot,activeSnapshot), metadata);
        verify(fileManager).uploadMetadata(metadataCaptor.capture(), nameCaptor.capture(), locationCaptor.capture());

        // Capture the metadata
        JsonObject capturedMetadata = metadataCaptor.getValue();

        assertEquals(2, capturedMetadata.getJsonArray("salts").size(), "The 'salts' array should contain 2 items");
        assertEquals(capturedMetadata.getString("first_level"), metadata.getValue("first_level"));
        assertEquals(capturedMetadata.getString("id_prefix"), metadata.getValue("id_prefix"));
        verify(taggableCloudStorage,times(2)).upload(pathCaptor.capture(), cloudPathCaptor.capture(), any());

        assertWrittenFileEquals(pathCaptor.getValue(), activeSnapshot);
    }

    @Test
    public void testUnencryptedAndEncryptedBehavesTheSame() throws Exception {
        RotatingSaltProvider.SaltSnapshot snapshot = makeSnapshot(
                feb26,
                Instant.now().plus(Duration.ofSeconds(90)),
                100
        );

        RotatingSaltProvider.SaltSnapshot snapshot2 = makeSnapshot(
                feb27,
                Instant.now().plus(Duration.ofMinutes(2)),
                10
        );

        List<RotatingSaltProvider.SaltSnapshot> snapshots = List.of(snapshot, snapshot2);

        when(rotatingSaltProvider.getMetadata()).thenThrow(new CloudStorageException("The specified key does not exist: AmazonS3Exception: test-core-bucket"));
        when(rotatingSaltProvider.getSnapshots()).thenReturn(snapshots);
        when(taggableCloudStorage.list(anyString())).thenReturn(new ArrayList<>());

        ArgumentCaptor<JsonObject> metadataCaptor = ArgumentCaptor.forClass(JsonObject.class);
        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<CloudPath> locationCaptor = ArgumentCaptor.forClass(CloudPath.class);

        SaltStoreWriter saltStoreWriter = new SaltStoreWriter(config, rotatingSaltProvider,
                fileManager, taggableCloudStorage, versionGenerator);

        saltStoreWriter.upload(snapshot);
        verify(fileManager).uploadMetadata(metadataCaptor.capture(), nameCaptor.capture(), locationCaptor.capture());

        JsonObject capturedMetadata = metadataCaptor.getValue();
        Integer key_id = capturedMetadata.getInteger("key_id", null);
        assertNull(key_id);
        JsonArray saltsArray = capturedMetadata.getJsonArray("salts");
        assertEquals(1, saltsArray.size(), "Salts array should have exactly one entry, as other is removed in newest-effective logic");
        JsonObject salt = saltsArray.getJsonObject(0);
        assertEquals(
                feb27.toEpochMilli(),
                salt.getLong("effective"),
                "Effective timestamp should match second entry"
        );
        assertEquals(10, salt.getInteger("size"), "Size should match second entries");

        //Now sending snapshot2 to encrypted to verify that does the same.
        EncryptedSaltStoreWriter encryptedSaltStoreWriter = new EncryptedSaltStoreWriter(config, rotatingSaltProvider,
                fileManager, taggableCloudStorage, versionGenerator, storeScope, rotatingCloudEncryptionKeyProvider, SITE_ID);

        JsonObject metadata = new JsonObject()
                .put("version", mar25.toEpochMilli())
                .put("generated", mar25.getEpochSecond())
                .put("first_level", "FIRST-LEVEL")
                .put("id_prefix", "a")
                .put("id_secret", "ID-SECRET");

        encryptedSaltStoreWriter.upload(List.of(snapshot2), metadata);

        verify(fileManager,atLeastOnce()).uploadMetadata(metadataCaptor.capture(), nameCaptor.capture(), locationCaptor.capture());

        capturedMetadata = metadataCaptor.getValue();
        key_id = capturedMetadata.getInteger("key_id");
        saltsArray = capturedMetadata.getJsonArray("salts");
        salt = saltsArray.getJsonObject(0);
        assertEquals(1, key_id);
        assertEquals(
                feb27.toEpochMilli(),
                salt.getLong("effective"),
                "Effective timestamp should match second entry"
        );
        assertEquals(10, salt.getInteger("size"), "Size should match second entries");
        verify(taggableCloudStorage,atLeastOnce()).upload(pathCaptor.capture(), cloudPathCaptor.capture(), any());
    }

    private RotatingSaltProvider.SaltSnapshot makeSnapshot(Instant effective, Instant expires, int nsalts) {
        SaltEntry[] entries = new SaltEntry[nsalts];

        for (int i = 0; i < entries.length; ++i) {
            entries[i] = new SaltEntry(
                    i,
                    "hashed_id",
                    effective.toEpochMilli(),
                    "salt",
                    1000L,
                    "previous salt",
                    new KeyMaterial(1, "key 1", "key salt 1"),
                    new KeyMaterial(2, "key 2", "key salt 2")
            );
        }
        return new RotatingSaltProvider.SaltSnapshot(effective, expires, entries, "test_first_level_salt");
    }

    private void assertWrittenFileEquals(String fileLocation, RotatingSaltProvider.SaltSnapshot snapshot) throws IOException {
        InputStream encoded = Files.newInputStream(Paths.get(fileLocation));
        String contents = decryptInputStream(encoded, rotatingCloudEncryptionKeyProvider, "salts");
        SaltEntry[] entries = snapshot.getAllRotatingSalts();
        var lines = contents.split("\n");
        for (int i = 0; i < lines.length; i++) {
            var line = lines[i];
            var entry = entries[i];
            String[] fields = line.split(",");

            assertAll(
                    () -> assertEquals(entry.id(), parseLong(fields[0])),
                    () -> assertEquals(entry.lastUpdated(), parseLong(fields[1])),
                    () -> assertEquals(entry.currentSalt(), fields[2]),
                    () -> assertEquals(entry.refreshFrom(), parseLong(fields[3])),
                    () -> assertEquals(entry.previousSalt(), fields[4]),
                    () -> assertEquals(entry.currentKey().id(), parseLong(fields[5])),
                    () -> assertEquals(entry.currentKey().key(), fields[6]),
                    () -> assertEquals(entry.currentKey().salt(), fields[7]),
                    () -> assertEquals(entry.previousKey().id(), parseLong(fields[8])),
                    () -> assertEquals(entry.previousKey().key(), fields[9]),
                    () -> assertEquals(entry.previousKey().salt(), fields[10])
            );
        }
    }
}
