package com.uid2.admin.job.sitesync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableList;
import com.uid2.admin.model.Site;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.InstantClock;
import com.uid2.admin.store.SiteStoreFactory;
import com.uid2.admin.store.reader.RotatingSiteStore;
import com.uid2.admin.store.version.EpochVersionGenerator;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.mocks.FileStorageMock;
import com.uid2.admin.vertx.JsonUtil;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.auth.OperatorType;
import com.uid2.shared.auth.Role;
import com.uid2.shared.auth.RotatingOperatorKeyProvider;
import com.uid2.shared.cloud.CloudStorageException;
import com.uid2.shared.cloud.InMemoryStorageMock;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.scope.GlobalScope;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


public class SiteSyncJobTest {
    private InMemoryStorageMock cloudStorage;
    private RotatingOperatorKeyProvider globalOperatorReader;
    CloudPath globalSiteMetadataPath = new CloudPath("/some/test/path/sites/metadata.json");
    CloudPath globalSitesPath = new CloudPath("/some/test/path/sites/sites.json");
    CloudPath globalOperatorMetadataPath = new CloudPath("/some/test/path/operators/metadata.json");
    CloudPath globalOperatorPath = new CloudPath("/some/test/path/operators/operators.json");
    ObjectWriter objectWriter = JsonUtil.createJsonWriter();

    Integer scopedSiteId = 10;
    private SiteStoreFactory siteStoreFactory;

    @BeforeEach
    void setUp() throws CloudStorageException, JsonProcessingException {
        cloudStorage = new InMemoryStorageMock();
        FileStorageMock fileStorage = new FileStorageMock(cloudStorage);
        globalOperatorReader = new RotatingOperatorKeyProvider(
                cloudStorage,
                cloudStorage,
                new GlobalScope(globalOperatorMetadataPath));
        Clock clock = new InstantClock();
        VersionGenerator versionGenerator = new EpochVersionGenerator(clock);
        uploadMetadata(globalSiteMetadataPath, globalSitesPath, "sites");
        uploadMetadata(globalOperatorMetadataPath, globalOperatorPath, "operators");
        uploadData("[]", globalSitesPath);
        uploadData(objectWriter.writeValueAsString(ImmutableList.of(
                new OperatorKey(
                        "key",
                        "name",
                        "contact",
                        "protocol",
                        1618873215,
                        false,
                        scopedSiteId,
                        new HashSet<>(Collections.singletonList(Role.ID_READER)),
                        OperatorType.PRIVATE))
        ), globalOperatorPath);
        siteStoreFactory = new SiteStoreFactory(
                cloudStorage,
                globalSiteMetadataPath,
                fileStorage,
                objectWriter,
                versionGenerator,
                clock);
    }

    @Test
    public void writesNoSitesIfThereAreNoSites() throws Exception {
        SiteSyncJob job = new SiteSyncJob(siteStoreFactory, globalOperatorReader);

        job.execute();

        RotatingSiteStore reader = siteStoreFactory.getReader(scopedSiteId);
        reader.loadContent();
        Collection<Site> scopedSites = reader.getAllSites();
        assertThat(scopedSites).isEmpty();

    }

    @Test
    public void syncsNewSites() throws Exception {
        Site site = new Site(scopedSiteId, "site 1", true);
        siteStoreFactory.getGlobalWriter().upload(ImmutableList.of(site));

        SiteSyncJob job = new SiteSyncJob(siteStoreFactory, globalOperatorReader);
        job.execute();

        List<String> allFilesInCloud = cloudStorage.list("");
        assertThat(allFilesInCloud).contains(
                "/some/test/path/sites/site/10/metadata.json",
                "/some/test/path/sites/site/10/sites.json"
        );

        RotatingSiteStore reader = siteStoreFactory.getReader(scopedSiteId);
        reader.loadContent();
        Collection<Site> scopedSites = reader.getAllSites();
        assertThat(scopedSites).containsExactly(site);
    }

    @Test
    public void overridesPreviouslySyncedSitesWhenThereAreChanges() throws Exception {
        Site site = new Site(scopedSiteId, "site 1", true);
        siteStoreFactory.getWriter(scopedSiteId).upload(ImmutableList.of(site));
        Site updatedSite = new Site(scopedSiteId, "site 1 updated", true);
        siteStoreFactory.getGlobalWriter().upload(ImmutableList.of(updatedSite));

        RotatingSiteStore reader = siteStoreFactory.getReader(scopedSiteId);
        reader.loadContent();
        Long oldVersion = reader.getMetadata().getLong("version");

        SiteSyncJob job = new SiteSyncJob(siteStoreFactory, globalOperatorReader);

        job.execute();

        reader.loadContent();
        assertThat(reader.getAllSites()).containsExactly(updatedSite);

        Long newVersion = reader.getMetadata().getLong("version");
        assertThat(newVersion).isGreaterThan(oldVersion);
    }

    @Test
    public void doesNotSyncSitesThatAreNotChanged() throws Exception {
        Site site = new Site(scopedSiteId, "site 1", true);
        siteStoreFactory.getWriter(scopedSiteId).upload(ImmutableList.of(site));
        siteStoreFactory.getGlobalWriter().upload(ImmutableList.of(site));

        RotatingSiteStore reader = siteStoreFactory.getReader(scopedSiteId);
        reader.loadContent();
        Long oldVersion = reader.getMetadata().getLong("version");

        SiteSyncJob job = new SiteSyncJob(siteStoreFactory, globalOperatorReader);

        job.execute();

        reader.loadContent();
        assertThat(reader.getAllSites()).containsExactly(site);
        Long newVersion = reader.getMetadata().getLong("version");
        assertThat(newVersion).isEqualTo(oldVersion);
    }

    private void uploadMetadata(
            CloudPath metadataPath,
            CloudPath dataPath,
            String dataType) throws CloudStorageException {
        JsonObject sitesMetadata = new JsonObject()
                .put(dataType, new JsonObject().put(
                        "location", dataPath.toString()
                ));
        uploadData(sitesMetadata.encodePrettily(), metadataPath);
    }

    private void uploadData(String data, CloudPath globalSitesPath) throws CloudStorageException {
        cloudStorage.upload(toInputStream(data), globalSitesPath.toString());
    }

    private static ByteArrayInputStream toInputStream(String data) {
        return new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
    }
}