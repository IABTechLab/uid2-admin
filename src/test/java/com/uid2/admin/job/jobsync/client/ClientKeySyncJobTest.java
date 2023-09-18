package com.uid2.admin.job.jobsync.client;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.InstantClock;
import com.uid2.admin.store.MultiScopeStoreWriter;
import com.uid2.admin.store.factory.ClientKeyStoreFactory;
import com.uid2.admin.store.version.EpochVersionGenerator;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.mocks.FileStorageMock;
import com.uid2.admin.vertx.ObjectWriterFactory;
import com.uid2.shared.auth.*;
import com.uid2.shared.cloud.InMemoryStorageMock;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.reader.StoreReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

public class ClientKeySyncJobTest {
    private static final Instant NOW = Instant.now();
    private static final CloudPath GLOBAL_SITE_METADATA_PATH = new CloudPath("/some/test/path/clients/metadata.json");
    private static final ObjectWriter OBJECT_WRITER = ObjectWriterFactory.build();
    private static final Integer SCOPED_SITE_ID = 10;
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
                    Set.of(Role.OPERATOR),
                    OperatorType.PRIVATE));
    private static final ClientKey CLIENT = new ClientKey(
            "key",
            "keyHash",
            "keySalt",
            "secret",
            "name",
            "contact",
            NOW,
            ImmutableSet.of(Role.OPERATOR),
            SCOPED_SITE_ID,
            false);

    private FileManager fileManager;
    private ClientKeyStoreFactory clientKeyStoreFactory;

    @BeforeEach
    public void setup() {
        InMemoryStorageMock cloudStorage = new InMemoryStorageMock();
        FileStorageMock fileStorage = new FileStorageMock(cloudStorage);
        Clock clock = new InstantClock();
        VersionGenerator versionGenerator = new EpochVersionGenerator(clock);
        fileManager = new FileManager(cloudStorage, fileStorage);
        clientKeyStoreFactory = new ClientKeyStoreFactory(
                cloudStorage,
                GLOBAL_SITE_METADATA_PATH,
                OBJECT_WRITER,
                versionGenerator,
                clock,
                fileManager
        );
    }

    @Test
    public void doesNotSyncClientsThatAreNotChanged() throws Exception {
        clientKeyStoreFactory.getWriter(SCOPED_SITE_ID).upload(ImmutableList.of(CLIENT), null);
        clientKeyStoreFactory.getGlobalWriter().upload(ImmutableList.of(CLIENT), null);

        StoreReader<Collection<ClientKey>> reader = clientKeyStoreFactory.getReader(SCOPED_SITE_ID);
        reader.loadContent();
        Long oldVersion = reader.getMetadata().getLong("version");

        ClientKeySyncJob job = new ClientKeySyncJob(new MultiScopeStoreWriter<>(
                fileManager,
                clientKeyStoreFactory,
                MultiScopeStoreWriter::areCollectionsEqual),
                ImmutableList.of(CLIENT), OPERATORS
        );
        job.execute();

        reader.loadContent();

        assertAll(
                "doesNotSyncClientsThatAreNotChanged",
                () -> assertThat(reader.getAll()).containsExactly(CLIENT),
                () -> assertThat(reader.getMetadata().getLong("version")).isEqualTo(oldVersion));
    }
}
