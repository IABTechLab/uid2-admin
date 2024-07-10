package com.uid2.admin.job.sitesync;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableList;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.InstantClock;
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
import org.junit.jupiter.api.BeforeEach;

import java.util.Set;

public class SiteEncryptionTest {
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
                fileManager
        );
    }

}
