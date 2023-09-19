package com.uid2.admin.store.writer;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.auth.AdminKeyset;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.reader.RotatingAdminKeysetStore;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.mocks.FileStorageMock;
import com.uid2.admin.vertx.JsonUtil;
import com.uid2.shared.cloud.InMemoryStorageMock;
import com.uid2.shared.model.ClientType;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.scope.GlobalScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class AdminKeysetWriterTest {
    private static final Instant NOW = Instant.now();
    private static final String ROOT_DIR = "this-test-data-type";
    private static final String METADATA_FILE_NAME = "test-metadata.json";
    private static final CloudPath GLOBAL_METADATA_PATH = new CloudPath(ROOT_DIR).resolve(METADATA_FILE_NAME);
    private static final GlobalScope GLOBAL_SCOPE = new GlobalScope(GLOBAL_METADATA_PATH);
    private static final ObjectWriter JSON_WRITER = JsonUtil.createJsonWriter();

    @Mock private Clock clock;
    @Mock private VersionGenerator versionGenerator;

    private FileManager fileManager;
    private RotatingAdminKeysetStore globalStore;

    @BeforeEach
    public void setup() {
        InMemoryStorageMock cloudStorage = new InMemoryStorageMock();
        fileManager = new FileManager(cloudStorage, new FileStorageMock(cloudStorage));
        globalStore = new RotatingAdminKeysetStore(cloudStorage, GLOBAL_SCOPE);
    }

    @Test
    public void uploadsClients() throws Exception {
        Map<Integer, AdminKeyset> keysets = Map.of(
                1, new AdminKeyset(1, 5, "test", Set.of(1, 2, 3), NOW.getEpochSecond(), true, true, Set.of(ClientType.DSP)),
                2, new AdminKeyset(2, 6, "test", Set.of(1, 3), NOW.getEpochSecond(), true, true, new HashSet<>())
        );

        AdminKeysetWriter writer = new AdminKeysetWriter(globalStore, fileManager, JSON_WRITER, versionGenerator, clock, GLOBAL_SCOPE);
        writer.upload(keysets, null);
        Map<Integer, AdminKeyset> actual = globalStore.getAll();

        Map<Integer, AdminKeyset> expected = Map.of(
                1, new AdminKeyset(1, 5, "test", Set.of(1, 2, 3), NOW.getEpochSecond(), true, true, Set.of(ClientType.DSP)),
                2, new AdminKeyset(2, 6, "test", Set.of(1, 3), NOW.getEpochSecond(), true, true, new HashSet<>())
        );
        assertThat(actual).containsAllEntriesOf(expected);
    }
}
