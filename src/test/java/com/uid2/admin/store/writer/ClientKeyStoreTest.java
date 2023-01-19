package com.uid2.admin.store.writer;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableList;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.mocks.FileStorageMock;
import com.uid2.shared.auth.ClientKey;
import com.uid2.shared.auth.Role;
import com.uid2.shared.store.reader.RotatingClientKeyProvider;
import com.uid2.shared.cloud.InMemoryStorageMock;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.scope.GlobalScope;
import com.uid2.shared.store.scope.SiteScope;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.uid2.admin.vertx.JsonUtil.createJsonWriter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientKeyStoreTest {

    @Nested
    class WithGlobalScope {
        @Test
        void uploadsClients() throws Exception {
            ClientKeyStoreWriter writer = new ClientKeyStoreWriter(globalStore, fileManager, jsonWriter, versionGenerator, clock, globalScope);

            writer.upload(oneClient, null);

            Collection<ClientKey> actual = globalStore.getAll();
            assertThat(actual).containsExactlyElementsOf(oneClient);
        }

        @Test
        void overridesWithNewDataOnSubsequentUploads() throws Exception {
            ClientKeyStoreWriter writer = new ClientKeyStoreWriter(globalStore, fileManager, jsonWriter, versionGenerator, clock, globalScope);

            writer.upload(oneClient, null);
            writer.upload(anotherClient, null);

            Collection<ClientKey> actual = globalStore.getAll();
            assertThat(actual).containsExactlyElementsOf(anotherClient);
        }

        @Test
        void backsUpOldData() throws Exception {
            Long now = 1L; // seconds since epoch
            when(clock.getEpochSecond()).thenReturn(now);

            ClientKeyStoreWriter writer = new ClientKeyStoreWriter(globalStore, fileManager, jsonWriter, versionGenerator, clock, globalScope);

            writer.upload(oneClient, null);
            writer.upload(anotherClient, null);

            List<String> files = cloudStorage.list(rootDir);
            String datedBackup = "this-test-data-type/clients.json." + now + ".bak";
            String latestBackup = "this-test-data-type/clients.json.bak";
            assertThat(files).contains(datedBackup, latestBackup);
        }

        @Test
        void assignsNewVersionOnEveryWrite() throws Exception {
            Long now = 1L; // seconds since epoch
            when(clock.getEpochSecond()).thenReturn(now);

            ClientKeyStoreWriter writer = new ClientKeyStoreWriter(globalStore, fileManager, jsonWriter, versionGenerator, clock, globalScope);

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
        void savesGlobalFilesToCorrectLocation() throws Exception {
            ClientKeyStoreWriter writer = new ClientKeyStoreWriter(globalStore, fileManager, jsonWriter, versionGenerator, clock, globalScope);

            writer.upload(oneClient, null);

            List<String> files = cloudStorage.list(rootDir);
            String dataFile = rootDir + "/clients.json";
            String metaFile = rootDir + "/" + metadataFileName;
            assertThat(files).contains(dataFile, metaFile);
        }
    }

    @Nested
    class WithSiteScope {
        private final int siteInScope = 5;
        private final SiteScope siteScope = new SiteScope(globalMetadataPath, siteInScope);

        @Test
        void doesNotWriteToGlobalScope() throws Exception {
            ClientKeyStoreWriter globalWriter = new ClientKeyStoreWriter(globalStore, fileManager, jsonWriter, versionGenerator, clock, globalScope);
            globalWriter.upload(Collections.emptyList(), null);

            ClientKeyStoreWriter clientWriter = new ClientKeyStoreWriter(clientStore, fileManager, jsonWriter, versionGenerator, clock, siteScope);
            clientWriter.upload(oneClient, null);

            Collection<ClientKey> actual = globalStore.getAll();
            assertThat(actual).isEmpty();
        }

        @Test
        void writesToSiteScope() throws Exception {
            ClientKeyStoreWriter clientWriter = new ClientKeyStoreWriter(clientStore, fileManager, jsonWriter, versionGenerator, clock, siteScope);

            clientWriter.upload(oneClient, null);

            ClientKey actual = clientStore.getClientKey(oneClient.get(0).getKey());
            assertThat(actual).isEqualTo(oneClient.get(0));
        }

        @Test
        void writingToMultipleSiteScopesDoesntOverwrite() throws Exception {
            ClientKeyStoreWriter clientWriter = new ClientKeyStoreWriter(clientStore, fileManager, jsonWriter, versionGenerator, clock, siteScope);
            clientWriter.upload(oneClient, null);

            int siteInScope2 = 6;
            SiteScope scope2 = new SiteScope(globalMetadataPath, siteInScope2);
            RotatingClientKeyProvider siteStore2 = new RotatingClientKeyProvider(cloudStorage, scope2);
            ClientKeyStoreWriter siteWriter2 = new ClientKeyStoreWriter(siteStore2, fileManager, jsonWriter, versionGenerator, clock, scope2);
            siteWriter2.upload(anotherClient, null);

            Collection<ClientKey> actual1 = clientStore.getAll();
            assertThat(actual1).containsExactlyElementsOf(oneClient);

            Collection<ClientKey> actual2 = siteStore2.getAll();
            assertThat(actual2).containsExactlyElementsOf(anotherClient);
        }

        @Test
        void savesClientFilesToCorrectLocation() throws Exception {
            ClientKeyStoreWriter clientWriter = new ClientKeyStoreWriter(clientStore, fileManager, jsonWriter, versionGenerator, clock, siteScope);
            clientWriter.upload(oneClient, null);

            String scopedSiteDir = rootDir + "/site/" + siteInScope;
            List<String> files = cloudStorage.list(scopedSiteDir);
            String dataFile = scopedSiteDir + "/clients.json";
            String metaFile = scopedSiteDir + "/" + metadataFileName;
            assertThat(files).contains(dataFile, metaFile);
        }

        private RotatingClientKeyProvider clientStore;

        @BeforeEach
        void setUp() {
            clientStore = new RotatingClientKeyProvider(cloudStorage, siteScope);
        }
    }

    @BeforeEach
    void setUp() {
        cloudStorage = new InMemoryStorageMock();
        FileStorageMock fileStorage = new FileStorageMock(cloudStorage);
        fileManager = new FileManager(cloudStorage, fileStorage);
        globalStore = new RotatingClientKeyProvider(cloudStorage, globalScope);
        versionGenerator = mock(VersionGenerator.class);
        clock = mock(Clock.class);
    }

    private Clock clock;
    private VersionGenerator versionGenerator;
    private RotatingClientKeyProvider globalStore;
    private InMemoryStorageMock cloudStorage;
    private FileManager fileManager;
    private final List<ClientKey> oneClient = ImmutableList.of(
            new ClientKey("key1", "secret1", "contact1")
                    .withRoles(Role.GENERATOR)
                    .withSiteId(5)
    );
    private final List<ClientKey> anotherClient = ImmutableList.of(
            new ClientKey("key2", "secret2", "contact2")
                    .withRoles(Role.CLIENTKEY_ISSUER)
                    .withSiteId(5)
    );
    private final String rootDir = "this-test-data-type";
    private final String metadataFileName = "test-metadata.json";
    private final CloudPath globalMetadataPath = new CloudPath(rootDir).resolve(metadataFileName);
    private final GlobalScope globalScope = new GlobalScope(globalMetadataPath);
    private final ObjectWriter jsonWriter = createJsonWriter();
}