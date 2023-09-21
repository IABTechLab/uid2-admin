package com.uid2.admin.job.jobsync.client;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableList;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.InstantClock;
import com.uid2.admin.store.MultiScopeStoreWriter;
import com.uid2.admin.store.factory.ClientKeyStoreFactory;
import com.uid2.admin.store.version.EpochVersionGenerator;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.mocks.FileStorageMock;
import com.uid2.admin.vertx.JsonUtil;
import com.uid2.shared.auth.*;
import com.uid2.shared.cloud.InMemoryStorageMock;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.reader.StoreReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class ClientKeySyncJobTest {
    private final CloudPath globalSiteMetadataPath = new CloudPath("/some/test/path/clients/metadata.json");
    private final ObjectWriter objectWriter = JsonUtil.createJsonWriter();
    private final Integer scopedSiteId = 10;
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
                    OperatorType.PRIVATE
            )
    );
    private final ClientKey client = new ClientKey(
            "key",
            "keyHash",
            "keySalt",
            "secret",
            "name",
            "contact",
            Instant.MIN,
            Set.of(Role.OPERATOR),
            scopedSiteId,
            false
    );

    private ClientKeyStoreFactory clientKeyStoreFactory;
    private FileManager fileManager;

    @BeforeEach
    public void setup() {
        InMemoryStorageMock cloudStorage = new InMemoryStorageMock();
        FileStorageMock fileStorage = new FileStorageMock(cloudStorage);
        Clock clock = new InstantClock();
        VersionGenerator versionGenerator = new EpochVersionGenerator(clock);

        fileManager = new FileManager(cloudStorage, fileStorage);
        clientKeyStoreFactory = new ClientKeyStoreFactory(
                cloudStorage,
                globalSiteMetadataPath,
                objectWriter,
                versionGenerator,
                clock,
                fileManager
        );
    }

    @Test
    public void doesNotSyncClientsThatAreNotChanged() throws Exception {
        clientKeyStoreFactory.getWriter(scopedSiteId).upload(List.of(client), null);
        clientKeyStoreFactory.getGlobalWriter().upload(List.of(client), null);

        StoreReader<Collection<ClientKey>> reader = clientKeyStoreFactory.getReader(scopedSiteId);
        reader.loadContent();
        Long oldVersion = reader.getMetadata().getLong("version");

        ClientKeySyncJob job = new ClientKeySyncJob(new MultiScopeStoreWriter<>(
                fileManager,
                clientKeyStoreFactory,
                MultiScopeStoreWriter::areCollectionsEqual),
                List.of(client),
                operators
        );
        job.execute();

        reader.loadContent();

        assertAll(
                "doesNotSyncClientsThatAreNotChanged",
                () -> assertThat(reader.getAll()).containsExactly(client),
                () -> assertThat(reader.getMetadata().getLong("version")).isEqualTo(oldVersion)
        );
    }
}
