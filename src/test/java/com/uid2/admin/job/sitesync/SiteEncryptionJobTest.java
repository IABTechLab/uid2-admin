package com.uid2.admin.job.sitesync;

import com.uid2.admin.job.EncryptionJob.SiteEncryptionJob;
import com.uid2.admin.model.PrivateSiteDataMap;
import com.uid2.admin.store.MultiScopeStoreWriter;
import com.uid2.admin.util.PrivateSiteUtil;
import com.uid2.admin.util.PublicSiteUtil;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.model.Site;
import com.uid2.shared.store.reader.StoreReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class SiteEncryptionJobTest {

    private MultiScopeStoreWriter<Collection<Site>> multiScopeStoreWriter;
    private Collection<Site> globalSites;
    private Collection<OperatorKey> globalOperators;
    private SiteEncryptionJob siteEncryptionJob;

    @BeforeEach
    void setUp() {
        multiScopeStoreWriter = mock(MultiScopeStoreWriter.class);
        globalSites = Collections.emptyList();
        globalOperators = Collections.emptyList();
        siteEncryptionJob = new SiteEncryptionJob(multiScopeStoreWriter, globalSites, globalOperators);
    }

    @Test
    void testGetId() {
        assertEquals("cloud-encryption-sync-sites", siteEncryptionJob.getId());
    }

    @Test
    void testExecute() throws Exception {
        PrivateSiteDataMap<Site> privateSites = mock(PrivateSiteDataMap.class);
        PrivateSiteDataMap<Site> publicSites = mock(PrivateSiteDataMap.class);

        try (MockedStatic<PrivateSiteUtil> privateSiteUtilMockedStatic = Mockito.mockStatic(PrivateSiteUtil.class);
             MockedStatic<PublicSiteUtil> publicSiteUtilMockedStatic = Mockito.mockStatic(PublicSiteUtil.class)) {

            privateSiteUtilMockedStatic.when(() -> PrivateSiteUtil.getSites(globalSites, globalOperators)).thenReturn(privateSites);
            publicSiteUtilMockedStatic.when(() -> PublicSiteUtil.getPublicSites(globalSites, globalOperators)).thenReturn(publicSites);

            siteEncryptionJob.execute();

            ArgumentCaptor<PrivateSiteDataMap> privateCaptor = ArgumentCaptor.forClass(PrivateSiteDataMap.class);
            verify(multiScopeStoreWriter).uploadPrivateWithEncryption(privateCaptor.capture(), eq(null));
            assertEquals(privateSites, privateCaptor.getValue());

            ArgumentCaptor<PrivateSiteDataMap> publicCaptor = ArgumentCaptor.forClass(PrivateSiteDataMap.class);
            verify(multiScopeStoreWriter).uploadPublicWithEncryption(publicCaptor.capture(), eq(null));
            assertEquals(publicSites, publicCaptor.getValue());
        }
    }

    @Test
    void writesNoSitesIfThereAreNoSites() throws Exception {
        MultiScopeStoreWriter<Collection<Site>> emptyMultiScopeStoreWriter = mock(MultiScopeStoreWriter.class);
        SiteEncryptionJob job = new SiteEncryptionJob(emptyMultiScopeStoreWriter, Collections.emptyList(), Collections.emptyList());

        try (MockedStatic<PrivateSiteUtil> privateSiteUtilMock = mockStatic(PrivateSiteUtil.class);
             MockedStatic<PublicSiteUtil> publicSiteUtilMock = mockStatic(PublicSiteUtil.class)) {

            PrivateSiteDataMap<Site> emptyPrivateMap = mock(PrivateSiteDataMap.class);
            PrivateSiteDataMap<Site> emptyPublicMap = mock(PrivateSiteDataMap.class);
            when(emptyPrivateMap.isEmpty()).thenReturn(true);
            when(emptyPublicMap.isEmpty()).thenReturn(true);

            privateSiteUtilMock.when(() -> PrivateSiteUtil.getSites(anyCollection(), anyCollection())).thenReturn(emptyPrivateMap);
            publicSiteUtilMock.when(() -> PublicSiteUtil.getPublicSites(anyCollection(), anyCollection())).thenReturn(emptyPublicMap);

            // Execute
            job.execute();

            // Verify
            verify(emptyMultiScopeStoreWriter).uploadPrivateWithEncryption(argThat(map -> map.isEmpty()), eq(null));
            verify(emptyMultiScopeStoreWriter).uploadPublicWithEncryption(argThat(map -> map.isEmpty()), eq(null));

            // Verify that getSites and getPublicSites were called with empty collections
            privateSiteUtilMock.verify(() -> PrivateSiteUtil.getSites(eq(Collections.emptyList()), eq(Collections.emptyList())));
            publicSiteUtilMock.verify(() -> PublicSiteUtil.getPublicSites(eq(Collections.emptyList()), eq(Collections.emptyList())));
        }
    }

    @Test
    void syncsNewSites() throws Exception {
        Site site = mock(Site.class);
        Collection<Site> sites = Collections.singletonList(site);
        SiteEncryptionJob job = new SiteEncryptionJob(multiScopeStoreWriter, sites, globalOperators);

        PrivateSiteDataMap<Site> expectedPrivateSites = mock(PrivateSiteDataMap.class);
        PrivateSiteDataMap<Site> expectedPublicSites = mock(PrivateSiteDataMap.class);

        try (MockedStatic<PrivateSiteUtil> privateSiteUtilMockedStatic = Mockito.mockStatic(PrivateSiteUtil.class);
             MockedStatic<PublicSiteUtil> publicSiteUtilMockedStatic = Mockito.mockStatic(PublicSiteUtil.class)) {

            privateSiteUtilMockedStatic.when(() -> PrivateSiteUtil.getSites(sites, globalOperators)).thenReturn(expectedPrivateSites);
            publicSiteUtilMockedStatic.when(() -> PublicSiteUtil.getPublicSites(sites, globalOperators)).thenReturn(expectedPublicSites);

            job.execute();

            verify(multiScopeStoreWriter).uploadPrivateWithEncryption(eq(expectedPrivateSites), eq(null));
            verify(multiScopeStoreWriter).uploadPublicWithEncryption(eq(expectedPublicSites), eq(null));
        }
    }

    @Test
    void overwritesExistingSites() throws Exception {
        Site site1 = mock(Site.class);
        Site site2 = mock(Site.class);
        Collection<Site> initialSites = Collections.singletonList(site1);
        Collection<Site> updatedSites = Arrays.asList(site1, site2);

        SiteEncryptionJob initialJob = new SiteEncryptionJob(multiScopeStoreWriter, initialSites, globalOperators);
        SiteEncryptionJob updatedJob = new SiteEncryptionJob(multiScopeStoreWriter, updatedSites, globalOperators);

        PrivateSiteDataMap<Site> initialPrivateSites = mock(PrivateSiteDataMap.class);
        PrivateSiteDataMap<Site> initialPublicSites = mock(PrivateSiteDataMap.class);
        PrivateSiteDataMap<Site> updatedPrivateSites = mock(PrivateSiteDataMap.class);
        PrivateSiteDataMap<Site> updatedPublicSites = mock(PrivateSiteDataMap.class);

        try (MockedStatic<PrivateSiteUtil> privateSiteUtilMockedStatic = Mockito.mockStatic(PrivateSiteUtil.class);
             MockedStatic<PublicSiteUtil> publicSiteUtilMockedStatic = Mockito.mockStatic(PublicSiteUtil.class)) {

            privateSiteUtilMockedStatic.when(() -> PrivateSiteUtil.getSites(initialSites, globalOperators)).thenReturn(initialPrivateSites);
            publicSiteUtilMockedStatic.when(() -> PublicSiteUtil.getPublicSites(initialSites, globalOperators)).thenReturn(initialPublicSites);
            privateSiteUtilMockedStatic.when(() -> PrivateSiteUtil.getSites(updatedSites, globalOperators)).thenReturn(updatedPrivateSites);
            publicSiteUtilMockedStatic.when(() -> PublicSiteUtil.getPublicSites(updatedSites, globalOperators)).thenReturn(updatedPublicSites);

            initialJob.execute();

            verify(multiScopeStoreWriter).uploadPrivateWithEncryption(eq(initialPrivateSites), eq(null));
            verify(multiScopeStoreWriter).uploadPublicWithEncryption(eq(initialPublicSites), eq(null));

            updatedJob.execute();

            verify(multiScopeStoreWriter).uploadPrivateWithEncryption(eq(updatedPrivateSites), eq(null));
            verify(multiScopeStoreWriter).uploadPublicWithEncryption(eq(updatedPublicSites), eq(null));
        }
    }
}

