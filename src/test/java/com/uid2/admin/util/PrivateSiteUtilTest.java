package com.uid2.admin.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.uid2.admin.model.PrivateSiteDataMap;
import com.uid2.shared.Const;
import com.uid2.shared.auth.*;
import com.uid2.shared.model.ClientSideKeypair;
import com.uid2.shared.model.EncryptionKey;
import com.uid2.shared.model.KeysetKey;
import com.uid2.shared.model.Site;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import java.time.Instant;
import java.util.*;

import static com.uid2.admin.util.PrivateSiteUtil.*;
import static org.junit.jupiter.api.Assertions.*;

public class PrivateSiteUtilTest {
    private final ClientKey site1ClientWithReaderRole = new ClientBuilder()
            .withSiteId(siteId1)
            .thatIsEnabled()
            .withReaderRole()
            .build();
    private final ClientKey site2ClientWithReaderRole = new ClientBuilder()
            .withSiteId(siteId2)
            .thatIsEnabled()
            .withReaderRole()
            .build();
    private final OperatorKey site1PrivateOperator = new OperatorBuilder()
            .withSiteId(siteId1)
            .withType(OperatorType.PRIVATE)
            .thatIsEnabled()
            .build();
    private final OperatorKey site2PrivateOperator = new OperatorBuilder()
            .withSiteId(siteId2)
            .withType(OperatorType.PRIVATE)
            .thatIsEnabled()
            .build();

    public PrivateSiteUtilTest() {
    }

    @Nested
    class Client {
        @Test
        public void returnsNoSitesForNoSitesOrOperators() {
            PrivateSiteDataMap<ClientKey> expected = new PrivateSiteDataMap<>();

            PrivateSiteDataMap<ClientKey> actual = getClientKeys(noOperators, noClients);

            assertEquals(expected, actual);
        }

        @Test
        public void returnsSiteWithNoClientsForNoSitesAndOperatorForASite() {
            PrivateSiteDataMap<ClientKey> expected = new PrivateSiteDataMap<ClientKey>()
                    .with(siteId1, noClients);

            PrivateSiteDataMap<ClientKey> actual = getClientKeys(ImmutableList.of(site1PrivateOperator), noClients);

            assertEquals(expected, actual);
        }

        @Test
        public void returnsSiteWithClientForOperatorAndClientForASite() {
            PrivateSiteDataMap<ClientKey> result = getClientKeys(ImmutableList.of(
                    site1PrivateOperator
            ), ImmutableList.of(site1ClientWithReaderRole));

            PrivateSiteDataMap<ClientKey> expected = new PrivateSiteDataMap<ClientKey>()
                    .with(siteId1, ImmutableSet.of(site1ClientWithReaderRole));
            assertEquals(expected, result);
        }

        @Test
        public void ignoresDisabledClients() {
            ImmutableList<OperatorKey> operators = ImmutableList.of(site1PrivateOperator);
            ImmutableList<ClientKey> clients = ImmutableList.of(
                    new ClientBuilder()
                            .withSiteId(siteId1)
                            .thatIsDisabled()
                            .build()
            );
            PrivateSiteDataMap<ClientKey> result = getClientKeys(operators, clients);

            PrivateSiteDataMap<ClientKey> expected = new PrivateSiteDataMap<ClientKey>().with(siteId1, noClients);
            assertEquals(expected, result);
        }

        @Test
        public void ignoresSitesWithOnlyPublicOperators() {
            List<OperatorKey> operators = ImmutableList.of(
                    new OperatorBuilder()
                            .withSiteId(siteId1)
                            .withType(OperatorType.PUBLIC)
                            .build()
            );

            ImmutableList<ClientKey> clients = ImmutableList.of(site1ClientWithReaderRole);
            PrivateSiteDataMap<ClientKey> result = getClientKeys(operators, clients);

            PrivateSiteDataMap<ClientKey> expected = new PrivateSiteDataMap<>();
            assertEquals(expected, result);
        }
    }

    @Nested
    class EncryptionKeys {
        EncryptionKey site1Key = new EncryptionKey(5, new byte[]{}, Instant.EPOCH, Instant.now(), Instant.MAX, siteId1);

        @Test
        public void returnsNoKeysForNoData() {
            PrivateSiteDataMap<EncryptionKey> expected = new PrivateSiteDataMap<>();

            PrivateSiteDataMap<EncryptionKey> actual = getEncryptionKeys(noOperators, noKeys, noAcls, noClients);

            assertEquals(expected, actual);
        }

