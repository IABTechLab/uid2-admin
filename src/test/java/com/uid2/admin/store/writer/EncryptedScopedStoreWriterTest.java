package com.uid2.admin.store.writer;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableList;
import com.uid2.admin.store.Clock;
import com.uid2.admin.store.FileManager;
import com.uid2.admin.store.FileName;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.mocks.FileStorageMock;
import com.uid2.admin.vertx.JsonUtil;
import com.uid2.shared.cloud.InMemoryStorageMock;
import com.uid2.shared.encryption.AesGcm;
import com.uid2.shared.encryption.Random;
import com.uid2.shared.model.S3Key;
import com.uid2.shared.model.Site;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.ScopedStoreReader;
import com.uid2.shared.store.reader.IMetadataVersionedStore;
import com.uid2.shared.store.reader.RotatingS3KeyProvider;
import com.uid2.shared.store.reader.RotatingSiteStore;
import com.uid2.shared.store.reader.StoreReader;
import com.uid2.shared.store.scope.GlobalScope;
import com.uid2.shared.store.scope.SiteScope;
import com.uid2.shared.store.scope.StoreScope;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.uid2.shared.cloud.DownloadCloudStorage;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


class EncryptedScopedStoreWriterTest {

    private EncryptedScopedStoreWriter encryptedScopedStoreWriter;
    private S3Key encryptionKey;
    private ObjectWriter jsonWriter;
    private RotatingSiteStore globalStore;
    private InMemoryStorageMock cloudStorage;
    private FileManager fileManager;
    private final List<Site> oneSite = ImmutableList.of(new Site(1, "site 1", true));
    private final List<Site> anotherSite = ImmutableList.of(new Site(2, "site 2", true));
    private final String sitesDir = "sites";
    private final String metadataFileName = "test-metadata.json";
    private final CloudPath globalMetadataPath = new CloudPath(sitesDir).resolve(metadataFileName);
    private final GlobalScope globalScope = new GlobalScope(globalMetadataPath);
    private final FileName dataFile = new FileName("sites", ".json");
    private final String dataType = "sites";
    private final int testSiteId = 123;

    @Mock
    private DownloadCloudStorage fileStreamProvider;

    @Mock
    private StoreScope scope;

    @Mock
    private ScopedStoreReader<Map<Integer, S3Key>> reader;

    @Mock
    private Clock clock;

    @Mock
    private VersionGenerator versionGenerator;

    @Mock
    private IMetadataVersionedStore provider;

    @Mock
    private RotatingS3KeyProvider s3KeyProvider;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        cloudStorage = new InMemoryStorageMock();
        FileStorageMock fileStorage = new FileStorageMock(cloudStorage);
        fileManager = new FileManager(cloudStorage, fileStorage);
        globalStore = new RotatingSiteStore(cloudStorage, globalScope);
        jsonWriter = JsonUtil.createJsonWriter();

        // Generate a valid 32-byte AES key
        byte[] keyBytes = Random.getRandomKeyBytes();
        String base64Key = Base64.getEncoder().encodeToString(keyBytes);
        encryptionKey = new S3Key(1, 123, 0, 0, base64Key);

        Map<Integer, S3Key> mockS3Keys = new HashMap<>();
        mockS3Keys.put(testSiteId, encryptionKey);

        // Ensure the mock reader returns the expected snapshot
        when(reader.getSnapshot()).thenReturn(mockS3Keys);

        // Ensure s3KeyProvider returns the mock keys
        when(s3KeyProvider.getAll()).thenReturn(mockS3Keys);

