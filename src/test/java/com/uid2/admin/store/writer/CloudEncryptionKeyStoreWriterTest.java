package com.uid2.admin.store.writer;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.mocks.FileStorageMock;
import com.uid2.shared.model.CloudEncryptionKey;
import com.uid2.shared.cloud.InMemoryStorageMock;
import com.uid2.shared.store.reader.RotatingCloudEncryptionKeyProvider;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.scope.GlobalScope;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.uid2.admin.vertx.JsonUtil.createJsonWriter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;

public class CloudEncryptionKeyStoreWriterTest {
    private Clock clock;
    private VersionGenerator versionGenerator;
    private RotatingCloudEncryptionKeyProvider globalStore;
    private InMemoryStorageMock cloudStorage;
    private FileManager fileManager;
    private CloudEncryptionKeyStoreWriter cloudEncryptionKeyStoreWriter;

    private final Map<Integer, CloudEncryptionKey> cloudEncryptionKeys = Map.of(
            1, new CloudEncryptionKey(1, 123, 1687635529, 1687808329, "S3keySecretByteHere1"),
            2, new CloudEncryptionKey(2, 123, 1687808429, 1687808329, "S3keySecretByteHere2"),
            3, new CloudEncryptionKey(3, 456, 1687635529, 1687808329, "S3keySecretByteHere3")
    );
    private final Map<Integer, CloudEncryptionKey> expected = Map.of(
            1, new CloudEncryptionKey(1, 123, 1687635529, 1687808329, "S3keySecretByteHere1"),
            2, new CloudEncryptionKey(2, 123, 1687808429, 1687808329, "S3keySecretByteHere2"),
            3, new CloudEncryptionKey(3, 456, 1687635529, 1687808329, "S3keySecretByteHere3")
    );

    private final String rootDir = "this-test-data-type";
    private final String metadataFileName = "test-metadata.json";
    private final CloudPath globalMetadataPath = new CloudPath(rootDir).resolve(metadataFileName);
    private final GlobalScope globalScope = new GlobalScope(globalMetadataPath);
    private final ObjectWriter jsonWriter = createJsonWriter();

    @BeforeEach
    void setUp() {
        cloudStorage = new InMemoryStorageMock();
        FileStorageMock fileStorage = new FileStorageMock(cloudStorage);
        fileManager = new FileManager(cloudStorage, fileStorage);
        globalStore = new RotatingCloudEncryptionKeyProvider(cloudStorage, globalScope);
        versionGenerator = mock(VersionGenerator.class);
        clock = mock(Clock.class);
        cloudEncryptionKeyStoreWriter = new CloudEncryptionKeyStoreWriter(globalStore, fileManager, jsonWriter, versionGenerator, clock, globalScope);
    }

    @Test
    void uploadsCloudEncryptionKeys() throws Exception {
        JsonObject extraMeta = new JsonObject();

        cloudEncryptionKeyStoreWriter.upload(cloudEncryptionKeys, extraMeta);

        Map<Integer, CloudEncryptionKey> actualKeys = globalStore.getAll();
        assertThat(actualKeys).hasSize(cloudEncryptionKeys.size());
        assertThat(actualKeys).containsAllEntriesOf(expected);
    }
}