        @Test
        public void ignoresSitesWithOnlyPublicOperators() {
            PrivateSiteDataMap<EncryptionKey> expected = new PrivateSiteDataMap<>();

            ImmutableList<OperatorKey> operators = ImmutableList.of(
                    new OperatorBuilder()
                            .withSiteId(siteId1)
                            .withType(OperatorType.PUBLIC)
                            .build()
            );

            PrivateSiteDataMap<EncryptionKey> actual = getEncryptionKeys(
                    operators,
                    ImmutableSet.of(site1Key),
                    noAcls,
                    ImmutableSet.of(site1ClientWithReaderRole)
            );

            assertEquals(expected, actual);
        }

        @Test
        public void sharesSpecialSitesWithAll() {
            Set<EncryptionKey> specialSiteKeys = ImmutableSet.of(
                    new EncryptionKey(5, new byte[]{}, Instant.EPOCH, Instant.now(), Instant.MAX, -2),
                    new EncryptionKey(5, new byte[]{}, Instant.EPOCH, Instant.now(), Instant.MAX, -1),
                    new EncryptionKey(5, new byte[]{}, Instant.EPOCH, Instant.now(), Instant.MAX, 2)
            );

            PrivateSiteDataMap<EncryptionKey> expected = new PrivateSiteDataMap<EncryptionKey>()
                    .with(siteId1, specialSiteKeys)
                    .with(siteId2, specialSiteKeys);

            ImmutableList<OperatorKey> operators = ImmutableList.of(
                    site1PrivateOperator,
                    site2PrivateOperator);

            ImmutableSet<ClientKey> clients = ImmutableSet.of(
                    site1ClientWithReaderRole,
                    site2ClientWithReaderRole);

            PrivateSiteDataMap<EncryptionKey> actual = getEncryptionKeys(
                    operators,
                    specialSiteKeys,
                    noAcls,
                    clients
            );

            assertEquals(expected, actual);
        }

        @Nested
        class AclRules {
            @Test
            public void sharesKeysWithSitesOnWhitelist() {
                PrivateSiteDataMap<EncryptionKey> expected = new PrivateSiteDataMap<EncryptionKey>()
                        .with(siteId1, ImmutableSet.of(site1Key))
                        .with(siteId2, ImmutableSet.of(site1Key));

                final Map<Integer, EncryptionKeyAcl> whitelist = ImmutableMap.of(
                        siteId1, new EncryptionKeyAcl(true, ImmutableSet.of(siteId2))
                );

                ImmutableList<OperatorKey> operators = ImmutableList.of(
                        site1PrivateOperator,
                        site2PrivateOperator);

                ImmutableSet<ClientKey> clients = ImmutableSet.of(
                        site1ClientWithReaderRole,
                        site2ClientWithReaderRole);
                PrivateSiteDataMap<EncryptionKey> actual = getEncryptionKeys(
                        operators,
                        ImmutableSet.of(site1Key),
                        whitelist,
                        clients
                );

                assertEquals(expected, actual);
            }


            @Test
            public void doesNotShareKeysWithSitesOutsideOfWhitelist() {
                PrivateSiteDataMap<EncryptionKey> expected = new PrivateSiteDataMap<EncryptionKey>()
                        .with(siteId1, ImmutableSet.of(site1Key))
                        .with(siteId2, ImmutableSet.of());

                final Map<Integer, EncryptionKeyAcl> whitelist = ImmutableMap.of(
                        siteId1, new EncryptionKeyAcl(true, ImmutableSet.of(siteId3))
                );

                ImmutableList<OperatorKey> operators = ImmutableList.of(
                        site1PrivateOperator,
                        site2PrivateOperator);

                ImmutableSet<ClientKey> clients = ImmutableSet.of(
                        site1ClientWithReaderRole,
                        site2ClientWithReaderRole);
                PrivateSiteDataMap<EncryptionKey> actual = getEncryptionKeys(
                        operators,
                        ImmutableSet.of(site1Key),
                        whitelist,
                        clients
                );

                assertEquals(expected, actual);
            }

            @Test
            public void doesNotShareKeysWithSitesOnTheBlacklist() {
                PrivateSiteDataMap<EncryptionKey> expected = new PrivateSiteDataMap<EncryptionKey>()
                        .with(siteId1, ImmutableSet.of(site1Key))
                        .with(siteId2, ImmutableSet.of());

                final Map<Integer, EncryptionKeyAcl> blacklist = ImmutableMap.of(
                        siteId1, new EncryptionKeyAcl(false, ImmutableSet.of(siteId2))
                );

                ImmutableList<OperatorKey> operators = ImmutableList.of(
                        site1PrivateOperator,
                        site2PrivateOperator);

                ImmutableSet<ClientKey> clients = ImmutableSet.of(
                        site1ClientWithReaderRole,
                        site2ClientWithReaderRole);

                PrivateSiteDataMap<EncryptionKey> actual = getEncryptionKeys(
                        operators,
                        ImmutableSet.of(site1Key),
                        blacklist,
                        clients
                );

                assertEquals(expected, actual);
            }

