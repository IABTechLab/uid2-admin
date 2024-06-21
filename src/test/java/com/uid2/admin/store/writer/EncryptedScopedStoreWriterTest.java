package com.uid2.admin.store.writer;

import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.FileName;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.shared.encryption.AesGcm;
import com.uid2.shared.encryption.Random;
import com.uid2.shared.model.S3Key;
import com.uid2.shared.store.reader.StoreReader;
import com.uid2.shared.store.scope.StoreScope;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EncryptedScopedStoreWriterTest {
    //this is a very big and abstract test, need to break it down and test more in detail
    private EncryptedScopedStoreWriter encryptedScopedStoreWriter;
    private S3Key encryptionKey;

    @BeforeEach
    void setUp() {
        StoreReader<Map<Integer, S3Key>> provider = mock(StoreReader.class);
        FileManager fileManager = mock(FileManager.class);
        VersionGenerator versionGenerator = mock(VersionGenerator.class);
        Clock clock = mock(Clock.class);
        StoreScope scope = mock(StoreScope.class);

        // Generate a valid 32-byte AES key
        byte[] keyBytes = Random.getRandomKeyBytes();
        String base64Key = Base64.getEncoder().encodeToString(keyBytes);
        encryptionKey = new S3Key(1, 123, 0, 0, base64Key);

        FileName dataFile = new FileName("s3encryption_keys", ".json");
        String dataType = "s3encryption_keys";
        encryptedScopedStoreWriter = spy(new EncryptedScopedStoreWriter(provider, fileManager, versionGenerator, clock, scope, dataFile, dataType));
    }

    @Test
    void testUploadWithEncryptionKey() throws Exception {
        String data = "test data";
        JsonObject extraMeta = new JsonObject();

        // Print initial data and extraMeta
        System.out.println("Initial Data: " + data);
        System.out.println("Extra Meta: " + extraMeta.encodePrettily());

        // Print the encryption key information
        System.out.println("Encryption Key (Base64): " + encryptionKey.getSecret());

        // Mock the encryption process
        byte[] secret = Base64.getDecoder().decode(encryptionKey.getSecret());
        System.out.println("Decoded Secret (Hex): " + bytesToHex(secret));

        byte[] encryptedPayload = AesGcm.encrypt(data.getBytes(StandardCharsets.UTF_8), secret);
        System.out.println("Encrypted Payload (Base64): " + Base64.getEncoder().encodeToString(encryptedPayload));

        JsonObject expectedJson = new JsonObject()
                .put("key_id", encryptionKey.getId())
                .put("encryption_version", "1.0");

        // Print the expected JSON
        System.out.println("Expected JSON: " + expectedJson.encodePrettily());

        // Mock the upload process in FileManager
        doNothing().when(encryptedScopedStoreWriter).upload(anyString(), any(JsonObject.class));

        // Capture the arguments passed to the upload method of the superclass
        ArgumentCaptor<String> dataCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<JsonObject> metaCaptor = ArgumentCaptor.forClass(JsonObject.class);

        // Call the uploadWithEncryptionKey method
        encryptedScopedStoreWriter.uploadWithEncryptionKey(data, extraMeta, encryptionKey);

        // Verify that the superclass's upload method was called with the correct arguments
        verify(encryptedScopedStoreWriter, times(1)).upload(dataCaptor.capture(), metaCaptor.capture());

        // Print the captured arguments
        System.out.println("Captured Data: " + dataCaptor.getValue());
        System.out.println("Captured Meta: " + metaCaptor.getValue().encodePrettily());

        // Verify the captured arguments
        JsonObject actualJson = new JsonObject(dataCaptor.getValue());
        assertEquals(expectedJson.getString("key_id"), actualJson.getString("key_id"));
        assertEquals(expectedJson.getString("encryption_version"), actualJson.getString("encryption_version"));
        assertTrue(actualJson.containsKey("encrypted_payload"));
        assertFalse(actualJson.getString("encrypted_payload").isEmpty());
        assertEquals(extraMeta, metaCaptor.getValue());
    }

    // Helper method to convert bytes to hex string
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            hexString.append(String.format("%02X", b));
        }
        return hexString.toString();
    }
}
