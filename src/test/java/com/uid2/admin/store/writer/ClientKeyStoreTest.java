package com.uid2.admin.store.writer;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableList;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.mocks.FileStorageMock;
import com.uid2.admin.vertx.JsonUtil;
import com.uid2.shared.auth.ClientKey;
import com.uid2.shared.auth.Role;
import com.uid2.shared.cloud.InMemoryStorageMock;
import com.uid2.shared.secret.KeyHashResult;
import com.uid2.shared.secret.KeyHasher;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.reader.RotatingClientKeyProvider;
import com.uid2.shared.store.scope.GlobalScope;
import com.uid2.shared.store.scope.SiteScope;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ClientKeyStoreTest {
    private static final Instant NOW = Instant.now();
    private static final String ROOT_DIR = "this-test-data-type";
    private static final String METADATA_FILE_NAME = "test-metadata.json";
    private static final CloudPath GLOBAL_METADATA_PATH = new CloudPath(ROOT_DIR).resolve(METADATA_FILE_NAME);
    private static final GlobalScope GLOBAL_SCOPE = new GlobalScope(GLOBAL_METADATA_PATH);
    private static final ObjectWriter JSON_WRITER = JsonUtil.createJsonWriter();
    private static final KeyHasher KEY_HASHER = new KeyHasher();

    @Mock private Clock clock;
    @Mock private VersionGenerator versionGenerator;

    private InMemoryStorageMock cloudStorage;
    private FileManager fileManager;
    private RotatingClientKeyProvider globalStore;
    private List<ClientKey> oneClient;
    private List<ClientKey> anotherClient;

    @BeforeEach
    public void setup() {
        cloudStorage = new InMemoryStorageMock();
        fileManager = new FileManager(cloudStorage, new FileStorageMock(cloudStorage));
        globalStore = new RotatingClientKeyProvider(cloudStorage, GLOBAL_SCOPE);
        oneClient = generateOneClient("1");
        anotherClient = generateOneClient("2");
    }

    @Nested
    public class WithGlobalScope {
        @Test
        public void uploadsClients() throws Exception {
            ClientKeyStoreWriter writer = new ClientKeyStoreWriter(globalStore, fileManager, JSON_WRITER, versionGenerator, clock, GLOBAL_SCOPE);

            writer.upload(oneClient, null);

            Collection<ClientKey> actual = globalStore.getAll();
            assertThat(actual).containsExactlyElementsOf(oneClient);
        }

        @Test
        public void overridesWithNewDataOnSubsequentUploads() throws Exception {
            ClientKeyStoreWriter writer = new ClientKeyStoreWriter(globalStore, fileManager, JSON_WRITER, versionGenerator, clock, GLOBAL_SCOPE);

            writer.upload(oneClient, null);
            writer.upload(anotherClient, null);

            Collection<ClientKey> actual = globalStore.getAll();
            assertThat(actual).containsExactlyElementsOf(anotherClient);
        }

        @Test
        public void doesNotBackUpOldData() throws Exception {
            Long now = 1L; // seconds since epoch
            when(clock.getEpochSecond()).thenReturn(now);

            ClientKeyStoreWriter writer = new ClientKeyStoreWriter(globalStore, fileManager, JSON_WRITER, versionGenerator, clock, GLOBAL_SCOPE);

            writer.upload(oneClient, null);
            writer.upload(anotherClient, null);

            List<String> files = cloudStorage.list(ROOT_DIR);
            String datedBackup = "this-test-data-type/clients.json." + now + ".bak";
            String latestBackup = "this-test-data-type/clients.json.bak";
            assertThat(files).doesNotContain(datedBackup, latestBackup);

            String metaDataFile = "this-test-data-type/test-metadata.json";
            String clientFile = "this-test-data-type/clients.json";
            assertThat(files).contains(metaDataFile, clientFile);
        }

        @Test
        public void assignsNewVersionOnEveryWrite() throws Exception {
            Long now = 1L; // seconds since epoch
            when(clock.getEpochSecond()).thenReturn(now);

            ClientKeyStoreWriter writer = new ClientKeyStoreWriter(globalStore, fileManager, JSON_WRITER, versionGenerator, clock, GLOBAL_SCOPE);

            when(versionGenerator.getVersion()).thenReturn(10L);
            writer.upload(oneClient, null);
            JsonObject metadata1 = globalStore.getMetadata();
            assertThat(metadata1.getLong("version")).isEqualTo(10L);

            when(versionGenerator.getVersion()).thenReturn(11L);
            writer.upload(anotherClient, null);
            JsonObject metadata2 = globalStore.getMetadata();
            assertThat(metadata2.getLong("version")).isEqualTo(11L);
        }

        @Test
        public void savesGlobalFilesToCorrectLocation() throws Exception {
            ClientKeyStoreWriter writer = new ClientKeyStoreWriter(globalStore, fileManager, JSON_WRITER, versionGenerator, clock, GLOBAL_SCOPE);

            writer.upload(oneClient, null);

            List<String> files = cloudStorage.list(ROOT_DIR);
            String dataFile = ROOT_DIR + "/clients.json";
            String metaFile = ROOT_DIR + "/" + METADATA_FILE_NAME;
            assertThat(files).contains(dataFile, metaFile);
        }
    }

    @Nested
    public class WithSiteScope {
        private static final int SCOPED_SITE_ID = 5;
        private final SiteScope siteScope = new SiteScope(GLOBAL_METADATA_PATH, SCOPED_SITE_ID);

        private RotatingClientKeyProvider clientStore;

        @BeforeEach
        public void setup() {
            clientStore = new RotatingClientKeyProvider(cloudStorage, siteScope);
        }

        @Test
        public void doesNotWriteToGlobalScope() throws Exception {
            ClientKeyStoreWriter globalWriter = new ClientKeyStoreWriter(globalStore, fileManager, JSON_WRITER, versionGenerator, clock, GLOBAL_SCOPE);
            globalWriter.upload(Collections.emptyList(), null);

            ClientKeyStoreWriter clientWriter = new ClientKeyStoreWriter(clientStore, fileManager, JSON_WRITER, versionGenerator, clock, siteScope);
            clientWriter.upload(oneClient, null);

            Collection<ClientKey> actual = globalStore.getAll();
            assertThat(actual).isEmpty();
        }

        @Test
        public void writesToSiteScope() throws Exception {
            ClientKeyStoreWriter clientWriter = new ClientKeyStoreWriter(clientStore, fileManager, JSON_WRITER, versionGenerator, clock, siteScope);

            clientWriter.upload(oneClient, null);

            ClientKey actual = clientStore.getClientKey(oneClient.get(0).getKey());
            assertThat(actual).isEqualTo(oneClient.get(0));
        }

        @Test
        public void writingToMultipleSiteScopesDoesntOverwrite() throws Exception {
            ClientKeyStoreWriter clientWriter = new ClientKeyStoreWriter(clientStore, fileManager, JSON_WRITER, versionGenerator, clock, siteScope);
            clientWriter.upload(oneClient, null);

            int siteInScope2 = 6;
            SiteScope scope2 = new SiteScope(GLOBAL_METADATA_PATH, siteInScope2);
            RotatingClientKeyProvider siteStore2 = new RotatingClientKeyProvider(cloudStorage, scope2);
            ClientKeyStoreWriter siteWriter2 = new ClientKeyStoreWriter(siteStore2, fileManager, JSON_WRITER, versionGenerator, clock, scope2);
            siteWriter2.upload(anotherClient, null);

            Collection<ClientKey> actual1 = clientStore.getAll();
            assertThat(actual1).containsExactlyElementsOf(oneClient);

            Collection<ClientKey> actual2 = siteStore2.getAll();
            assertThat(actual2).containsExactlyElementsOf(anotherClient);
        }

        @Test
        public void savesClientFilesToCorrectLocation() throws Exception {
            ClientKeyStoreWriter clientWriter = new ClientKeyStoreWriter(clientStore, fileManager, JSON_WRITER, versionGenerator, clock, siteScope);
            clientWriter.upload(oneClient, null);

            String scopedSiteDir = ROOT_DIR + "/site/" + SCOPED_SITE_ID;
            List<String> files = cloudStorage.list(scopedSiteDir);
            String dataFile = scopedSiteDir + "/clients.json";
            String metaFile = scopedSiteDir + "/" + METADATA_FILE_NAME;
            assertThat(files).contains(dataFile, metaFile);
        }
    }

    private List<ClientKey> generateOneClient(String suffix) {
        KeyHashResult result = KEY_HASHER.hashKey("key" + suffix);
        ClientKey key = new ClientKey(
                "key" + suffix,
                result.getHash(),
                result.getSalt(),
                "secret" + suffix,
                "name" + suffix,
                NOW,
                Set.of(Role.GENERATOR),
                5
        );
        return ImmutableList.of(key);
    }
}