            @Test
            public void shareKeysWithSitesNotOnTheBlacklist() {
                PrivateSiteDataMap<EncryptionKey> expected = new PrivateSiteDataMap<EncryptionKey>()
                        .with(siteId1, ImmutableSet.of(site1Key))
                        .with(siteId2, ImmutableSet.of(site1Key));

                final Map<Integer, EncryptionKeyAcl> blacklist = ImmutableMap.of(
                        siteId1, new EncryptionKeyAcl(false, ImmutableSet.of(siteId3))
                );

                ImmutableList<OperatorKey> operators = ImmutableList.of(
                        site1PrivateOperator,
                        site2PrivateOperator);

                ImmutableSet<ClientKey> clients = ImmutableSet.of(
                        site1ClientWithReaderRole,
                        site2ClientWithReaderRole);

                PrivateSiteDataMap<EncryptionKey> actual = getEncryptionKeys(
                        operators,
                        ImmutableSet.of(site1Key),
                        blacklist,
                        clients
                );

                assertEquals(expected, actual);
            }

            @Test
            public void sharesAllKeysWhenNoAcl() {
                PrivateSiteDataMap<EncryptionKey> expected = new PrivateSiteDataMap<EncryptionKey>()
                        .with(siteId1, ImmutableSet.of(site1Key))
                        .with(siteId2, ImmutableSet.of(site1Key));

                ImmutableList<OperatorKey> operators = ImmutableList.of(
                        site1PrivateOperator,
                        site2PrivateOperator);

                ImmutableSet<ClientKey> clients = ImmutableSet.of(
                        site1ClientWithReaderRole,
                        site2ClientWithReaderRole);

                PrivateSiteDataMap<EncryptionKey> actual = getEncryptionKeys(
                        operators,
                        ImmutableSet.of(site1Key),
                        noAcls,
                        clients
                );

                assertEquals(expected, actual);
            }
        }


        @Test
        public void testGenerateEncryptionKeyData() {
            final Set<Role> readerRole = new HashSet<>();
            readerRole.add(Role.ID_READER);

            final OperatorKey[] operatorKeys = {
                    new OperatorKey("key3", "name3", "contact3", "aws-nitro", 2, false, 3, new HashSet<>(), OperatorType.PRIVATE),
                    new OperatorKey("key4", "name4", "contact4", "aws-nitro", 2, false, 4, new HashSet<>(), OperatorType.PRIVATE),
                    new OperatorKey("key5", "name5", "contact5", "aws-nitro", 2, false, 5, new HashSet<>(), OperatorType.PUBLIC),
                    new OperatorKey("key6", "name6", "contact6", "aws-nitro", 2, false, 6, new HashSet<>(), OperatorType.PRIVATE),
                    new OperatorKey("key7", "name6", "contact6", "aws-nitro", 2, false, 7, new HashSet<>(), OperatorType.PUBLIC)
            };
            final EncryptionKey[] encryptionKeys = {
                    new EncryptionKey(1, new byte[]{}, Instant.now(), Instant.now(), Instant.now(), Const.Data.RefreshKeySiteId),
                    new EncryptionKey(2, new byte[]{}, Instant.now(), Instant.now(), Instant.now(), 3),
                    new EncryptionKey(3, new byte[]{}, Instant.now(), Instant.now(), Instant.now(), 4),
                    new EncryptionKey(4, new byte[]{}, Instant.now(), Instant.now(), Instant.now(), 5),
                    new EncryptionKey(5, new byte[]{}, Instant.now(), Instant.now(), Instant.now(), 6),
                    //testing for Reader site role that doesn't have any ACL white/blacklist defined
                    new EncryptionKey(6, new byte[]{}, Instant.now(), Instant.now(), Instant.now(), 7)
            };
            final ClientKey[] clientKeys = {
                    new ClientKey("key3", "", "name3", "contact3", Instant.now(), readerRole, 3, false),
                    new ClientKey("key4", "", "name4", "contact4", Instant.now(), readerRole, 4, false),
                    new ClientKey("key7", "", "name7", "contact7", Instant.now(), readerRole, 7, false)
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
            acls.put(6, new EncryptionKeyAcl(false, site6Blacklist));

            final PrivateSiteDataMap<EncryptionKey> result = getEncryptionKeys(
                    Arrays.asList(operatorKeys), Arrays.asList(encryptionKeys), acls, Arrays.asList(clientKeys));

            final Set<EncryptionKey> expectedSite3EncryptionKeys = new HashSet<>();
            expectedSite3EncryptionKeys.add(encryptionKeys[0]);
            expectedSite3EncryptionKeys.add(encryptionKeys[1]);
            expectedSite3EncryptionKeys.add(encryptionKeys[5]);
            final Set<EncryptionKey> expectedSite4EncryptionKeys = new HashSet<>();
            expectedSite4EncryptionKeys.add(encryptionKeys[0]);
            expectedSite4EncryptionKeys.add(encryptionKeys[1]);
            expectedSite4EncryptionKeys.add(encryptionKeys[2]);
            expectedSite4EncryptionKeys.add(encryptionKeys[5]);
            final Set<EncryptionKey> expectedSite6EncryptionKeys = new HashSet<>();
            expectedSite6EncryptionKeys.add(encryptionKeys[0]);
            expectedSite6EncryptionKeys.add(encryptionKeys[2]);
            expectedSite6EncryptionKeys.add(encryptionKeys[4]);
            expectedSite6EncryptionKeys.add(encryptionKeys[5]);
            final PrivateSiteDataMap<EncryptionKey> expected = new PrivateSiteDataMap<>();
            expected.put(3, expectedSite3EncryptionKeys);
            expected.put(4, expectedSite4EncryptionKeys);
            expected.put(6, expectedSite6EncryptionKeys);

            assertEquals(expected, result);
        }
    }

