package com.uid2.admin.store.writer;

import com.google.common.collect.ImmutableList;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.mocks.FileStorageMock;
import com.uid2.shared.auth.Keyset;
import com.uid2.shared.cloud.InMemoryStorageMock;
import com.uid2.shared.store.reader.RotatingKeysetProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.scope.GlobalScope;
import com.fasterxml.jackson.databind.ObjectWriter;
import static com.uid2.admin.vertx.JsonUtil.createJsonWriter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class KeysetStoreTest {
    private Clock clock;
    private VersionGenerator versionGenerator;
    private RotatingKeysetProvider globalStore;
    private InMemoryStorageMock cloudStorage;
    private FileManager fileManager;

    private final Map<Integer, Keyset> keysets = Map.of(
            1, new Keyset(1, 5, "test", Set.of(1, 2, 3), Instant.now().getEpochSecond(), true, true),
            2, new Keyset(2, 6, "test", Set.of(1, 3), Instant.now().getEpochSecond(), true, true)
    );

    private final Map<Integer, Keyset> expected = Map.of(
            1, new Keyset(1, 5, "test", Set.of(1, 2, 3), Instant.now().getEpochSecond(), true, true),
            2, new Keyset(2, 6, "test", Set.of(1, 3), Instant.now().getEpochSecond(), true, true)
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
       globalStore = new RotatingKeysetProvider(cloudStorage, globalScope);
       versionGenerator = mock(VersionGenerator.class);
       clock = mock(Clock.class);
    }

    @Test
    void uploadsClients() throws Exception {
        KeysetStoreWriter writer = new KeysetStoreWriter(globalStore, fileManager, jsonWriter, versionGenerator, clock, globalScope);

        writer.upload(keysets, null);

        Map<Integer, Keyset> actual = globalStore.getAll();
        assertThat(actual).containsAllEntriesOf(expected);
    }
}
