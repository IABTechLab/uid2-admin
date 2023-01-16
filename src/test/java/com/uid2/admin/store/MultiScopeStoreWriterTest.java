package com.uid2.admin.store;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.uid2.admin.model.Site;
import com.uid2.admin.store.factory.SiteStoreFactory;
import com.uid2.admin.store.version.EpochVersionGenerator;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.mocks.FileStorageMock;
import com.uid2.admin.vertx.JsonUtil;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.auth.OperatorType;
import com.uid2.shared.auth.Role;
import com.uid2.shared.cloud.InMemoryStorageMock;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.reader.StoreReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class MultiScopeStoreWriterTest {
    private InMemoryStorageMock cloudStorage;
    CloudPath globalSiteMetadataPath = new CloudPath("/some/test/path/sites/metadata.json");
    ObjectWriter objectWriter = JsonUtil.createJsonWriter();
    Integer scopedSiteId = 10;
    private SiteStoreFactory siteStoreFactory;

    ImmutableList<OperatorKey> operators = ImmutableList.of(
            new OperatorKey(
                    "key",
                    "name",
                    "contact",
                    "protocol",
                    1618873215,
                    false,
                    scopedSiteId,
                    new HashSet<>(Collections.singletonList(Role.ID_READER)),
                    OperatorType.PRIVATE));

    Site site = new Site(scopedSiteId, "site 1", true);

    @BeforeEach
    void setUp() {
        cloudStorage = new InMemoryStorageMock();
        FileStorageMock fileStorage = new FileStorageMock(cloudStorage);
        Clock clock = new InstantClock();
        VersionGenerator versionGenerator = new EpochVersionGenerator(clock);
        siteStoreFactory = new SiteStoreFactory(
                cloudStorage,
                globalSiteMetadataPath,
                fileStorage,
                objectWriter,
                versionGenerator,
                clock);
    }

    @Test
    public void writesNothingGivenNoDesiredState() throws Exception {
        MultiScopeStoreWriter<Collection<Site>> multiStore = new MultiScopeStoreWriter<>(siteStoreFactory, MultiScopeStoreWriter::areCollectionsEqual);

        multiStore.uploadIfChanged(ImmutableMap.of());

        assertThat(cloudStorage.list("")).isEmpty();

    }

    @Test
    public void syncsNewData() throws Exception {
        MultiScopeStoreWriter<Collection<Site>> multiStore = new MultiScopeStoreWriter<>(siteStoreFactory, MultiScopeStoreWriter::areCollectionsEqual);

        multiStore.uploadIfChanged(ImmutableMap.of(
                scopedSiteId, ImmutableList.of(site)
        ));

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
    public void overwritesExistingDataWhenChanged() throws Exception {
        siteStoreFactory.getWriter(scopedSiteId).upload(ImmutableList.of(site));

        StoreReader<Collection<Site>> reader = siteStoreFactory.getReader(scopedSiteId);
        reader.loadContent();
        Long oldVersion = reader.getMetadata().getLong("version");

        Site updatedSite = new Site(scopedSiteId, "site 1 updated", true);
        MultiScopeStoreWriter<Collection<Site>> multiStore = new MultiScopeStoreWriter<>(siteStoreFactory, MultiScopeStoreWriter::areCollectionsEqual);

        multiStore.uploadIfChanged(ImmutableMap.of(
                scopedSiteId, ImmutableList.of(updatedSite)
        ));

        reader.loadContent();
        assertThat(reader.getAll()).containsExactly(updatedSite);

        Long newVersion = reader.getMetadata().getLong("version");
        assertThat(newVersion).isGreaterThan(oldVersion);
    }

    @Test
    public void doesNotWriteDataThatHasNotChanged() throws Exception {
        siteStoreFactory.getWriter(scopedSiteId).upload(ImmutableList.of(site));
        siteStoreFactory.getGlobalWriter().upload(ImmutableList.of(site));

        StoreReader<Collection<Site>> reader = siteStoreFactory.getReader(scopedSiteId);
        reader.loadContent();
        Long oldVersion = reader.getMetadata().getLong("version");

        MultiScopeStoreWriter<Collection<Site>> multiStore = new MultiScopeStoreWriter<>(siteStoreFactory, MultiScopeStoreWriter::areCollectionsEqual);

        multiStore.uploadIfChanged(ImmutableMap.of(
                scopedSiteId, ImmutableList.of(site)
        ));

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
        Collection<String> a = ImmutableList.of("day", "evening");
        Collection<String> b = ImmutableList.of("morning", "night");
        @Test
        void whenSameObjectReturnsTrue() {
            assertThat(MultiScopeStoreWriter.areCollectionsEqual(a, a)).isTrue();
        }

        @Test
        void whenEqualReturnsTrue() {
            Collection<String> a1 = ImmutableList.of("day", "evening");

            assertThat(MultiScopeStoreWriter.areCollectionsEqual(a, a1)).isTrue();
        }

        @Test
        void whenNotEqualReturnsFalse() {
            assertThat(MultiScopeStoreWriter.areCollectionsEqual(a, b)).isFalse();
        }
    }
}