    @Nested
    class Acls {
        private final EncryptionKeyAcl site1Acl = new EncryptionKeyAcl(true, ImmutableSet.of(5, 6, 7));

        @Test
        public void returnsNoAclsForNoData() {
            Map<Integer, Map<Integer, EncryptionKeyAcl>> actual = getEncryptionKeyAclsForEachSite(noOperators, noAcls);

            Map<Integer, Map<Integer, EncryptionKeyAcl>> expected = ImmutableMap.of();
            assertEquals(expected, actual);
        }

        @Test
        public void ignoresSitesWithOnlyPublicOperators() {
            ImmutableList<OperatorKey> operators = ImmutableList.of(
                    new OperatorBuilder()
                            .withSiteId(siteId1)
                            .withType(OperatorType.PUBLIC)
                            .build()
            );

            Map<Integer, EncryptionKeyAcl> acls = ImmutableMap.of(
                    siteId1, site1Acl
            );

            Map<Integer, Map<Integer, EncryptionKeyAcl>> actual = getEncryptionKeyAclsForEachSite(operators, acls);

            Map<Integer, Map<Integer, EncryptionKeyAcl>> expected = ImmutableMap.of();
            assertEquals(expected, actual);
        }

        @Test
        public void recordsAclUnderItsOwnSiteId() {

            ImmutableList<OperatorKey> operators = ImmutableList.of(site1PrivateOperator);
            Map<Integer, EncryptionKeyAcl> acls = ImmutableMap.of(
                    siteId1, site1Acl
            );

            Map<Integer, Map<Integer, EncryptionKeyAcl>> actual = getEncryptionKeyAclsForEachSite(operators, acls);

            Map<Integer, Map<Integer, EncryptionKeyAcl>> expected = ImmutableMap.of(
                    siteId1, ImmutableMap.of(
                            siteId1, site1Acl
                    )
            );
            assertEquals(expected, actual);
        }

        @Test
        public void sharesAclWithSitesOnWhitelist() {
            EncryptionKeyAcl acl = new EncryptionKeyAcl(true, ImmutableSet.of(siteId2));

            ImmutableList<OperatorKey> operators = ImmutableList.of(site1PrivateOperator, site2PrivateOperator);
            Map<Integer, EncryptionKeyAcl> acls = ImmutableMap.of(
                    siteId1, acl
            );

            Map<Integer, Map<Integer, EncryptionKeyAcl>> actual = getEncryptionKeyAclsForEachSite(operators, acls);

            Map<Integer, Map<Integer, EncryptionKeyAcl>> expected = ImmutableMap.of(
                    siteId1, ImmutableMap.of(
                            siteId1, acl
                    ),
                    siteId2, ImmutableMap.of(
                            siteId1, acl
                    )
            );

            assertEquals(expected, actual);
        }

        @Test
        public void doesNotsharesAclWithSitesNotOnWhitelist() {
            EncryptionKeyAcl acl = new EncryptionKeyAcl(true, ImmutableSet.of(siteId3));

            ImmutableList<OperatorKey> operators = ImmutableList.of(site1PrivateOperator);
            Map<Integer, EncryptionKeyAcl> acls = ImmutableMap.of(
                    siteId1, acl
            );

            Map<Integer, Map<Integer, EncryptionKeyAcl>> actual = getEncryptionKeyAclsForEachSite(operators, acls);

            Map<Integer, Map<Integer, EncryptionKeyAcl>> expected = ImmutableMap.of(
                    siteId1, ImmutableMap.of(
                            siteId1, acl
                    )
            );

            assertEquals(expected, actual);
        }

