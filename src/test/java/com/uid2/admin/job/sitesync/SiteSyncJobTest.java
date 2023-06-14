package com.uid2.admin.job.sitesync;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableList;
import com.uid2.admin.job.jobsync.site.SiteSyncJob;
import com.uid2.admin.model.Site;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.InstantClock;
import com.uid2.admin.store.MultiScopeStoreWriter;
import com.uid2.admin.store.factory.SiteStoreFactory;
import com.uid2.admin.store.version.EpochVersionGenerator;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.mocks.FileStorageMock;
import com.uid2.admin.vertx.JsonUtil;
import com.uid2.shared.auth.InvalidRoleException;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.auth.OperatorType;
import com.uid2.shared.auth.Role;
import com.uid2.shared.cloud.InMemoryStorageMock;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.reader.StoreReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


public class SiteSyncJobTest {
    private InMemoryStorageMock cloudStorage;
    CloudPath globalSiteMetadataPath = new CloudPath("/some/test/path/sites/metadata.json");
    ObjectWriter objectWriter = JsonUtil.createJsonWriter();
    Integer scopedSiteId = 10;
    ImmutableList<OperatorKey> operators = ImmutableList.of(
            new OperatorKey(
                    "key",
                    "name",
                    "contact",
                    "protocol",
                    1618873215,
                    false,
                    scopedSiteId,
                    new HashSet<>(Collections.singletonList(Role.OPERATOR)),
                    OperatorType.PRIVATE));

    Site site = new Site(scopedSiteId, "site 1", true);
    private SiteStoreFactory siteStoreFactory;
    private FileManager fileManager;

    public SiteSyncJobTest() throws InvalidRoleException {
    }

    @BeforeEach
    void setUp() {
        cloudStorage = new InMemoryStorageMock();
        FileStorageMock fileStorage = new FileStorageMock(cloudStorage);
        Clock clock = new InstantClock();
        VersionGenerator versionGenerator = new EpochVersionGenerator(clock);
        fileManager = new FileManager(cloudStorage, fileStorage);
        siteStoreFactory = new SiteStoreFactory(
                cloudStorage,
                globalSiteMetadataPath,
                objectWriter,
                versionGenerator,
                clock,
                fileManager);
    }

    @Test
    public void writesNoSitesIfThereAreNoSites() throws Exception {
        SiteSyncJob job = new SiteSyncJob(new MultiScopeStoreWriter<>(
                fileManager,
                siteStoreFactory,
                MultiScopeStoreWriter::areCollectionsEqual),
                ImmutableList.of(), operators
        );

        job.execute();

        StoreReader<Collection<Site>> reader = siteStoreFactory.getReader(scopedSiteId);
        reader.loadContent();
        Collection<Site> scopedSites = reader.getAll();
        assertThat(scopedSites).isEmpty();

    }

    @Test
    public void syncsNewSites() throws Exception {
        SiteSyncJob job = new SiteSyncJob(new MultiScopeStoreWriter<>(
                fileManager,
                siteStoreFactory,
                MultiScopeStoreWriter::areCollectionsEqual),
                ImmutableList.of(site), operators
        );
        job.execute();

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
    public void overridesPreviouslySyncedSitesWhenThereAreChanges() throws Exception {
        siteStoreFactory.getWriter(scopedSiteId).upload(ImmutableList.of(site), null);


        StoreReader<Collection<Site>> reader = siteStoreFactory.getReader(scopedSiteId);
        reader.loadContent();
        Long oldVersion = reader.getMetadata().getLong("version");

        Site updatedSite = new Site(scopedSiteId, "site 1 updated", true);
        SiteSyncJob job = new SiteSyncJob(new MultiScopeStoreWriter<>(
                fileManager,
                siteStoreFactory,
                MultiScopeStoreWriter::areCollectionsEqual),
                ImmutableList.of(updatedSite), operators
        );

        job.execute();

        reader.loadContent();
        assertThat(reader.getAll()).containsExactly(updatedSite);

        Long newVersion = reader.getMetadata().getLong("version");
        assertThat(newVersion).isGreaterThan(oldVersion);
    }

    @Test
    public void doesNotSyncSitesThatAreNotChanged() throws Exception {
        siteStoreFactory.getWriter(scopedSiteId).upload(ImmutableList.of(site), null);
        siteStoreFactory.getGlobalWriter().upload(ImmutableList.of(site), null);

        StoreReader<Collection<Site>> reader = siteStoreFactory.getReader(scopedSiteId);
        reader.loadContent();
        Long oldVersion = reader.getMetadata().getLong("version");

        SiteSyncJob job = new SiteSyncJob(new MultiScopeStoreWriter<>(
                fileManager,
                siteStoreFactory,
                MultiScopeStoreWriter::areCollectionsEqual),
                ImmutableList.of(site), operators
        );

        job.execute();

        reader.loadContent();
        assertThat(reader.getAll()).containsExactly(site);
        Long newVersion = reader.getMetadata().getLong("version");
        assertThat(newVersion).isEqualTo(oldVersion);
    }
}