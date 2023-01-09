package com.uid2.admin.util;

import com.uid2.admin.model.Site;
import com.uid2.admin.model.SyncedSiteDataMap;
import com.uid2.shared.Const;
import com.uid2.shared.auth.ClientKey;
import com.uid2.shared.auth.EncryptionKeyAcl;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.EncryptionKey;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class SyncedSiteDataGeneratorTest {

    @Test
    public void testGenerateClientKeyData() {
        final OperatorKey[] operatorKeys = {
                new OperatorKey("key3", "name3", "contact3", "aws-nitro", 2, false, 3, false),
                new OperatorKey("key4", "name4", "contact4", "aws-nitro", 2, false, 4, false),
                new OperatorKey("key5", "name5", "contact5", "aws-nitro", 2, false, 5, true),
        };
        final ClientKey[] clientKeys = {
                new ClientKey("key3_1", "", "name3_1", "contact3_1", Instant.now(), new HashSet<>(), 3, false),
                new ClientKey("key3_2", "", "name3_2", "contact3_2", Instant.now(), new HashSet<>(), 3, true),
                new ClientKey("key5", "", "name5", "contact5", Instant.now(), new HashSet<>(), 5, false),
                new ClientKey("key6", "", "name6", "contact6", Instant.now(), new HashSet<>(), 5, false)
        };

        final SyncedSiteDataMap<ClientKey> result = SyncedSiteDataGenerator.generateClientKeyData(
                Arrays.asList(operatorKeys), Arrays.asList(clientKeys));

        final Set<ClientKey> expectedSite3Clients = new HashSet<>();
        expectedSite3Clients.add(clientKeys[0]);
        final Set<ClientKey> expectedSite4Clients = new HashSet<>();
        final SyncedSiteDataMap<ClientKey> expected = new SyncedSiteDataMap<>();
        expected.put(3, expectedSite3Clients);
        expected.put(4, expectedSite4Clients);

        assertEquals(expected, result);
    }

    @Test
    public void testGenerateEncryptionKeyData() {
        final Set<Role> readerRole = new HashSet<>();
        readerRole.add(Role.ID_READER);

        final OperatorKey[] operatorKeys = {
                new OperatorKey("key3", "name3", "contact3", "aws-nitro", 2, false, 3, false),
                new OperatorKey("key4", "name4", "contact4", "aws-nitro", 2, false, 4, false),
                new OperatorKey("key5", "name5", "contact5", "aws-nitro", 2, false, 5, true),
                new OperatorKey("key6", "name6", "contact6", "aws-nitro", 2, false, 6, false)
        };
        final EncryptionKey[] encryptionKeys = {
                new EncryptionKey(1, new byte[]{}, Instant.now(), Instant.now(), Instant.now(), Const.Data.RefreshKeySiteId),
                new EncryptionKey(2, new byte[]{}, Instant.now(), Instant.now(), Instant.now(), 3),
                new EncryptionKey(3, new byte[]{}, Instant.now(), Instant.now(), Instant.now(), 4),
                new EncryptionKey(4, new byte[]{}, Instant.now(), Instant.now(), Instant.now(), 5),
                new EncryptionKey(5, new byte[]{}, Instant.now(), Instant.now(), Instant.now(), 6)
        };
        final ClientKey[] clientKeys = {
                new ClientKey("key3", "", "name3", "contact3", Instant.now(), readerRole, 3, false),
                new ClientKey("key4", "", "name4", "contact4", Instant.now(), readerRole, 4, false)
        };

        final Set<Integer> site3Whitelist = new HashSet<>();
        site3Whitelist.add(4);
        final Set<Integer> site4Blacklist = new HashSet<>();
        site4Blacklist.add(3);
        final Set<Integer> site6Blacklist = new HashSet<>();
        site6Blacklist.add(3);
        site6Blacklist.add(4);
        site6Blacklist.add(5);
        site6Blacklist.add(6);
        final Map<Integer, EncryptionKeyAcl> acls = new HashMap<>();
        acls.put(3, new EncryptionKeyAcl(true, site3Whitelist));
        acls.put(4, new EncryptionKeyAcl(false, site4Blacklist));
        acls.put(6, new EncryptionKeyAcl(true, site6Blacklist));

        final SyncedSiteDataMap<EncryptionKey> result = SyncedSiteDataGenerator.generateEncryptionKeyData(
                Arrays.asList(operatorKeys), Arrays.asList(encryptionKeys), acls, Arrays.asList(clientKeys));

        final Set<EncryptionKey> expectedSite3EncryptionKeys = new HashSet<>();
        expectedSite3EncryptionKeys.add(encryptionKeys[0]);
        expectedSite3EncryptionKeys.add(encryptionKeys[1]);
        final Set<EncryptionKey> expectedSite4EncryptionKeys = new HashSet<>();
        expectedSite4EncryptionKeys.add(encryptionKeys[0]);
        expectedSite4EncryptionKeys.add(encryptionKeys[1]);
        expectedSite4EncryptionKeys.add(encryptionKeys[2]);
        final Set<EncryptionKey> expectedSite6EncryptionKeys = new HashSet<>();
        expectedSite6EncryptionKeys.add(encryptionKeys[0]);
        expectedSite6EncryptionKeys.add(encryptionKeys[2]);
        expectedSite6EncryptionKeys.add(encryptionKeys[4]);
        final SyncedSiteDataMap<EncryptionKey> expected = new SyncedSiteDataMap<>();
        expected.put(3, expectedSite3EncryptionKeys);
        expected.put(4, expectedSite4EncryptionKeys);
        expected.put(6, expectedSite6EncryptionKeys);

        assertEquals(expected, result);
    }

    @Test
    public void testGenerateEncryptionKeyAclData() {
        final OperatorKey[] operatorKeys = {
                new OperatorKey("key3", "name3", "contact3", "aws-nitro", 2, false, 3, false),
                new OperatorKey("key4", "name4", "contact4", "aws-nitro", 2, false, 4, false),
                new OperatorKey("key5", "name5", "contact5", "aws-nitro", 2, false, 5, true),
                new OperatorKey("key6", "name6", "contact6", "aws-nitro", 2, false, 6, false)
        };

        final Set<Integer> site3Whitelist = new HashSet<>();
        site3Whitelist.add(4);
        final Set<Integer> site4Blacklist = new HashSet<>();
        site4Blacklist.add(3);
        final Map<Integer, EncryptionKeyAcl> acls = new HashMap<>();
        acls.put(3, new EncryptionKeyAcl(true, site3Whitelist));
        acls.put(4, new EncryptionKeyAcl(false, site4Blacklist));

        SyncedSiteDataMap<EncryptionKeyAcl> result = SyncedSiteDataGenerator.generateEncryptionKeyAclData(
                Arrays.asList(operatorKeys), acls);

        final Set<EncryptionKeyAcl> site3EncryptionKeyAcls = new HashSet<>();
        site3EncryptionKeyAcls.add(acls.get(3));
        final Set<EncryptionKeyAcl> site4EncryptionKeyAcls = new HashSet<>();
        site4EncryptionKeyAcls.add(acls.get(3));
        site4EncryptionKeyAcls.add(acls.get(4));
        final Set<EncryptionKeyAcl> site6EncryptionKeyAcls = new HashSet<>();
        site6EncryptionKeyAcls.add(acls.get(4));
        final SyncedSiteDataMap<EncryptionKeyAcl> expected = new SyncedSiteDataMap<>();
        expected.put(3, site3EncryptionKeyAcls);
        expected.put(4, site4EncryptionKeyAcls);
        expected.put(6, site6EncryptionKeyAcls);

        assertEquals(expected, result);
    }

    @Test
    public void testGenerateSiteData() {
        final Site[] sites = {
                new Site(Const.Data.AdvertisingTokenSiteId, "1", true),
                new Site(3, "2", true),
                new Site(4, "3", true),
                new Site(5, "4", true)
        };
        final OperatorKey[] publicOperatorKeys = {
                new OperatorKey("key2", "name2", "contact2", "aws-nitro", 2, false, Const.Data.AdvertisingTokenSiteId, true),
                new OperatorKey("key5", "name5", "contact5", "aws-nitro", 5, false, 5, true),
        };
        final OperatorKey[] privateOperatorKeys = {
                new OperatorKey("key3", "name3", "contact3", "aws-nitro", 3, false, 3, false),
                new OperatorKey("key4", "name4", "contact4", "aws-nitro", 4, false, 4, false),
        };
        final List<OperatorKey> allOperatorKeys = new ArrayList<>(Arrays.asList(publicOperatorKeys));
        allOperatorKeys.addAll(Arrays.asList(privateOperatorKeys));

        final SyncedSiteDataMap<Site> result = SyncedSiteDataGenerator.generateSiteData(
                Arrays.asList(sites), allOperatorKeys);

        final Set<Site> expectedSite3Sites = new HashSet<>();
        expectedSite3Sites.add(sites[0]);
        expectedSite3Sites.add(sites[1]);
        final Set<Site> expectedSite4Sites = new HashSet<>();
        expectedSite4Sites.add(sites[0]);
        expectedSite4Sites.add(sites[2]);
        final SyncedSiteDataMap<Site> expected = new SyncedSiteDataMap<>();
        expected.put(3, expectedSite3Sites);
        expected.put(4, expectedSite4Sites);

        assertEquals(expected, result);
    }

}