        @Test
        public void sharesAclWithSitesOutsideOfBlacklist() {
            EncryptionKeyAcl acl = new EncryptionKeyAcl(false, ImmutableSet.of(siteId3));


            ImmutableList<OperatorKey> operators = ImmutableList.of(site1PrivateOperator, site2PrivateOperator);
            Map<Integer, EncryptionKeyAcl> acls = ImmutableMap.of(
                    siteId1, acl
            );

            Map<Integer, Map<Integer, EncryptionKeyAcl>> actual = getEncryptionKeyAclsForEachSite(operators, acls);

            Map<Integer, Map<Integer, EncryptionKeyAcl>> expected = ImmutableMap.of(
                    siteId1, ImmutableMap.of(
                            siteId1, acl
                    ),
                    siteId2, ImmutableMap.of(
                            siteId1, acl
                    )
            );

            assertEquals(expected, actual);
        }

        @Test
        public void doesNotSharesAclWithSitesOnTheBlacklist() {
            EncryptionKeyAcl acl = new EncryptionKeyAcl(false, ImmutableSet.of(siteId2));


            ImmutableList<OperatorKey> operators = ImmutableList.of(site1PrivateOperator);
            Map<Integer, EncryptionKeyAcl> acls = ImmutableMap.of(
                    siteId1, acl
            );

            Map<Integer, Map<Integer, EncryptionKeyAcl>> actual = getEncryptionKeyAclsForEachSite(operators, acls);

            Map<Integer, Map<Integer, EncryptionKeyAcl>> expected = ImmutableMap.of(
                    siteId1, ImmutableMap.of(
                            siteId1, acl
                    )
            );

            assertEquals(expected, actual);
        }

        @Test
        public void testGenerateEncryptionKeyAclData() {
            final OperatorKey[] operatorKeys = {
                    new OperatorKey("key3", "name3", "contact3", "aws-nitro", 2, false, 3, new HashSet<>(), OperatorType.PRIVATE),
                    new OperatorKey("key4", "name4", "contact4", "aws-nitro", 2, false, 4, new HashSet<>(), OperatorType.PRIVATE),
                    new OperatorKey("key5", "name5", "contact5", "aws-nitro", 2, false, 5, new HashSet<>(), OperatorType.PUBLIC),
                    new OperatorKey("key6", "name6", "contact6", "aws-nitro", 2, false, 6, new HashSet<>(), OperatorType.PRIVATE)
            };

            final Set<Integer> site3Whitelist = new HashSet<>();
            site3Whitelist.add(4);
            final Set<Integer> site4Blacklist = new HashSet<>();
            site4Blacklist.add(3);
            final Map<Integer, EncryptionKeyAcl> acls = new HashMap<>();
            acls.put(3, new EncryptionKeyAcl(true, site3Whitelist));
            acls.put(4, new EncryptionKeyAcl(false, site4Blacklist));

            PrivateSiteDataMap<EncryptionKeyAcl> result = getEncryptionKeyAcls(
                    Arrays.asList(operatorKeys), acls);

            final Set<EncryptionKeyAcl> site3EncryptionKeyAcls = new HashSet<>();
            site3EncryptionKeyAcls.add(acls.get(3));
            final Set<EncryptionKeyAcl> site4EncryptionKeyAcls = new HashSet<>();
            site4EncryptionKeyAcls.add(acls.get(3));
            site4EncryptionKeyAcls.add(acls.get(4));
            final Set<EncryptionKeyAcl> site6EncryptionKeyAcls = new HashSet<>();
            site6EncryptionKeyAcls.add(acls.get(4));
            final PrivateSiteDataMap<EncryptionKeyAcl> expected = new PrivateSiteDataMap<>();
            expected.put(3, site3EncryptionKeyAcls);
            expected.put(4, site4EncryptionKeyAcls);
            expected.put(6, site6EncryptionKeyAcls);

            assertEquals(expected, result);
        }

