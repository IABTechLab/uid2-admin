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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

public class SiteSyncJobTest {
    private static final Instant NOW = Instant.now();
    private static final CloudPath GLOBAL_SITE_METADATA_PATH = new CloudPath("/some/test/path/sites/metadata.json");
    private static final ObjectWriter OBJECT_WRITER = JsonUtil.createJsonWriter();
    private static final Integer SCOPED_SITE_ID = 10;
    private static final Site SITE = new Site(SCOPED_SITE_ID, "site 1", true);
    private static final ImmutableList<OperatorKey> OPERATORS = ImmutableList.of(
            new OperatorKey(
                    "keyHash",
                    "keySalt",
                    "name",
                    "contact",
                    "protocol",
                    NOW.minus(7, ChronoUnit.DAYS).getEpochSecond(),
                    false,
                    SCOPED_SITE_ID,
                    new HashSet<>(Collections.singletonList(Role.OPERATOR)),
                    OperatorType.PRIVATE
            )
    );

    private InMemoryStorageMock cloudStorage;
    private FileManager fileManager;
    private SiteStoreFactory siteStoreFactory;

    @BeforeEach
    void setup() {
        cloudStorage = new InMemoryStorageMock();
        fileManager = new FileManager(cloudStorage, new FileStorageMock(cloudStorage));

        Clock clock = new InstantClock();
        VersionGenerator versionGenerator = new EpochVersionGenerator(clock);
        siteStoreFactory = new SiteStoreFactory(
                cloudStorage,
                GLOBAL_SITE_METADATA_PATH,
                OBJECT_WRITER,
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
                ImmutableList.of(),
                OPERATORS
        );
        job.execute();

        StoreReader<Collection<Site>> reader = siteStoreFactory.getReader(SCOPED_SITE_ID);
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
                ImmutableList.of(SITE),
                OPERATORS
        );
        job.execute();

        StoreReader<Collection<Site>> reader = siteStoreFactory.getReader(SCOPED_SITE_ID);
        reader.loadContent();

        assertAll(
                "syncsNewSites",
                () -> assertThat(cloudStorage.list("")).contains(
                        "/some/test/path/sites/site/10/metadata.json",
                        "/some/test/path/sites/site/10/sites.json"
                ),
                () -> assertThat(reader.getAll()).containsExactly(SITE)
        );
    }

    @Test
    public void overridesPreviouslySyncedSitesWhenThereAreChanges() throws Exception {
        siteStoreFactory.getWriter(SCOPED_SITE_ID).upload(ImmutableList.of(SITE), null);

        StoreReader<Collection<Site>> reader = siteStoreFactory.getReader(SCOPED_SITE_ID);
        reader.loadContent();
        Long oldVersion = reader.getMetadata().getLong("version");

        Site updatedSite = new Site(SCOPED_SITE_ID, "site 1 updated", true);
        SiteSyncJob job = new SiteSyncJob(new MultiScopeStoreWriter<>(
                fileManager,
                siteStoreFactory,
                MultiScopeStoreWriter::areCollectionsEqual),
                ImmutableList.of(updatedSite),
                OPERATORS
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
        siteStoreFactory.getWriter(SCOPED_SITE_ID).upload(ImmutableList.of(SITE), null);
        siteStoreFactory.getGlobalWriter().upload(ImmutableList.of(SITE), null);

        StoreReader<Collection<Site>> reader = siteStoreFactory.getReader(SCOPED_SITE_ID);
        reader.loadContent();
        Long oldVersion = reader.getMetadata().getLong("version");

        SiteSyncJob job = new SiteSyncJob(new MultiScopeStoreWriter<>(
                fileManager,
                siteStoreFactory,
                MultiScopeStoreWriter::areCollectionsEqual),
                ImmutableList.of(SITE),
                OPERATORS
        );
        job.execute();

        reader.loadContent();

        assertAll(
                "doesNotSyncSitesThatAreNotChanged",
                () -> assertThat(reader.getAll()).containsExactly(SITE),
                () -> assertThat(reader.getMetadata().getLong("version")).isEqualTo(oldVersion)
        );
    }
}
