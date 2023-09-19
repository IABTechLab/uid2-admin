package com.uid2.admin.store.writer;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.mocks.FileStorageMock;
import com.uid2.admin.vertx.JsonUtil;
import com.uid2.shared.auth.Keyset;
import com.uid2.shared.cloud.InMemoryStorageMock;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.reader.RotatingKeysetProvider;
import com.uid2.shared.store.scope.GlobalScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class KeysetStoreTest {
    private final String rootDir = "this-test-data-type";
    private final String metadataFileName = "test-metadata.json";
    private final CloudPath globalMetadataPath = new CloudPath(rootDir).resolve(metadataFileName);
    private final GlobalScope globalScope = new GlobalScope(globalMetadataPath);
    private final ObjectWriter jsonWriter = JsonUtil.createJsonWriter();

    @Mock private Clock clock;
    @Mock private VersionGenerator versionGenerator;

    private RotatingKeysetProvider globalStore;
    private FileManager fileManager;

    @BeforeEach
    public void setup() {
        InMemoryStorageMock cloudStorage = new InMemoryStorageMock();
        FileStorageMock fileStorage = new FileStorageMock(cloudStorage);
        fileManager = new FileManager(cloudStorage, fileStorage);
        globalStore = new RotatingKeysetProvider(cloudStorage, globalScope);
    }

    @Test
    public void uploadsClients() throws Exception {
        Map<Integer, Keyset> keysets = Map.of(
                1, new Keyset(1, 5, "test1", Set.of(1, 2, 3), Instant.now().getEpochSecond(), true, true),
                2, new Keyset(2, 6, "test2", Set.of(1, 3), Instant.now().getEpochSecond(), true, true)
        );

        KeysetStoreWriter writer = new KeysetStoreWriter(globalStore, fileManager, jsonWriter, versionGenerator, clock, globalScope, true);
        writer.upload(keysets, null);
        Map<Integer, Keyset> actual = globalStore.getAll();

        Map<Integer, Keyset> expected = Map.of(
                1, new Keyset(1, 5, "test1", Set.of(1, 2, 3), Instant.now().getEpochSecond(), true, true),
                2, new Keyset(2, 6, "test2", Set.of(1, 3), Instant.now().getEpochSecond(), true, true)
        );
        assertThat(actual).containsAllEntriesOf(expected);
    }
}
