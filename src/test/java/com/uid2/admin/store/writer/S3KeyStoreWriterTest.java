package com.uid2.admin.store.writer;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.mocks.FileStorageMock;
import com.uid2.shared.model.S3Key;
import com.uid2.shared.cloud.InMemoryStorageMock;
import com.uid2.shared.store.reader.RotatingS3KeyProvider;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.scope.GlobalScope;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.uid2.admin.vertx.JsonUtil.createJsonWriter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;

public class S3KeyStoreWriterTest {
    private Clock clock;
    private VersionGenerator versionGenerator;
    private RotatingS3KeyProvider globalStore;
    private InMemoryStorageMock cloudStorage;
    private FileManager fileManager;
    private S3KeyStoreWriter s3KeyStoreWriter;

    private final Map<Integer, S3Key> s3Keys = Map.of(
            1, new S3Key(1, 123, 1687635529, 1687808329, "S3keySecretByteHere1"),
            2, new S3Key(2, 123, 1687808429, 1687808329, "S3keySecretByteHere2"),
            3, new S3Key(3, 456, 1687635529, 1687808329, "S3keySecretByteHere3")
    );
    private final Map<Integer, S3Key> expected = Map.of(
            1, new S3Key(1, 123, 1687635529, 1687808329, "S3keySecretByteHere1"),
            2, new S3Key(2, 123, 1687808429, 1687808329, "S3keySecretByteHere2"),
            3, new S3Key(3, 456, 1687635529, 1687808329, "S3keySecretByteHere3")
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
        globalStore = new RotatingS3KeyProvider(cloudStorage, globalScope);
        versionGenerator = mock(VersionGenerator.class);
        clock = mock(Clock.class);
        s3KeyStoreWriter = new S3KeyStoreWriter(globalStore, fileManager, jsonWriter, versionGenerator, clock, globalScope);
    }

    @Test
    void uploadsS3Keys() throws Exception {
        JsonObject extraMeta = new JsonObject();

        s3KeyStoreWriter.upload(s3Keys, extraMeta);

        Map<Integer, S3Key> actualKeys = globalStore.getAll();
        assertThat(actualKeys).hasSize(s3Keys.size());
        assertThat(actualKeys).containsAllEntriesOf(expected);
    }
}
