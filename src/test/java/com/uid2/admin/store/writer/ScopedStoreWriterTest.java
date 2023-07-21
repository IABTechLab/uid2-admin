package com.uid2.admin.store.writer;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableList;
import com.uid2.admin.model.Site;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.reader.RotatingSiteStore;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.mocks.FileStorageMock;
import com.uid2.admin.vertx.JsonUtil;
import com.uid2.shared.cloud.InMemoryStorageMock;
import com.uid2.shared.store.CloudPath;
import com.uid2.admin.store.FileName;
import com.uid2.shared.store.scope.GlobalScope;
import com.uid2.shared.store.scope.SiteScope;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScopedStoreWriterTest {
    @Nested
    class WithGlobalScope {
        @Test
        void uploadsContent() throws Exception {
            ScopedStoreWriter writer = new ScopedStoreWriter(globalStore, fileManager, versionGenerator, clock, globalScope, dataFile, dataType);

            writer.upload(jsonWriter.writeValueAsString(oneSite));

            Collection<Site> actual = globalStore.getAllSites();
            assertThat(actual).containsExactlyElementsOf(oneSite);
        }

        @Test
        void overridesWithNewDataOnSubsequentUploads() throws Exception {
            ScopedStoreWriter writer = new ScopedStoreWriter(globalStore, fileManager, versionGenerator, clock, globalScope, dataFile, dataType);

            writer.upload(jsonWriter.writeValueAsString(oneSite));
            writer.upload(jsonWriter.writeValueAsString(anotherSite));

            Collection<Site> actual = globalStore.getAllSites();
            assertThat(actual).containsExactlyElementsOf(anotherSite);
        }

        @Test
        void doesNotBackUpOldData() throws Exception {
            Long now = 1L; // seconds since epoch
            when(clock.getEpochSecond()).thenReturn(now);

            ScopedStoreWriter writer = new ScopedStoreWriter(globalStore, fileManager, versionGenerator, clock, globalScope, dataFile, dataType);

            writer.upload(jsonWriter.writeValueAsString(oneSite));
            writer.upload(jsonWriter.writeValueAsString(anotherSite));

            List<String> files = cloudStorage.list(sitesDir);
            String datedBackup = "sites/sites.json." + now + ".bak";
            String latestBackup = "sites/sites.json.bak";
            assertThat(files).doesNotContain(datedBackup, latestBackup);
        }

        @Test
        void assignsNewVersionOnEveryWrite() throws Exception {
            Long now = 1L; // seconds since epoch
            when(clock.getEpochSecond()).thenReturn(now);

            ScopedStoreWriter writer = new ScopedStoreWriter(globalStore, fileManager, versionGenerator, clock, globalScope, dataFile, dataType);

            when(versionGenerator.getVersion()).thenReturn(10L);
            writer.upload(jsonWriter.writeValueAsString(oneSite));
            JsonObject metadata1 = globalStore.getMetadata();
            assertThat(metadata1.getLong("version")).isEqualTo(10L);

            when(versionGenerator.getVersion()).thenReturn(11L);
            writer.upload(jsonWriter.writeValueAsString(anotherSite));
            JsonObject metadata2 = globalStore.getMetadata();
            assertThat(metadata2.getLong("version")).isEqualTo(11L);
        }

        @Test
        void savesGlobalFilesToCorrectLocation() throws Exception {
            ScopedStoreWriter writer = new ScopedStoreWriter(globalStore, fileManager, versionGenerator, clock, globalScope, dataFile, dataType);

            writer.upload(jsonWriter.writeValueAsString(oneSite));

            List<String> files = cloudStorage.list(sitesDir);
            String dataFile = sitesDir + "/sites.json";
            String metaFile = sitesDir + "/" + metadataFileName;
            assertThat(files).contains(dataFile, metaFile);
        }

        @Test
        void addsExtraMetadata() throws Exception {
            ScopedStoreWriter writer = new ScopedStoreWriter(globalStore, fileManager, versionGenerator, clock, globalScope, dataFile, dataType);
            JsonObject extraMeta = new JsonObject();
            String expected = "extraValue1";
            extraMeta.put("extraField1", expected);

            writer.upload("[]", extraMeta);

            JsonObject metadata = globalStore.getMetadata();
            String actual = metadata.getString("extraField1");
            assertThat(actual).isEqualTo(expected);
        }
    }

    @Nested
    class WithSiteScope {
        private final int siteInScope = 5;
        private final SiteScope siteScope = new SiteScope(globalMetadataPath, siteInScope);

        @Test
        void doesNotWriteToGlobalScope() throws Exception {
            ScopedStoreWriter globalWriter = new ScopedStoreWriter(globalStore, fileManager, versionGenerator, clock, globalScope, dataFile, dataType);
            globalWriter.upload(jsonWriter.writeValueAsString(Collections.emptyList()));

            ScopedStoreWriter siteWriter = new ScopedStoreWriter(siteStore, fileManager, versionGenerator, clock, siteScope, dataFile, dataType);
            siteWriter.upload(jsonWriter.writeValueAsString(oneSite));

            Collection<Site> actual = globalStore.getAllSites();
            assertThat(actual).isEmpty();
        }

        @Test
        void writesToSiteScope() throws Exception {
            ScopedStoreWriter siteWriter = new ScopedStoreWriter(siteStore, fileManager, versionGenerator, clock, siteScope, dataFile, dataType);

            siteWriter.upload(jsonWriter.writeValueAsString(oneSite));

            Site actual = siteStore.getSite(oneSite.get(0).getId());
            assertThat(actual).isEqualTo(oneSite.get(0));
        }

        @Test
        void writingToMultipleSiteScopesDoesntOverwrite() throws Exception {
            ScopedStoreWriter siteWriter = new ScopedStoreWriter(siteStore, fileManager, versionGenerator, clock, siteScope, dataFile, dataType);
            siteWriter.upload(jsonWriter.writeValueAsString(oneSite));

            int siteInScope2 = 6;
            SiteScope scope2 = new SiteScope(globalMetadataPath, siteInScope2);
            RotatingSiteStore siteStore2 = new RotatingSiteStore(cloudStorage, scope2);
            ScopedStoreWriter siteWriter2 = new ScopedStoreWriter(siteStore2, fileManager, versionGenerator, clock, scope2, dataFile, dataType);
            siteWriter2.upload(jsonWriter.writeValueAsString(anotherSite));

            Collection<Site> actual1 = siteStore.getAllSites();
            assertThat(actual1).containsExactlyElementsOf(oneSite);

            Collection<Site> actual2 = siteStore2.getAllSites();
            assertThat(actual2).containsExactlyElementsOf(anotherSite);
        }

        @Test
        void savesSiteFilesToCorrectLocation() throws Exception {
            ScopedStoreWriter siteWriter = new ScopedStoreWriter(siteStore, fileManager, versionGenerator, clock, siteScope, dataFile, dataType);
            siteWriter.upload(jsonWriter.writeValueAsString(oneSite));

            String scopedSiteDir = sitesDir + "/site/" + siteInScope;
            List<String> files = cloudStorage.list(scopedSiteDir);
            String dataFile = scopedSiteDir + "/sites.json";
            String metaFile = scopedSiteDir + "/" + metadataFileName;
            assertThat(files).contains(dataFile, metaFile);
        }

        private RotatingSiteStore siteStore;

        @BeforeEach
        void setUp() {
            siteStore = new RotatingSiteStore(cloudStorage, siteScope);
        }
    }


    @Test
    void rewritesMetadata() throws Exception {
        ScopedStoreWriter writer = new ScopedStoreWriter(globalStore, fileManager, versionGenerator, clock, globalScope, dataFile, dataType);

        String unchangedMetaField = "unchangedMetaField";
        String unchangedMetaValue = "unchangedMetaValue";
        when(versionGenerator.getVersion()).thenReturn(100L);
        writer.upload("[]", new JsonObject().put(unchangedMetaField, unchangedMetaValue));

        long expectedVersion = 200L;
        when(versionGenerator.getVersion()).thenReturn(expectedVersion);
        writer.rewriteMeta();

        JsonObject metadata = globalStore.getMetadata();
        Long actualVersion = metadata.getLong("version");
        assertThat(actualVersion).isEqualTo(expectedVersion);
        assertThat(metadata.getString(unchangedMetaField)).isEqualTo(unchangedMetaValue);
    }

    @Test
    void ignoresMetadataRewritesWhenNoMetadata() throws Exception {
        ScopedStoreWriter writer = new ScopedStoreWriter(globalStore, fileManager, versionGenerator, clock, globalScope, dataFile, dataType);

        writer.rewriteMeta();

        assertThat(cloudStorage.list("")).isEmpty();
    }

    @BeforeEach
    void setUp() {
        cloudStorage = new InMemoryStorageMock();
        FileStorageMock fileStorage = new FileStorageMock(cloudStorage);
        fileManager = new FileManager(cloudStorage, fileStorage);
        globalStore = new RotatingSiteStore(cloudStorage, globalScope);
        versionGenerator = mock(VersionGenerator.class);
        clock = mock(Clock.class);
    }

    private Clock clock;
    ObjectWriter jsonWriter = JsonUtil.createJsonWriter();
    private VersionGenerator versionGenerator;
    private RotatingSiteStore globalStore;
    private InMemoryStorageMock cloudStorage;
    private FileManager fileManager;
    private final List<Site> oneSite = ImmutableList.of(new Site(1, "site 1", true, new HashSet<>()));
    private final List<Site> anotherSite = ImmutableList.of(new Site(2, "site 2", true, new HashSet<>()));
    private final String sitesDir = "sites";
    private final String metadataFileName = "test-metadata.json";
    private final CloudPath globalMetadataPath = new CloudPath(sitesDir).resolve(metadataFileName);
    private final GlobalScope globalScope = new GlobalScope(globalMetadataPath);
    private final FileName dataFile = new FileName("sites", ".json");
    private final String dataType = "sites";

}