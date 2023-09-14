package com.uid2.admin.job.jobsync.keyset;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.AdminConst;
import com.uid2.admin.auth.AdminKeyset;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.InstantClock;
import com.uid2.admin.store.version.EpochVersionGenerator;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.AdminKeysetWriter;
import com.uid2.admin.store.writer.SiteStoreWriter;
import com.uid2.admin.store.writer.mocks.FileStorageMock;
import com.uid2.admin.store.reader.RotatingAdminKeysetStore;
import com.uid2.admin.store.writer.KeysetStoreWriter;
import com.uid2.admin.vertx.ObjectWriterFactory;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.auth.Keyset;
import com.uid2.shared.cloud.InMemoryStorageMock;
import com.uid2.shared.model.ClientType;
import com.uid2.shared.model.Site;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.reader.RotatingKeysetProvider;
import com.uid2.shared.store.reader.RotatingSiteStore;
import com.uid2.shared.store.scope.GlobalScope;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;


public class TestReplaceSharingtypesWithSitesJob {
    private InMemoryStorageMock cloudStorage = new InMemoryStorageMock();

    CloudPath globalKeysetMetadataPath = new CloudPath("path/to/keysets/metadata.json");
    GlobalScope keysetScope = new GlobalScope(globalKeysetMetadataPath);
    CloudPath globalAdminKeysetMetadataPath = new CloudPath("path/to/admin_keysets/metadata.json");
    GlobalScope adminKeysetScope = new GlobalScope(globalAdminKeysetMetadataPath);
    CloudPath globalSiteMetadataPath = new CloudPath("path/to/sites/metadata.json");
    GlobalScope siteScope = new GlobalScope(globalSiteMetadataPath);

    List<Site> sites = List.of(
            new Site(3, "site 3", true),
            new Site(4, "site 4", true,  Set.of(ClientType.DSP),  new HashSet<>()),
            new Site(5, "site 5", true,  Set.of(ClientType.ADVERTISER),  new HashSet<>()),
            new Site(6, "site 6", true,  Set.of(ClientType.DATA_PROVIDER),  new HashSet<>()),
            new Site(7, "site 7", true,  Set.of(ClientType.PUBLISHER),  new HashSet<>()),
            new Site(8, "site 8", true,  Set.of(ClientType.DSP, ClientType.ADVERTISER),  new HashSet<>()),
            new Site(9, "site 9", true,  Set.of(ClientType.DSP, ClientType.ADVERTISER, ClientType.PUBLISHER, ClientType.DATA_PROVIDER),  new HashSet<>())
    );

    Map<Integer, AdminKeyset> adminKeysets = Map.of(
            -2, new AdminKeyset(-2, -2, "master", null, 0L, true, true, Set.of()),
            -1, new AdminKeyset(-1, -1, "refresh", null, 0L, true, true, Set.of()),
            2, new AdminKeyset(2, 2, "Publisher General", null, 0L, true, true, Set.of()),
            3, new AdminKeyset(3, 3, "keyset_3", Set.of(), 0L, true, true, Set.of()),
            4, new AdminKeyset(4, 4, "keyset_4", Set.of(3, 5), 0L, true, true, Set.of()),
            5, new AdminKeyset(5, 5, "keyset_5", Set.of(3, 4), 0L, true, true, Set.of(ClientType.DSP)),
            6, new AdminKeyset(6, 6, "keyset_6", Set.of(), 0L, true, true, Set.of(ClientType.DSP)),
            7, new AdminKeyset(7, 7, "keyset_7", Set.of(), 0L, true, true, Set.of(ClientType.DSP, ClientType.ADVERTISER, ClientType.PUBLISHER, ClientType.DATA_PROVIDER)),
            8, new AdminKeyset(8, 8, "keyset_7", Set.of(3), 0L, true, true, Set.of(ClientType.ADVERTISER)),
            9, new AdminKeyset(9, 9, "keyset_8", Set.of(7), 0L, true, true, Set.of(ClientType.PUBLISHER))
    );

    Map<Integer, Keyset> expectedKeysets = Map.of(
            -2, new Keyset(-2, -2, "master", null, 0L, true, true),
            -1, new Keyset(-1, -1, "refresh", null, 0L, true, true),
            2, new Keyset(2, 2, "Publisher General", null, 0L, true, true),
            3, new Keyset(3, 3, "keyset_3", Set.of(), 0L, true, true),
            4, new Keyset(4, 4, "keyset_4", Set.of(3, 5), 0L, true, true),
            5, new Keyset(5, 5, "keyset_5", Set.of(3, 4, 8, 9), 0L, true, true),
            6, new Keyset(6, 6, "keyset_6", Set.of(4, 8, 9), 0L, true, true),
            7, new Keyset(7, 7, "keyset_7", Set.of(4, 5, 6, 7, 8, 9), 0L, true, true),
            8, new Keyset(8, 8, "keyset_7", Set.of(3, 5, 8, 9), 0L, true, true),
            9, new Keyset(9, 9, "keyset_8", Set.of(7, 9), 0L, true, true)
    );

    FileStorageMock fileStorage = new FileStorageMock(cloudStorage);
    FileManager fileManager = new FileManager(cloudStorage,  fileStorage);
    Clock clock = new InstantClock();
    VersionGenerator versionGenerator = new EpochVersionGenerator(clock);
    ObjectWriter objectWriter = ObjectWriterFactory.build();
    RotatingAdminKeysetStore adminKeysetStore = new RotatingAdminKeysetStore(cloudStorage, adminKeysetScope);
    AdminKeysetWriter adminKeysetStoreWriter = new AdminKeysetWriter(adminKeysetStore, fileManager, objectWriter, versionGenerator,  clock, adminKeysetScope);
    RotatingKeysetProvider keysetProvider = new RotatingKeysetProvider(cloudStorage, keysetScope);
    KeysetStoreWriter keysetStoreWriter = new KeysetStoreWriter(keysetProvider, fileManager, objectWriter, versionGenerator,  clock, keysetScope, true);

    RotatingSiteStore rotatingSiteStore = new RotatingSiteStore(cloudStorage, siteScope);
    SiteStoreWriter siteStoreWriter = new SiteStoreWriter(rotatingSiteStore, fileManager, objectWriter, versionGenerator, clock, siteScope);

    @Test
    public void testExecute() throws Exception {
        JsonObject config = new JsonObject();
        config.put(AdminConst.enableKeysetConfigProp, true);
        WriteLock writeLock = new WriteLock();
        adminKeysetStoreWriter.upload(adminKeysets, null);
        siteStoreWriter.upload(sites, null);
        keysetStoreWriter.upload(new HashMap<>(), null);

        ReplaceSharingTypesWithSitesJob job = new ReplaceSharingTypesWithSitesJob(config, writeLock, adminKeysetStore, keysetProvider, keysetStoreWriter, rotatingSiteStore);

        job.execute();
        keysetProvider.loadContent();
        Map<Integer, Keyset> results = keysetProvider.getAll();

        for(Integer keysetID : expectedKeysets.keySet()) {
            Keyset result = results.get(keysetID);
            Keyset expected = expectedKeysets.get(keysetID);

            assertTrue(expected.equals(result));
        }
    }

}