        @Test
        public void testGenerateEncryptionKeyAclDataForEachSite() {
            final OperatorKey[] operatorKeys = {
                    new OperatorKey("key3", "name3", "contact3", "aws-nitro", 2, false, 3, new HashSet<>(), OperatorType.PRIVATE),
                    new OperatorKey("key4", "name4", "contact4", "aws-nitro", 2, false, 4, new HashSet<>(), OperatorType.PRIVATE),
                    new OperatorKey("key5", "name5", "contact5", "aws-nitro", 2, false, 5, new HashSet<>(), OperatorType.PUBLIC),
                    new OperatorKey("key6", "name6", "contact6", "aws-nitro", 2, false, 6, new HashSet<>(), OperatorType.PRIVATE)
            };

            final Set<Integer> site3Whitelist = new HashSet<>();
            site3Whitelist.add(4);
            final Set<Integer> site4Blacklist = new HashSet<>();
            site4Blacklist.add(3);
            final Map<Integer, EncryptionKeyAcl> acls = new HashMap<>();
            acls.put(3, new EncryptionKeyAcl(true, site3Whitelist));
            acls.put(4, new EncryptionKeyAcl(false, site4Blacklist));

            HashMap<Integer, Map<Integer, EncryptionKeyAcl>> result = getEncryptionKeyAclsForEachSite(
                    Arrays.asList(operatorKeys), acls);

            final Map<Integer, EncryptionKeyAcl> site3EncryptionKeyAcls = new HashMap<>();
            site3EncryptionKeyAcls.put(3, acls.get(3));

            final Map<Integer, EncryptionKeyAcl> site4EncryptionKeyAcls = new HashMap<>();
            site4EncryptionKeyAcls.put(3, acls.get(3));
            site4EncryptionKeyAcls.put(4, acls.get(4));

            final Map<Integer, EncryptionKeyAcl> site6EncryptionKeyAcls = new HashMap<>();
            site6EncryptionKeyAcls.put(4, acls.get(4));


            final HashMap<Integer, Map<Integer, EncryptionKeyAcl>> expected = new HashMap<>();
            expected.put(3, site3EncryptionKeyAcls);
            expected.put(4, site4EncryptionKeyAcls);
            expected.put(6, site6EncryptionKeyAcls);

            assertEquals(expected, result);
        }

    }

    @Nested
    class KeysetKeys {

        KeysetKey keysetKey1 = new KeysetKey(1000, new byte[]{}, Instant.now(), Instant.now(), Instant.now(), 1);
        KeysetKey keysetKey2 = new KeysetKey(2000, new byte[]{}, Instant.now(), Instant.now(), Instant.now(), 2);
        KeysetKey keysetKey3 = new KeysetKey(3000, new byte[]{}, Instant.now(), Instant.now(), Instant.now(), 3);

        Keyset keyset1 = new Keyset(1, 123, "test-name-1", ImmutableSet.of(), 999999, true, true);
        Keyset keyset2 = new Keyset(2, 124, "test-name-2", ImmutableSet.of(), 999999, true, true);
        Keyset keyset3 = new Keyset(3, 125, "test-name-3", ImmutableSet.of(), 999999, true, true);

        Map<Integer, Keyset> keysets = new HashMap<Integer, Keyset>();

        @Test
        public void returnsNoKeysetKeysForNoKeysetKeysOrOperators() {
            PrivateSiteDataMap<KeysetKey> expected = new PrivateSiteDataMap<>();

            PrivateSiteDataMap<KeysetKey> actual = getKeysetKeys(noOperators, noKeysetKeys, keysets);

            assertEquals(expected, actual);
        }

        @Test
        public void ignoresSitesWithOnlyPublicOperators() {
            keysets.put(1, keyset1);

            PrivateSiteDataMap<KeysetKey> expected = new PrivateSiteDataMap<>();

            ImmutableList<OperatorKey> operators = ImmutableList.of(
                    new OperatorBuilder()
                            .withSiteId(siteId1)
                            .withType(OperatorType.PUBLIC)
                            .build()
            );

            PrivateSiteDataMap<KeysetKey> actual = getKeysetKeys(
                    operators,
                    ImmutableSet.of(),
                    keysets
            );

            assertEquals(expected, actual);
        }

        @Test
        public void siteHasOwnKeysetKeys() {

            keysets.put(1, keyset1);
            keysets.put(2, keyset2);
            keysets.put(3, keyset3);

            PrivateSiteDataMap<KeysetKey> expected = new PrivateSiteDataMap<>();
            expected.put(123, ImmutableSet.of(keysetKey1));
            expected.put(124, ImmutableSet.of(keysetKey2));
            expected.put(125, ImmutableSet.of(keysetKey3));

            ImmutableList<OperatorKey> operators = ImmutableList.of(
                    new OperatorBuilder()
                            .withSiteId(123)
                            .withType(OperatorType.PRIVATE)
                            .build(),
                    new OperatorBuilder()
                            .withSiteId(124)
                            .withType(OperatorType.PRIVATE)
                            .build(),
                    new OperatorBuilder()
                            .withSiteId(125)
                            .withType(OperatorType.PRIVATE)
                            .build()
            );

            PrivateSiteDataMap<KeysetKey> actual = getKeysetKeys(
                    operators,
                    ImmutableSet.of(keysetKey1, keysetKey2, keysetKey3),
                    keysets
            );

            assertEquals(expected, actual);
        }

