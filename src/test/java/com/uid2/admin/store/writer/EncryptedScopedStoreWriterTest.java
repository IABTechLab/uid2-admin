package com.uid2.admin.store.writer;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableList;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.FileName;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.mocks.FileStorageMock;
import com.uid2.admin.vertx.JsonUtil;
import com.uid2.shared.cloud.DownloadCloudStorage;
import com.uid2.shared.cloud.InMemoryStorageMock;
import com.uid2.shared.encryption.AesGcm;
import com.uid2.shared.encryption.Random;
import com.uid2.shared.model.S3Key;
import com.uid2.shared.model.Site;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.scope.EncryptedScope;
import com.uid2.shared.store.ScopedStoreReader;
import com.uid2.shared.store.reader.IMetadataVersionedStore;
import com.uid2.shared.store.reader.RotatingS3KeyProvider;
import com.uid2.shared.store.reader.RotatingSiteStore;
import com.uid2.shared.store.reader.StoreReader;
import com.uid2.shared.store.scope.GlobalScope;
import com.uid2.shared.store.scope.SiteScope;
import com.uid2.shared.store.scope.StoreScope;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EncryptedScopedStoreWriterTest {

    private Clock clock;
    ObjectWriter jsonWriter = JsonUtil.createJsonWriter();
    private VersionGenerator versionGenerator;
    private InMemoryStorageMock cloudStorage;
    private FileManager fileManager;
    private final String sitesDir = "sites";
    private final String metadataFileName = "test-metadata.json";
    private final CloudPath rootMetadataPath = new CloudPath(sitesDir).resolve(metadataFileName);
    private final String dataType = "sites";
    private final FileName dataFile = new FileName("sites", ".json");
    private EncryptedScopedStoreWriter encryptedScopedStoreWriter;
    private S3Key encryptionKey;
    private final int testSiteId = 123;
    private RotatingS3KeyProvider s3KeyProvider;
    private IMetadataVersionedStore provider;

    @BeforeEach
    void setUp() throws Exception {
        cloudStorage = new InMemoryStorageMock();
        FileStorageMock fileStorage = new FileStorageMock(cloudStorage);
        fileManager = mock(FileManager.class);
        clock = mock(Clock.class);
        versionGenerator = mock(VersionGenerator.class);
        provider = mock(IMetadataVersionedStore.class);


        // Generate a valid 32-byte AES key
        byte[] keyBytes = Random.getRandomKeyBytes();
        String base64Key = Base64.getEncoder().encodeToString(keyBytes);
        encryptionKey = new S3Key(1, testSiteId, 0, 0, base64Key);

        EncryptedScope encryptedScope = new EncryptedScope(rootMetadataPath, testSiteId,false);

        s3KeyProvider = mock(RotatingS3KeyProvider.class);
        Map<Integer, S3Key> mockKeyMap = new HashMap<>();
        mockKeyMap.put(testSiteId, encryptionKey);
        when(s3KeyProvider.getEncryptionKeyForSite(123)).thenReturn(encryptionKey);


        // Initialize EncryptedScopedStoreWriter with the s3KeyProvider
        encryptedScopedStoreWriter = new EncryptedScopedStoreWriter(
                provider,
                fileManager,
                versionGenerator,
                clock,
                encryptedScope,
                dataFile,
                dataType,
                s3KeyProvider,
                testSiteId
        );
    }

    @Test
    void testDataIsEncryptedBeforeUpload() throws Exception {
        String testData = "Test data to be encrypted";
        JsonObject extraMeta = new JsonObject().put("test", "meta");

        doAnswer(invocation -> {
            CloudPath location = invocation.getArgument(0);
            FileName fileName = invocation.getArgument(1);
            String content = invocation.getArgument(2);

            JsonObject json = new JsonObject(content);
            assertTrue(json.containsKey("key_id"));
            assertTrue(json.containsKey("encryption_version"));
            assertTrue(json.containsKey("encrypted_payload"));

            assertNotEquals(testData, json.getString("encrypted_payload"));

            return null;
        }).when(fileManager).uploadFile(any(CloudPath.class), any(FileName.class), anyString());

        encryptedScopedStoreWriter.upload(testData, extraMeta);

        // Verify that uploadFile was called
        verify(fileManager).uploadFile(any(CloudPath.class), any(FileName.class), anyString());
    }

    @Test
    void testSuccessfulUploadAndVerifyEncryptedContent() throws Exception {
        String testData = "Test data to be encrypted";
        JsonObject extraMeta = new JsonObject().put("test", "meta");

        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        doNothing().when(fileManager).uploadFile(any(CloudPath.class), any(FileName.class), contentCaptor.capture());

        encryptedScopedStoreWriter.upload(testData, extraMeta);

        String uploadedContent = contentCaptor.getValue();
        JsonObject json = new JsonObject(uploadedContent);

        assertTrue(json.containsKey("key_id"));
        assertTrue(json.containsKey("encryption_version"));
        assertTrue(json.containsKey("encrypted_payload"));
        assertNotEquals(testData, json.getString("encrypted_payload"));

        verify(fileManager).uploadFile(any(CloudPath.class), any(FileName.class), anyString());
    }

    @Test
    void testHandlingInvalidEncryptionKey() {
        when(s3KeyProvider.getEncryptionKeyForSite(123)).thenReturn(null);

        String testData = "Test data to be encrypted";
        JsonObject extraMeta = new JsonObject().put("test", "meta");

        assertThrows(IllegalStateException.class, () -> encryptedScopedStoreWriter.upload(testData, extraMeta));
    }
}

