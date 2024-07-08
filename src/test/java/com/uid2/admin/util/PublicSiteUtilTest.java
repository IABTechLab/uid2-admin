package com.uid2.admin.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.uid2.admin.legacy.LegacyClientKey;
import com.uid2.admin.model.PrivateSiteDataMap;
import com.uid2.shared.Const;
import com.uid2.shared.auth.EncryptionKeyAcl;
import com.uid2.shared.auth.Keyset;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.auth.OperatorType;
import com.uid2.shared.model.EncryptionKey;
import com.uid2.shared.model.KeysetKey;
import com.uid2.shared.model.Site;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PublicSiteUtilTest {
    private static final int siteId1 = 1;
    private static final int siteId2 = 2;
    private static final int siteId3 = 3;
    private static final int siteId4 = 4;

    private final Set<OperatorKey> noOperators = ImmutableSet.of();
    private final Set<LegacyClientKey> noClients = ImmutableSet.of();
    private final Set<EncryptionKey> noKeys = ImmutableSet.of();
    private final Set<KeysetKey> noKeysetKeys = ImmutableSet.of();
    private final Map<Integer, EncryptionKeyAcl> noAcls = ImmutableMap.of();

    @Test
    void testGetPublicSitesMap() {
        ImmutableList<OperatorKey> operators = ImmutableList.of(
                new OperatorBuilder()
                        .withSiteId(10)
                        .withType(OperatorType.PUBLIC)
                        .build(),
                new OperatorBuilder()
                        .withSiteId(11)
                        .withType(OperatorType.PUBLIC)
                        .build(),
                new OperatorBuilder()
                        .withSiteId(12)
                        .withType(OperatorType.PRIVATE)
                        .build()
        );

        PrivateSiteDataMap<String> result = PublicSiteUtil.getPublicSitesMap(operators);

        PrivateSiteDataMap<String> expected = new PrivateSiteDataMap<>();
        expected.put(10, new HashSet<>());
        expected.put(11, new HashSet<>());

        assertEquals(expected, result);
    }

    @Nested
    class PublicSites {
        @Test
        public void returnsNoSitesForNoData() {
            PrivateSiteDataMap<Site> actual = PublicSiteUtil.getPublicSites(ImmutableSet.of(), ImmutableSet.of());

            PrivateSiteDataMap<Site> expected = new PrivateSiteDataMap<>();
            assertEquals(expected, actual);
        }

        @Test
        public void returnsSitesForPublicOperators() {
            ImmutableList<OperatorKey> operators = ImmutableList.of(
                    new OperatorBuilder()
                            .withSiteId(siteId1)
                            .withType(OperatorType.PUBLIC)
                            .build(),
                    new OperatorBuilder()
                            .withSiteId(siteId2)
                            .withType(OperatorType.PUBLIC)
                            .build()
            );

            Set<Site> sites = ImmutableSet.of(
                    new Site(siteId1, "Site 1", true),
                    new Site(siteId2, "Site 2", true)
            );

            PrivateSiteDataMap<Site> actual = PublicSiteUtil.getPublicSites(sites, operators);

            PrivateSiteDataMap<Site> expected = new PrivateSiteDataMap<>();
            expected.put(siteId1, ImmutableSet.of(
                    new Site(siteId1, "Site 1", true),
                    new Site(siteId2, "Site 2", true)
            ));
            expected.put(siteId2, ImmutableSet.of(
                    new Site(siteId1, "Site 1", true),
                    new Site(siteId2, "Site 2", true)
            ));

            assertEquals(expected, actual);
        }
    }

    @Nested
    class PublicClients {
        @Test
        public void returnsNoClientsForNoData() {
            PrivateSiteDataMap<LegacyClientKey> actual = PublicSiteUtil.getPublicClients(noClients, noOperators);

            PrivateSiteDataMap<LegacyClientKey> expected = new PrivateSiteDataMap<>();
            assertEquals(expected, actual);
        }

        @Test
        public void returnsAllClientsForPublicOperators() {
            ImmutableList<OperatorKey> operators = ImmutableList.of(
                    new OperatorBuilder()
                            .withSiteId(siteId1)
                            .withType(OperatorType.PUBLIC)
                            .build(),
                    new OperatorBuilder()
                            .withSiteId(siteId2)
                            .withType(OperatorType.PUBLIC)
                            .build()
            );

            LegacyClientKey client1 = new LegacyClientKey("key1", "keyHash1", "keySalt1", "", "name1", "contact1", Instant.now(), new HashSet<>(), siteId1, false, "key-id-1");
            LegacyClientKey client2 = new LegacyClientKey("key2", "keyHash2", "keySalt2", "", "name2", "contact2", Instant.now(), new HashSet<>(), siteId2, false, "key-id-2");

            ImmutableSet<LegacyClientKey> clients = ImmutableSet.of(client1, client2);

            PrivateSiteDataMap<LegacyClientKey> actual = PublicSiteUtil.getPublicClients(clients, operators);

            PrivateSiteDataMap<LegacyClientKey> expected = new PrivateSiteDataMap<>();
            expected.put(siteId1, ImmutableSet.of(client1, client2));
            expected.put(siteId2, ImmutableSet.of(client1, client2));

            assertEquals(expected, actual);
        }

        @Test
        public void ignoresDisabledClients() {
            ImmutableList<OperatorKey> operators = ImmutableList.of(
                    new OperatorBuilder()
                            .withSiteId(siteId1)
                            .withType(OperatorType.PUBLIC)
                            .build()
            );

            LegacyClientKey enabledClient = new LegacyClientKey("key1", "keyHash1", "keySalt1", "", "name1", "contact1", Instant.now(), new HashSet<>(), siteId1, false, "key-id-1");
            LegacyClientKey disabledClient = new LegacyClientKey("key2", "keyHash2", "keySalt2", "", "name2", "contact2", Instant.now(), new HashSet<>(), siteId1, true, "key-id-2");

            ImmutableSet<LegacyClientKey> clients = ImmutableSet.of(enabledClient, disabledClient);

            PrivateSiteDataMap<LegacyClientKey> actual = PublicSiteUtil.getPublicClients(clients, operators);

            PrivateSiteDataMap<LegacyClientKey> expected = new PrivateSiteDataMap<>();
            expected.put(siteId1, ImmutableSet.of(enabledClient));

            assertEquals(expected, actual);
        }
    }

    @Nested
    class PublicKeyAcls {
        @Test
        public void returnsNoAclsForNoData() {
            HashMap<Integer, Map<Integer, EncryptionKeyAcl>> actual = PublicSiteUtil.getPublicKeyAcls(noAcls, noOperators);

            HashMap<Integer, Map<Integer, EncryptionKeyAcl>> expected = new HashMap<>();
            assertEquals(expected, actual);
        }

        @Test
        public void returnsAclsForPublicOperators() {
            ImmutableList<OperatorKey> operators = ImmutableList.of(
                    new OperatorBuilder()
                            .withSiteId(siteId1)
                            .withType(OperatorType.PUBLIC)
                            .build(),
                    new OperatorBuilder()
                            .withSiteId(siteId2)
                            .withType(OperatorType.PUBLIC)
                            .build()
            );

            EncryptionKeyAcl acl1 = new EncryptionKeyAcl(true, ImmutableSet.of(siteId3));
            EncryptionKeyAcl acl2 = new EncryptionKeyAcl(false, ImmutableSet.of(siteId4));

            Map<Integer, EncryptionKeyAcl> acls = ImmutableMap.of(
                    siteId1, acl1,
                    siteId2, acl2
            );

            HashMap<Integer, Map<Integer, EncryptionKeyAcl>> actual = PublicSiteUtil.getPublicKeyAcls(acls, operators);

            HashMap<Integer, Map<Integer, EncryptionKeyAcl>> expected = new HashMap<>();
            expected.put(siteId1, ImmutableMap.of(
                    siteId1, acl1,
                    siteId2, acl2
            ));

            expected.put(siteId2, ImmutableMap.of(
                    siteId1, acl1,
                    siteId2, acl2
            ));
            assertEquals(expected, actual);
        }
    }

    @Nested
    class PublicKeysets {
        @Test
        public void testGetPublicKeysets() {
            ImmutableList<OperatorKey> operators = ImmutableList.of(
                    new OperatorBuilder()
                            .withSiteId(10)
                            .withType(OperatorType.PUBLIC)
                            .build(),
                    new OperatorBuilder()
                            .withSiteId(11)
                            .withType(OperatorType.PUBLIC)
                            .build(),
                    new OperatorBuilder()
                            .withSiteId(12)
                            .withType(OperatorType.PRIVATE)
                            .build()
            );

            Keyset keyset1 = new Keyset(1, 123, "Keyset 1", null, 999999, true, true);
            Keyset keyset2 = new Keyset(2, 124, "Keyset 2", null, 999999, true, true);
            Keyset keyset3 = new Keyset(3, 125, "Keyset 3", null, 999999, true, true);

            Map<Integer, Keyset> keysets = ImmutableMap.of(
                    123, keyset1,
                    124, keyset2,
                    125, keyset3
            );

            HashMap<Integer, Map<Integer, Keyset>> result = PublicSiteUtil.getPublicKeysets(keysets, operators);

            HashMap<Integer, Map<Integer, Keyset>> expected = new HashMap<>();
            expected.put(10, ImmutableMap.of(
                    123, keyset1,
                    124, keyset2,
                    125, keyset3
            ));
            expected.put(11, ImmutableMap.of(
                    123, keyset1,
                    124, keyset2,
                    125, keyset3
            ));

            assertEquals(expected, result);
        }
    }

    @Nested
    class PublicEncryptionKeys {
        @Test
        public void testGetPublicEncryptionKeys() {
            ImmutableList<OperatorKey> operators = ImmutableList.of(
                    new OperatorBuilder()
                            .withSiteId(10)
                            .withType(OperatorType.PUBLIC)
                            .build(),
                    new OperatorBuilder()
                            .withSiteId(11)
                            .withType(OperatorType.PUBLIC)
                            .build()
            );

            EncryptionKey key1 = new EncryptionKey(1, new byte[]{}, Instant.now(), Instant.now(), Instant.now(), 10);
            EncryptionKey key2 = new EncryptionKey(2, new byte[]{}, Instant.now(), Instant.now(), Instant.now(), 11);

            ImmutableSet<EncryptionKey> keys = ImmutableSet.of(key1, key2);

            PrivateSiteDataMap<EncryptionKey> result = PublicSiteUtil.getPublicEncryptionKeys(keys, operators);

            PrivateSiteDataMap<EncryptionKey> expected = new PrivateSiteDataMap<>();
            expected.put(10, ImmutableSet.of(key1, key2));
            expected.put(11, ImmutableSet.of(key1, key2));

            assertEquals(expected, result);
        }
    }

    @Nested
    class PublicKeysetKeys {
        @Test
        public void testGetPublicKeysetKeys() {
            ImmutableList<OperatorKey> operators = ImmutableList.of(
                    new OperatorBuilder()
                            .withSiteId(10)
                            .withType(OperatorType.PUBLIC)
                            .build(),
                    new OperatorBuilder()
                            .withSiteId(11)
                            .withType(OperatorType.PUBLIC)
                            .build()
            );

            KeysetKey key1 = new KeysetKey(1, new byte[]{}, Instant.now(), Instant.now(), Instant.now(), 10);
            KeysetKey key2 = new KeysetKey(2, new byte[]{}, Instant.now(), Instant.now(), Instant.now(), 11);

            ImmutableSet<KeysetKey> keysetKeys = ImmutableSet.of(key1, key2);

            PrivateSiteDataMap<KeysetKey> result = PublicSiteUtil.getPublicKeysetKeys(keysetKeys, operators);

            PrivateSiteDataMap<KeysetKey> expected = new PrivateSiteDataMap<>();
            expected.put(10, ImmutableSet.of(key1, key2));
            expected.put(11, ImmutableSet.of(key1, key2));

            assertEquals(expected, result);
        }
    }

    private static class OperatorBuilder {
        private final OperatorKey operator = new OperatorKey("keyHash3", "keySalt3", "name3", "contact3", "aws-nitro", 2, false, siteId1, ImmutableSet.of(), OperatorType.PRIVATE, "key-id-3");

        public OperatorBuilder withSiteId(int siteId) {
            this.operator.setSiteId(siteId);
            return this;
        }

        public OperatorBuilder withType(OperatorType operatorType) {
            this.operator.setOperatorType(operatorType);
            return this;
        }

        public OperatorKey build() {
            return operator;
        }
    }
}