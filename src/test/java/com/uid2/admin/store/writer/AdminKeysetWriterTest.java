package com.uid2.admin.store.writer;

import com.uid2.admin.auth.AdminKeyset;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.reader.RotatingAdminKeysetStore;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.mocks.FileStorageMock;
import com.uid2.shared.cloud.InMemoryStorageMock;
import com.uid2.shared.model.ClientType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.scope.GlobalScope;
import com.fasterxml.jackson.databind.ObjectWriter;
import static com.uid2.admin.vertx.ObjectWriterFactory.createJsonWriter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AdminKeysetWriterTest {
    private Clock clock;
    private VersionGenerator versionGenerator;
    private RotatingAdminKeysetStore globalStore;
    private InMemoryStorageMock cloudStorage;
    private FileManager fileManager;

    private final Map<Integer, AdminKeyset> keysets = Map.of(
            1, new AdminKeyset(1, 5, "test", Set.of(1, 2, 3), Instant.now().getEpochSecond(), true, true, Set.of(ClientType.DSP)),
            2, new AdminKeyset(2, 6, "test", Set.of(1, 3), Instant.now().getEpochSecond(), true, true, new HashSet<>())
    );

    private final Map<Integer, AdminKeyset> expected = Map.of(
            1, new AdminKeyset(1, 5, "test", Set.of(1, 2, 3), Instant.now().getEpochSecond(), true, true, Set.of(ClientType.DSP)),
            2, new AdminKeyset(2, 6, "test", Set.of(1, 3), Instant.now().getEpochSecond(), true, true, new HashSet<>())
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
       globalStore = new RotatingAdminKeysetStore(cloudStorage, globalScope);
       versionGenerator = mock(VersionGenerator.class);
       clock = mock(Clock.class);
    }

    @Test
    void uploadsClients() throws Exception {
        AdminKeysetWriter writer = new AdminKeysetWriter(globalStore, fileManager, jsonWriter, versionGenerator, clock, globalScope);

        writer.upload(keysets, null);

        Map<Integer, AdminKeyset> actual = globalStore.getAll();
        assertThat(actual).containsAllEntriesOf(expected);
    }
}