        @Test
        public void siteHasAllowedKeysetKeys() {

            keysets.put(1, new Keyset(1, 123, "test-name-1", ImmutableSet.of(124, 125), 999999, true, true));
            keysets.put(2, keyset2);
            keysets.put(3, keyset3);

            PrivateSiteDataMap<KeysetKey> expected = new PrivateSiteDataMap<>();
            expected.put(123, ImmutableSet.of(keysetKey1));
            expected.put(124, ImmutableSet.of(keysetKey1, keysetKey2));
            expected.put(125, ImmutableSet.of(keysetKey1, keysetKey3));

            ImmutableList<OperatorKey> operators = ImmutableList.of(
                    new OperatorBuilder()
                            .withSiteId(123)
                            .withType(OperatorType.PRIVATE)
                            .build(),
                    new OperatorBuilder()
                            .withSiteId(124)
                            .withType(OperatorType.PRIVATE)
                            .build(),
                    new OperatorBuilder()
                            .withSiteId(125)
                            .withType(OperatorType.PRIVATE)
                            .build()
            );

            PrivateSiteDataMap<KeysetKey> actual = getKeysetKeys(
                    operators,
                    ImmutableSet.of(keysetKey1, keysetKey2, keysetKey3),
                    keysets
            );

            assertEquals(expected, actual);
        }

        @Test
        public void skipKeyWithKeysetIdNotFoundAndLogError() {
            // setup test log output
            Logger privateSiteUtilLogger = (Logger) LoggerFactory.getLogger(PrivateSiteUtil.class);
            ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
            listAppender.start();
            privateSiteUtilLogger.addAppender(listAppender);

            keysets.put(1, new Keyset(1, 123, "test-name-1", ImmutableSet.of(124, 125), 999999, true, true));
            keysets.put(2, keyset2);

            PrivateSiteDataMap<KeysetKey> expected = new PrivateSiteDataMap<>();
            expected.put(123, ImmutableSet.of(keysetKey1));
            expected.put(124, ImmutableSet.of(keysetKey1, keysetKey2));
            expected.put(125, ImmutableSet.of(keysetKey1));

            ImmutableList<OperatorKey> operators = ImmutableList.of(
                    new OperatorBuilder()
                            .withSiteId(123)
                            .withType(OperatorType.PRIVATE)
                            .build(),
                    new OperatorBuilder()
                            .withSiteId(124)
                            .withType(OperatorType.PRIVATE)
                            .build(),
                    new OperatorBuilder()
                            .withSiteId(125)
                            .withType(OperatorType.PRIVATE)
                            .build()
            );

            PrivateSiteDataMap<KeysetKey> actual = getKeysetKeys(
                    operators,
                    ImmutableSet.of(keysetKey1, keysetKey2, keysetKey3),
                    keysets
            );

            List<ILoggingEvent> logsList = listAppender.list;
            assertEquals("Unable to find keyset with keyset id 3", logsList.get(0)
                    .getMessage());
            assertEquals(Level.ERROR, logsList.get(0)
                    .getLevel());

            assertEquals(expected, actual);
        }
    }

    @Nested
    class Sites {
        Site site1 = new Site(siteId1, "site1", true);

        @Test
        public void returnsNoSitesForNoData() {
            PrivateSiteDataMap<Site> actual = getSites(ImmutableSet.of(), noOperators);

            PrivateSiteDataMap<Site> expected = new PrivateSiteDataMap<>();
            assertEquals(expected, actual);
        }

        @Test
        public void ignoresSitesWithOnlyPublicOperators() {
            ImmutableList<OperatorKey> operators = ImmutableList.of(
                    new OperatorBuilder()
                            .withSiteId(siteId1)
                            .withType(OperatorType.PUBLIC)
                            .build()
            );

            Set<Site> sites = ImmutableSet.of(site1);

            PrivateSiteDataMap<Site> actual = getSites(sites, operators);

            PrivateSiteDataMap<Site> expected = new PrivateSiteDataMap<>();
            assertEquals(expected, actual);
        }

        @Test
        public void providesSitesTheirOwnData() {
            ImmutableList<OperatorKey> operators = ImmutableList.of(site1PrivateOperator);

            Set<Site> sites = ImmutableSet.of(site1);

            PrivateSiteDataMap<Site> actual = getSites(sites, operators);

            PrivateSiteDataMap<Site> expected = new PrivateSiteDataMap<Site>().with(siteId1, ImmutableSet.of(site1));
            assertEquals(expected, actual);
        }

