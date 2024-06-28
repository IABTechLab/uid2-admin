package com.uid2.admin.job.sitesync;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableList;
import com.uid2.admin.job.jobsync.site.SiteSyncJob;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.InstantClock;
import com.uid2.admin.store.MultiScopeStoreWriter;
import com.uid2.admin.store.factory.SiteStoreFactory;
import com.uid2.admin.store.version.EpochVersionGenerator;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.mocks.FileStorageMock;
import com.uid2.admin.vertx.JsonUtil;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.auth.OperatorType;
import com.uid2.shared.auth.Role;
import com.uid2.shared.cloud.InMemoryStorageMock;
import com.uid2.shared.model.Site;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.reader.StoreReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

public class SiteSyncJobTest {
    private final CloudPath globalSiteMetadataPath = new CloudPath("/some/test/path/sites/metadata.json");
    private final ObjectWriter objectWriter = JsonUtil.createJsonWriter();
    private final int scopedSiteId = 10;
    private final ImmutableList<OperatorKey> operators = ImmutableList.of(
            new OperatorKey(
                    "keyHash",
                    "keySalt",
                    "name",
                    "contact",
                    "protocol",
                    1618873215,
                    false,
                    scopedSiteId,
                    Set.of(Role.OPERATOR),
                    OperatorType.PRIVATE,
                    "key-id")
    );
    private final Site site = new Site(scopedSiteId, "site 1", true);

    private InMemoryStorageMock cloudStorage;
    private SiteStoreFactory siteStoreFactory;
    private FileManager fileManager;

    @BeforeEach
    public void setup() {
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
                null,
                fileManager
        );
    }

    @Test
    public void writesNoSitesIfThereAreNoSites() throws Exception {
        SiteSyncJob job = new SiteSyncJob(new MultiScopeStoreWriter<>(
                fileManager,
                siteStoreFactory,
                MultiScopeStoreWriter::areCollectionsEqual),
                ImmutableList.of(),
                operators
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
                ImmutableList.of(site),
                operators
        );
        job.execute();

        StoreReader<Collection<Site>> reader = siteStoreFactory.getReader(scopedSiteId);
        reader.loadContent();

        assertAll(
                "syncsNewSites",
                () -> assertThat(cloudStorage.list("")).contains(
                        "/some/test/path/sites/site/10/metadata.json",
                        "/some/test/path/sites/site/10/sites.json"
                ),
                () -> assertThat(reader.getAll()).containsExactly(site)
        );
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
                ImmutableList.of(updatedSite),
                operators
        );
        job.execute();

        reader.loadContent();

        assertAll(
                "overridesPreviouslySyncedSitesWhenThereAreChanges",
                () -> assertThat(reader.getAll()).containsExactly(updatedSite),
                () -> assertThat(reader.getMetadata().getLong("version")).isGreaterThan(oldVersion)
        );
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
                ImmutableList.of(site),
                operators
        );
        job.execute();

        reader.loadContent();

        assertAll(
                "doesNotSyncSitesThatAreNotChanged",
                () -> assertThat(reader.getAll()).containsExactly(site),
                () -> assertThat(reader.getMetadata().getLong("version")).isEqualTo(oldVersion)
        );
    }
}
