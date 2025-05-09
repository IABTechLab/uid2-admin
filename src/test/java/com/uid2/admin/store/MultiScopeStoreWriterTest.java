package com.uid2.admin.store;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.uid2.admin.store.factory.EncryptedStoreFactory;
import com.uid2.admin.store.factory.SiteStoreFactory;
import com.uid2.admin.store.factory.StoreFactory;
import com.uid2.admin.store.version.EpochVersionGenerator;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.mocks.FileStorageMock;
import com.uid2.admin.vertx.JsonUtil;
import com.uid2.shared.cloud.DownloadCloudStorage;
import com.uid2.shared.cloud.InMemoryStorageMock;
import com.uid2.shared.model.CloudEncryptionKey;
import com.uid2.shared.model.Site;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.reader.RotatingCloudEncryptionKeyProvider;
import com.uid2.shared.store.reader.StoreReader;

import com.uid2.shared.store.scope.StoreScope;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MultiScopeStoreWriterTest {
    private InMemoryStorageMock cloudStorage;
    private DownloadCloudStorage fileStreamProvider;
    private StoreScope scope;
    CloudPath globalSiteMetadataPath = new CloudPath("/some/test/path/sites/metadata.json");
    ObjectWriter objectWriter = JsonUtil.createJsonWriter();
    Integer scopedSiteId = 10;
    private SiteStoreFactory siteStoreFactory;
    @Mock
    private RotatingCloudEncryptionKeyProvider cloudEncryptionKeyProvider;

    Site site = new Site(scopedSiteId, "site 1", true);
    private FileManager fileManager;

    @BeforeEach
    void setUp() throws Exception {
        cloudStorage = new InMemoryStorageMock();
        FileStorageMock fileStorage = new FileStorageMock(cloudStorage);
        Clock clock = new InstantClock();
        VersionGenerator versionGenerator = new EpochVersionGenerator(clock);
        fileManager = new FileManager(cloudStorage, fileStorage);
        cloudEncryptionKeyProvider = mock(RotatingCloudEncryptionKeyProvider.class);
        siteStoreFactory = new SiteStoreFactory(
                cloudStorage,
                globalSiteMetadataPath,
                objectWriter,
                versionGenerator,
                clock,
                fileManager);
    }

    @Test
    public void writesNothingGivenNoDesiredState() throws Exception {
        MultiScopeStoreWriter<Collection<Site>> multiStore = new MultiScopeStoreWriter<>(fileManager, siteStoreFactory, MultiScopeStoreWriter::areCollectionsEqual);

        multiStore.uploadIfChanged(ImmutableMap.of(), null);

        assertThat(cloudStorage.list("")).isEmpty();

    }

    @Test
    public void syncsNewData() throws Exception {
        MultiScopeStoreWriter<Collection<Site>> multiStore = new MultiScopeStoreWriter<>(fileManager, siteStoreFactory, MultiScopeStoreWriter::areCollectionsEqual);

        multiStore.uploadIfChanged(ImmutableMap.of(scopedSiteId, ImmutableList.of(site)),null);

        List<String> allFilesInCloud = cloudStorage.list("");
        assertThat(allFilesInCloud).contains(
                "/some/test/path/sites/site/10/metadata.json",
                "/some/test/path/sites/site/10/sites.json"
        );

        StoreReader<Collection<Site>> reader = siteStoreFactory.getReader(scopedSiteId);
        reader.loadContent();
        Collection<Site> scopedSites = reader.getAll();
        assertThat(scopedSites).containsExactly(site);
    }
    @Test
    public void addsExtraMetadataProvided() throws Exception {
        MultiScopeStoreWriter<Collection<Site>> multiStore = new MultiScopeStoreWriter<>(fileManager, siteStoreFactory, MultiScopeStoreWriter::areCollectionsEqual);

        JsonObject extraMeta = new JsonObject().put("key1", "value1").put("key2", 2);

        multiStore.uploadIfChanged(ImmutableMap.of(scopedSiteId, ImmutableList.of(site)), extraMeta);

        StoreReader<Collection<Site>> reader = siteStoreFactory.getReader(scopedSiteId);
        JsonObject actualMetadata = reader.getMetadata();
        assertThat(actualMetadata.getString("key1")).isEqualTo("value1");
        assertThat(actualMetadata.getInteger("key2")).isEqualTo(2);
    }

    @Test
    public void overwritesExistingDataWhenChanged() throws Exception {
        siteStoreFactory.getWriter(scopedSiteId).upload(ImmutableList.of(site), null);

        StoreReader<Collection<Site>> reader = siteStoreFactory.getReader(scopedSiteId);
        reader.loadContent();
        Long oldVersion = reader.getMetadata().getLong("version");

        // This test relies on our version generator returning a new timestamp, but the code can execute so fast we don't get a new version
        // This small sleep makes this test much more stable
        Thread.sleep(100);

        Site updatedSite = new Site(scopedSiteId, "site 1 updated", true);
        MultiScopeStoreWriter<Collection<Site>> multiStore = new MultiScopeStoreWriter<>(fileManager, siteStoreFactory, MultiScopeStoreWriter::areCollectionsEqual);

        multiStore.uploadIfChanged(ImmutableMap.of(
                scopedSiteId, ImmutableList.of(updatedSite)
        ), null);

        reader.loadContent();
        assertThat(reader.getAll()).containsExactly(updatedSite);

        Long newVersion = reader.getMetadata().getLong("version");
        assertThat(newVersion).isGreaterThan(oldVersion);
    }

    @Test
    public void doesNotWriteDataThatHasNotChanged() throws Exception {
        siteStoreFactory.getWriter(scopedSiteId).upload(ImmutableList.of(site), null);
        siteStoreFactory.getGlobalWriter().upload(ImmutableList.of(site), null);

        StoreReader<Collection<Site>> reader = siteStoreFactory.getReader(scopedSiteId);
        reader.loadContent();
        Long oldVersion = reader.getMetadata().getLong("version");

        MultiScopeStoreWriter<Collection<Site>> multiStore = new MultiScopeStoreWriter<>(fileManager, siteStoreFactory, MultiScopeStoreWriter::areCollectionsEqual);

        multiStore.uploadIfChanged(ImmutableMap.of(
                scopedSiteId, ImmutableList.of(site)
        ), null);

        reader.loadContent();
        assertThat(reader.getAll()).containsExactly(site);
        Long newVersion = reader.getMetadata().getLong("version");
        assertThat(newVersion).isEqualTo(oldVersion);
    }

    @Nested
    class AreMapsEqual {
        Map<String, String> a = ImmutableMap.of(
                "day", "giorno",
                "evening", "sera"
        );
        Map<String, String> b = ImmutableMap.of(
                "day", "Tag",
                "evening", "Abend"
        );
        @Test
        void whenSameObjectReturnsTrue() {
            assertThat(MultiScopeStoreWriter.areMapsEqual(a, a)).isTrue();
        }

        @Test
        void whenEqualReturnsTrue() {
            Map<String, String> a1 = ImmutableMap.of(
                    "day", "giorno",
                    "evening", "sera"
            );

            assertThat(MultiScopeStoreWriter.areMapsEqual(a, a1)).isTrue();
        }

        @Test
        void whenNotEqualReturnsFalse() {
            assertThat(MultiScopeStoreWriter.areMapsEqual(a, b)).isFalse();
        }
    }

    @Nested
    class AreCollectionsEqual {
        Collection<TestData> a = ImmutableList.of(new TestData("day"), new TestData("evening"));
        Collection<TestData> b = ImmutableList.of(new TestData("morning"), new TestData("night"))  ;
        @Test
        void whenSameObjectReturnsTrue() {
            assertThat(MultiScopeStoreWriter.areCollectionsEqual(a, a)).isTrue();
        }

        @Test
        void whenEqualReturnsTrue() {
            Collection<TestData> a1 = ImmutableList.of(new TestData("day"), new TestData("evening"));

            assertThat(MultiScopeStoreWriter.areCollectionsEqual(a, a1)).isTrue();
        }

        @Test
        void whenNotEqualReturnsFalse() {
            assertThat(MultiScopeStoreWriter.areCollectionsEqual(a, b)).isFalse();
        }
    }

    @Test
    public void uploadPrivateWithEncryption() throws Exception {
        JsonObject metadata = new JsonObject();
        metadata.put("version", 5);
        InputStream metadataStream = new ByteArrayInputStream(metadata.encode().getBytes(StandardCharsets.UTF_8));
        cloudStorage.upload(metadataStream, "/some/test/path/sites/site/10/metadata.json");
        CloudEncryptionKey encryptionKey = new CloudEncryptionKey(1, 10, 1, 1, "mydrCudb2PZOm01Qn0SpthltmexHUAA11Hy1m+uxjVw=");
        when(cloudEncryptionKeyProvider.getEncryptionKeyForSite(10)).thenReturn(encryptionKey);
        Map<Integer, CloudEncryptionKey> allKeys = new HashMap<>();
        allKeys.put(1, encryptionKey);
        when(cloudEncryptionKeyProvider.getAll()).thenReturn(allKeys);
        when(cloudEncryptionKeyProvider.getKey(1)).thenReturn(encryptionKey);
        SiteStoreFactory siteStoreFactory = new SiteStoreFactory(
                cloudStorage,
                globalSiteMetadataPath,
                objectWriter,
                new EpochVersionGenerator(new InstantClock()),
                new InstantClock(),
                cloudEncryptionKeyProvider,
                fileManager
        );

        MultiScopeStoreWriter<Collection<Site>> multiStore = new MultiScopeStoreWriter<>(
                fileManager,
                siteStoreFactory,
                MultiScopeStoreWriter::areCollectionsEqual
        );

        Site privateSite = new Site(scopedSiteId, "private site", false);
        JsonObject extraMeta = new JsonObject().put("key", "value");

        multiStore.uploadPrivateWithEncryption(ImmutableMap.of(scopedSiteId, ImmutableList.of(privateSite)), extraMeta);

        StoreReader<Collection<Site>> reader = ((EncryptedStoreFactory<Collection<Site>>) siteStoreFactory)
                .getEncryptedReader(scopedSiteId, false);
        reader.loadContent();
        Collection<Site> sites = reader.getAll();
        assertThat(sites).containsExactly(privateSite);
        assertThat(reader.getMetadata().getString("key")).isEqualTo("value");
        cloudStorage.delete("/some/test/path/sites/site/10/metadata.json");
    }

    @Test
    public void uploadPublicWithEncryption() throws Exception {
        JsonObject metadata = new JsonObject();
        metadata.put("version", 5);
        InputStream metadataStream = new ByteArrayInputStream(metadata.encode().getBytes(StandardCharsets.UTF_8));
        cloudStorage.upload(metadataStream, "/some/test/path/sites/site/10/metadata.json");
        CloudEncryptionKey encryptionKey = new CloudEncryptionKey(1, 10, 1, 1, "mydrCudb2PZOm01Qn0SpthltmexHUAA11Hy1m+uxjVw=");
        when(cloudEncryptionKeyProvider.getEncryptionKeyForSite(10)).thenReturn(encryptionKey);
        Map<Integer, CloudEncryptionKey> allKeys = new HashMap<>();
        allKeys.put(1, encryptionKey);
        when(cloudEncryptionKeyProvider.getAll()).thenReturn(allKeys);
        when(cloudEncryptionKeyProvider.getKey(1)).thenReturn(encryptionKey);
        SiteStoreFactory siteStoreFactory = new SiteStoreFactory(
                cloudStorage,
                globalSiteMetadataPath,
                objectWriter,
                new EpochVersionGenerator(new InstantClock()),
                new InstantClock(),
                cloudEncryptionKeyProvider,
                fileManager
        );

        MultiScopeStoreWriter<Collection<Site>> multiStore = new MultiScopeStoreWriter<>(
                fileManager,
                siteStoreFactory,
                MultiScopeStoreWriter::areCollectionsEqual
        );
        Site publicSite = new Site(scopedSiteId, "public site", true);
        JsonObject extraMeta = new JsonObject().put("key", "value");

        multiStore.uploadPublicWithEncryption(ImmutableMap.of(scopedSiteId, ImmutableList.of(publicSite)), extraMeta);

        StoreReader<Collection<Site>> reader = ((EncryptedStoreFactory<Collection<Site>>) siteStoreFactory)
                .getEncryptedReader(scopedSiteId, true);
        reader.loadContent();
        Collection<Site> sites = reader.getAll();
        assertThat(sites).containsExactly(publicSite);
        assertThat(reader.getMetadata().getString("key")).isEqualTo("value");
        cloudStorage.delete("/some/test/path/sites/site/10/metadata.json");
    }


    class TestData {
        private final String field1;

        public TestData(String field1) {
            this.field1 = field1;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TestData testData = (TestData) o;
            return Objects.equals(field1, testData.field1);
        }

        @Override
        public int hashCode() {
            return Objects.hash(field1);
        }
    }
}