        @Test
        public void doesNotShareSitesDataWithEachOther() {
            ImmutableList<OperatorKey> operators = ImmutableList.of(site1PrivateOperator, site2PrivateOperator);

            Site site2 = new Site(siteId2, "Site 2", true);
            Set<Site> sites = ImmutableSet.of(site1, site2);

            PrivateSiteDataMap<Site> actual = getSites(sites, operators);

            PrivateSiteDataMap<Site> expected = new PrivateSiteDataMap<Site>()
                    .with(siteId1, ImmutableSet.of(site1))
                    .with(siteId2, ImmutableSet.of(site2));
            assertEquals(expected, actual);
        }

        @Test
        public void siteWithId2IsSharedWithEveryone() {
            ImmutableList<OperatorKey> operators = ImmutableList.of(site1PrivateOperator);

            Site siteWithId2 = new Site(2, "Site with ID 2", true);
            Set<Site> sites = ImmutableSet.of(site1, siteWithId2);

            PrivateSiteDataMap<Site> actual = getSites(sites, operators);

            PrivateSiteDataMap<Site> expected = new PrivateSiteDataMap<Site>()
                    .with(siteId1, ImmutableSet.of(site1, siteWithId2));
            assertEquals(expected, actual);
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
                    new OperatorKey("key2", "name2", "contact2", "aws-nitro", 2, false, Const.Data.AdvertisingTokenSiteId, new HashSet<>(), OperatorType.PUBLIC),
                    new OperatorKey("key5", "name5", "contact5", "aws-nitro", 5, false, 5, new HashSet<>(), OperatorType.PUBLIC),
            };
            final OperatorKey[] privateOperatorKeys = {
                    new OperatorKey("key3", "name3", "contact3", "aws-nitro", 3, false, 3, new HashSet<>(), OperatorType.PRIVATE),
                    new OperatorKey("key4", "name4", "contact4", "aws-nitro", 4, false, 4, new HashSet<>(), OperatorType.PRIVATE),
            };
            final List<OperatorKey> allOperatorKeys = new ArrayList<>(Arrays.asList(publicOperatorKeys));
            allOperatorKeys.addAll(Arrays.asList(privateOperatorKeys));

            final PrivateSiteDataMap<Site> result = getSites(
                    Arrays.asList(sites), allOperatorKeys);

            final Set<Site> expectedSite3Sites = new HashSet<>();
            expectedSite3Sites.add(sites[0]);
            expectedSite3Sites.add(sites[1]);
            final Set<Site> expectedSite4Sites = new HashSet<>();
            expectedSite4Sites.add(sites[0]);
            expectedSite4Sites.add(sites[2]);
            final PrivateSiteDataMap<Site> expected = new PrivateSiteDataMap<>();
            expected.put(3, expectedSite3Sites);
            expected.put(4, expectedSite4Sites);

            assertEquals(expected, result);
        }
    }

    static int siteId1 = 10;
    static int siteId2 = 11;
    static int siteId3 = 12;
    final Set<OperatorKey> noOperators = ImmutableSet.of();
    final Set<ClientKey> noClients = ImmutableSet.of();
    final Set<EncryptionKey> noKeys = ImmutableSet.of();
    final Set<ClientSideKeypair> noKeypairs = ImmutableSet.of();
    final Set<KeysetKey> noKeysetKeys = ImmutableSet.of();
    final Map<Integer, EncryptionKeyAcl> noAcls = ImmutableMap.of();

    static class OperatorBuilder {
        private final OperatorKey operator = new OperatorKey("key3", "name3", "contact3", "aws-nitro", 2, false, siteId1, ImmutableSet.of(), OperatorType.PRIVATE);

        OperatorBuilder() {
        }

        public OperatorBuilder withSiteId(int siteId) {
            this.operator.setSiteId(siteId);
            return this;
        }

        public OperatorBuilder withType(OperatorType operatorType) {
            this.operator.setOperatorType(operatorType);
            return this;
        }

        public OperatorBuilder thatIsEnabled() {
            this.operator.setDisabled(false);
            return this;
        }

        public OperatorBuilder thatIsDisabled() {
            this.operator.setDisabled(true);
            return this;
        }

        public OperatorKey build() {
            return operator;
        }

    }

    static class ClientBuilder {
        private int siteId = siteId1;
        private boolean isDisabled = false;
        private final HashSet<Role> roles = new HashSet<>();

        public ClientBuilder withSiteId(int siteId) {
            this.siteId = siteId;
            return this;
        }

        public ClientBuilder thatIsEnabled() {
            this.isDisabled = false;
            return this;
        }

        public ClientBuilder thatIsDisabled() {
            this.isDisabled = true;
            return this;
        }

        public ClientBuilder withReaderRole() {
            roles.add(Role.ID_READER);
            return this;
        }

        public ClientKey build() {
            return new ClientKey("key3_1", "", "name3_1", "contact3_1", Instant.now(), roles, siteId, isDisabled);
        }
    }
}