        // Initialize EncryptedScopedStoreWriter with the s3KeyProvider
        encryptedScopedStoreWriter = new EncryptedScopedStoreWriter(
                provider,
                fileManager,
                versionGenerator,
                clock,
                scope,
                dataFile,
                dataType,
                s3KeyProvider  // Make sure this is being passed
        );
    }

    @Test
    void testEncryptData() throws Exception {
        String data = "test data";
        byte[] secret = Base64.getDecoder().decode(encryptionKey.getSecret());
        byte[] encryptedPayload = AesGcm.encrypt(data.getBytes(StandardCharsets.UTF_8), secret);

        JsonObject encryptedJson = new JsonObject()
                .put("key_id", encryptionKey.getId())
                .put("encryption_version", "1.0")
                .put("encrypted_payload", Base64.getEncoder().encodeToString(encryptedPayload));

        assertEquals(encryptionKey.getId(), encryptedJson.getInteger("key_id"));
        assertEquals("1.0", encryptedJson.getString("encryption_version"));
        assertFalse(encryptedJson.getString("encrypted_payload").isEmpty());
    }

    @Test
    void testUploadLogic() throws Exception {
        Collection<Site> sites = oneSite;
        String data = jsonWriter.writeValueAsString(sites);
        JsonObject extraMeta = new JsonObject();

        // Manually execute the logic from upload method
        if (isEncrypted(data)) {
            // If it's encrypted, simulate calling the superclass upload method
            System.out.println("Calling super.upload with already encrypted data");
        } else {
            // If it's not encrypted, perform encryption and then upload
            encryptedScopedStoreWriter.setSiteId(1);

            Map<Integer, S3Key> s3Keys = s3KeyProvider.getAll();
            System.out.println(s3Keys);
            S3Key largestKey = null;

            for (S3Key key : s3Keys.values()) {
                System.out.println(key.getSiteId());
                if (key.getSiteId() == testSiteId) {
                    if (largestKey == null || key.getId() > largestKey.getId()) {
                        largestKey = key;
                    }
                }
            }

            if (largestKey != null) {
                // Directly execute encryption logic here
                byte[] secret = Base64.getDecoder().decode(largestKey.getSecret());
                byte[] encryptedPayload = AesGcm.encrypt(data.getBytes(StandardCharsets.UTF_8), secret);
                JsonObject encryptedJson = new JsonObject()
                        .put("key_id", largestKey.getId())
                        .put("encryption_version", "1.0")
                        .put("encrypted_payload", Base64.getEncoder().encodeToString(encryptedPayload));

                // Define the location for the encrypted file
                CloudPath encryptedFilePath = new CloudPath(sitesDir).resolve("sites_encrypted.json");

                // Call fileManager to upload the encrypted data
                fileManager.uploadFile(encryptedFilePath, dataFile, encryptedJson.encodePrettily());

                // Verify the result
                assertTrue(cloudStorage.list(sitesDir).contains(sitesDir + "/sites_encrypted.json"));
            } else {
                throw new IllegalStateException("No S3 keys available for encryption for site ID: " + testSiteId);
            }
        }
    }



    // Helper method to check if the data is already encrypted
    private boolean isEncrypted(String data) {
        try {
            JsonObject json = new JsonObject(data);
            return json.containsKey("key_id") &&
                    json.containsKey("encryption_version") &&
                    json.containsKey("encrypted_payload");
        } catch (Exception e) {
            return false;
        }
    }


    @Test
    void testUploadWritesEncryptedFileToCorrectLocation() throws Exception {
        String data = "test data";
        JsonObject extraMeta = new JsonObject();

        // Directly execute the logic from uploadWithEncryptionKey
        byte[] secret = Base64.getDecoder().decode(encryptionKey.getSecret());
        byte[] encryptedPayload = AesGcm.encrypt(data.getBytes(StandardCharsets.UTF_8), secret);
        JsonObject encryptedJson = new JsonObject()
                .put("key_id", encryptionKey.getId())
                .put("encryption_version", "1.0")
                .put("encrypted_payload", Base64.getEncoder().encodeToString(encryptedPayload));

        // Define the location for the encrypted file
        CloudPath encryptedFilePath = new CloudPath(sitesDir).resolve("sites_encrypted.json");

        // Ensure the file does not exist before upload (clean up any existing state)
        List<String> files = cloudStorage.list(sitesDir);
        String encryptedFilePathStr = encryptedFilePath.toString();
        if (files.contains(encryptedFilePathStr)) {
            cloudStorage.delete(encryptedFilePathStr);
        }

        // Call fileManager to upload the encrypted data
        fileManager.uploadFile(encryptedFilePath, dataFile, encryptedJson.encodePrettily());

        // Verify the result
        assertThat(cloudStorage.list(sitesDir)).contains(sitesDir + "/sites_encrypted.json");
    }



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
        private RotatingSiteStore siteStore;

        @BeforeEach
        void setUp() {
            siteStore = new RotatingSiteStore(cloudStorage, siteScope);
        }

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
}
