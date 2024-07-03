package com.uid2.admin.util;

import com.uid2.admin.legacy.LegacyClientKey;
import com.uid2.admin.model.PrivateSiteDataMap;
import com.uid2.shared.Const;
import com.uid2.shared.auth.*;
import com.uid2.shared.model.EncryptionKey;
import com.uid2.shared.model.KeysetKey;
import com.uid2.shared.model.Site;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Given global sets of data, generate Private Site data for private operators
 * Note: only generate Private Site data for each site that has at least 1 private operator
 */
public final class PrivateSiteUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(PrivateSiteUtil.class);

    private PrivateSiteUtil() {
    }

    public static PrivateSiteDataMap<LegacyClientKey> getClientKeys(
            Collection<OperatorKey> operators,
            Collection<LegacyClientKey> clients) {
        final PrivateSiteDataMap<LegacyClientKey> result = getPrivateSites(operators);

        // For each client key that is enabled, add it to every Synced Site
        clients.forEach(c -> {
            if (!c.isDisabled()) {
                result.computeIfPresent(c.getSiteId(), (privateSiteId, privateSiteSet) -> {
                    privateSiteSet.add(c);
                    return privateSiteSet;
                });
            }
        });

        return result;
    }

    public static PrivateSiteDataMap<EncryptionKey> getEncryptionKeys(
            Collection<OperatorKey> operators,
            Collection<EncryptionKey> keys,
            Map<Integer, EncryptionKeyAcl> acls,
            Collection<LegacyClientKey> clients) {
        final PrivateSiteDataMap<EncryptionKey> result = getPrivateSites(operators);

        // If it is for a Special Site, add this key to every Private Site
        keys.stream()
                .filter(k -> isSpecialSite(k.getSiteId()))
                .forEach(k -> result.forEach((privateSiteId, privateSiteSet) -> privateSiteSet.add(k)));

        // Else, add it to corresponding Private Site
        keys.stream()
                .filter(k -> !isSpecialSite(k.getSiteId()))
                .forEach(k -> result.computeIfPresent(k.getSiteId(), (privateSiteId, privateSiteSet) -> {
                    privateSiteSet.add(k);
                    return privateSiteSet;
                }));

        // Filter OUT special keys and filter IN Reader Site keys ONLY
        final Set<Integer> readerSites = clients.stream()
                .filter(c -> c.hasRole(Role.ID_READER))
                .map(LegacyClientKey::getSiteId)
                .collect(Collectors.toSet());
        keys.stream()
                .filter(k -> !isSpecialSite(k.getSiteId()) && readerSites.contains(k.getSiteId()))
                .forEach(encryptionKey -> processAclPermissionsForEncryptionKey(encryptionKey, result, acls));

        return result;
    }

    // acls is Map<SiteId, EncryptionKeyAcl>
    public static PrivateSiteDataMap<EncryptionKeyAcl> getEncryptionKeyAcls(
            Collection<OperatorKey> operators,
            Map<Integer, EncryptionKeyAcl> acls) {
        final PrivateSiteDataMap<EncryptionKeyAcl> result = getPrivateSites(operators);

        acls.forEach((siteId, acl) -> {
            // Add it to site file for its site_id
            result.computeIfPresent(siteId, (privateSiteId, privateSiteSet) -> {
                privateSiteSet.add(acl);
                return privateSiteSet;
            });

            if (acl.getIsWhitelist()) {
                // If it's a whitelist, also write it to every site file for the whitelist
                acl.getAccessList().forEach(whiteListedSiteId ->
                        result.computeIfPresent(whiteListedSiteId, (privateSiteId, privateSiteSet) -> {
                            // Avoid adding duplicate as it could be added above already
                            if(privateSiteId.intValue() != siteId.intValue()) {
                                privateSiteSet.add(acl);
                            }
                            return privateSiteSet;
                        }));
            } else { // Blacklisted
                // If it's a blacklist, also write it to every site file except those on the blacklist
                final Set<Integer> blacklisted = acl.getAccessList();
                result.forEach((privateSiteId, privateSiteSet) -> {
                    // Avoid adding duplicate as it could be added above already
                    if (!blacklisted.contains(privateSiteId) && privateSiteId.intValue() != siteId.intValue()) {
                        privateSiteSet.add(acl);
                    }
                });
            }
        });
        return result;
    }

    //returns <SiteId, Map<SiteId, EncryptionKeyAcl>> - so for each site (id) X, returns
    // a map of <Site, EncryptionKeyAcl> that Site X needs to know about
    public static HashMap<Integer, Map<Integer, EncryptionKeyAcl>> getEncryptionKeyAclsForEachSite(
            Collection<OperatorKey> operators,
            Map<Integer, EncryptionKeyAcl> acls) {
        final HashMap<Integer, Map<Integer, EncryptionKeyAcl>> result = getPrivateSiteMap(operators);

        acls.forEach((siteId, acl) -> {
            // Add it to site file for its site_id
            result.computeIfPresent(siteId, (privateSiteId, privateSiteMap) -> {
                privateSiteMap.put(siteId, acl);
                return privateSiteMap;
            });

            if (acl.getIsWhitelist()) {
                // If it's a whitelist, also write it to every site file for the whitelist
                acl.getAccessList().forEach(whiteListedSiteId ->
                        result.computeIfPresent(whiteListedSiteId, (privateSiteId, privateSiteMap) -> {
                            // Avoid adding duplicate as it could be added above already
                            if(privateSiteId.intValue() != siteId.intValue()) {
                                privateSiteMap.put(siteId, acl);
                            }
                            return privateSiteMap;
                        }));
            } else { // Blacklisted
                // If it's a blacklist, also write it to every site file except those on the blacklist
                final Set<Integer> blacklisted = acl.getAccessList();
                result.forEach((privateSiteId, privateSiteMap) -> {
                    // Avoid adding duplicate as it could be added above already
                    if (!blacklisted.contains(privateSiteId) && privateSiteId.intValue() != siteId.intValue()) {
                        privateSiteMap.put(siteId, acl);
                    }
                });
            }
        });
        return result;
    }

    public static HashMap<Integer, Map<Integer, Keyset>> getKeysetForEachSite(Collection<OperatorKey> operators,
        Map<Integer, Keyset> keysets) {
        final HashMap<Integer, Map<Integer, Keyset>> result = getPrivateSiteMap(operators);

        keysets.forEach((keysetId, keyset) -> {
            int siteId = keyset.getSiteId();
            // Add to list for own site
            result.computeIfPresent(siteId, (privateSiteId, privateKeysetMap) -> {
                privateKeysetMap.put(keysetId, keyset);
                return privateKeysetMap;
            });

            // Add special sites to all operators
            if(isSpecialSite(siteId)) {
                result.forEach((privateSiteId, privateSiteMap) -> {
                    privateSiteMap.put(keysetId, keyset);
                });
            }
            // Add to list of all allowed sites
            Set<Integer> allowedSites = keyset.getAllowedSites();
            if(allowedSites != null)
            {
                allowedSites.forEach(allowedSiteId ->
                        result.computeIfPresent(allowedSiteId, (privateSiteId, privateSiteMap) -> {
                            if(privateSiteId.intValue() != siteId) {
                                privateSiteMap.put(keysetId, keyset);
                            }
                            return privateSiteMap;
                        }));
            }
        });

        return result;
    }

    public static PrivateSiteDataMap<Site> getSites(
            Collection<Site> sites,
            Collection<OperatorKey> operators) {
        final PrivateSiteDataMap<Site> result = getPrivateSites(operators);

        sites.forEach(s -> {
            // Special case
            if (s.getId() == Const.Data.AdvertisingTokenSiteId) {
                // If its id is 2, add it for every site
                result.forEach((privateSiteId, privateSiteData) -> privateSiteData.add(s));
            } else {
                // Add it to its own site data file only
                result.computeIfPresent(s.getId(), (privateSiteId, privateSiteSet) -> {
                    privateSiteSet.add(s);
                    return privateSiteSet;
                });
            }
        });

        return result;
    }

    private static <T> PrivateSiteDataMap<T> getPrivateSites(Collection<OperatorKey> operators) {
        PrivateSiteDataMap<T> result = new PrivateSiteDataMap<>();
        operators.forEach(o -> {
            // TODO: Should we check if site is disabled?
            if (o.getOperatorType() == OperatorType.PRIVATE
                    && o.getSiteId() != null && !result.containsKey(o.getSiteId())) {
                result.put(o.getSiteId(), new HashSet<>());
            }
        });
        return result;
    }

    private static <T> HashMap<Integer, Map<Integer, T>> getPrivateSiteMap(Collection<OperatorKey> operators) {
        HashMap<Integer, Map<Integer, T>> result = new HashMap<>();
        operators.forEach(o -> {
            // TODO: Should we check if site is disabled?
            if (o.getOperatorType() == OperatorType.PRIVATE
                    && o.getSiteId() != null && !result.containsKey(o.getSiteId())) {
                result.put(o.getSiteId(), new HashMap<>());
            }
        });
        return result;
    }

    public static PrivateSiteDataMap<Site> getPublicSites(
            Collection<Site> sites,
            Collection<OperatorKey> operators) {
        final PrivateSiteDataMap<Site> result = getPublicSitesMap(operators);

        sites.forEach(s -> {
            result.forEach((publicSiteId, publicSiteData) -> {
                publicSiteData.add(s);
            });
        });

        return result;
    }

    private static <T> PrivateSiteDataMap<T> getPublicSitesMap(Collection<OperatorKey> operators) {
        PrivateSiteDataMap<T> result = new PrivateSiteDataMap<>();
        operators.forEach(o -> {
            // TODO: Should we check if site is disabled?
            if (o.getOperatorType() == OperatorType.PUBLIC
                    && o.getSiteId() != null && !result.containsKey(o.getSiteId())) {
                result.put(o.getSiteId(), new HashSet<>());
            }
        });
        return result;
    }


    private static boolean isSpecialSite(int siteId) {
        return siteId == Const.Data.RefreshKeySiteId
                || siteId == Const.Data.MasterKeySiteId
                || siteId == Const.Data.AdvertisingTokenSiteId;
    }

    private static void processAclPermissionsForEncryptionKey(
            EncryptionKey encryptionKey,
            PrivateSiteDataMap<EncryptionKey> privateSiteEncryptionKeyMap,
            Map<Integer, EncryptionKeyAcl> acls) {
        if(acls.containsKey(encryptionKey.getSiteId())) {
            final EncryptionKeyAcl acl = acls.get(encryptionKey.getSiteId());
            if (acl.getIsWhitelist()) {
                // If it is a whitelist, write this key to every site_id on the whitelist
                // The filter below is to avoid adding duplicate as it could be added above already
                acl.getAccessList().stream()
                        .filter(whiteListedSiteId -> whiteListedSiteId != encryptionKey.getSiteId())
                        .forEach(whiteListedSiteId ->
                                privateSiteEncryptionKeyMap.computeIfPresent(whiteListedSiteId, (privateSiteId, privateSiteSet) -> {
                                    privateSiteSet.add(encryptionKey);
                                    return privateSiteSet;
                                }));
            } else { // Blacklisted
                // If it is a blacklist, write this key to every site_id that is not on the blacklist
                final Set<Integer> blacklisted = acl.getAccessList();
                privateSiteEncryptionKeyMap.forEach((privateSiteId, privateSiteSet) -> {
                    // Avoid adding duplicate as it could be added above already
                    if (!blacklisted.contains(privateSiteId) && privateSiteId != encryptionKey.getSiteId()) {
                        privateSiteSet.add(encryptionKey);
                    }
                });
            }
        } else {
            // If no keys_acl are for this site_id, add it to each site
            privateSiteEncryptionKeyMap.forEach((privateSiteId, privateSiteSet) -> {
                // Avoid adding duplicate as it could be added above already
                if (privateSiteId != encryptionKey.getSiteId()) {
                    privateSiteSet.add(encryptionKey);
                }
            });
        }
    }

    public static PrivateSiteDataMap<KeysetKey> getKeysetKeys(Collection<OperatorKey> globalOperators,
                                                              Collection<KeysetKey> globalKeysetKeys,
                                                              Map<Integer, Keyset> globalKeysets) {
        final PrivateSiteDataMap<KeysetKey> result = getPrivateSites(globalOperators);

        globalKeysetKeys.stream().forEach(keysetKey -> {
            //Add the key from keysets for its own site
            Keyset keyset = globalKeysets.get(keysetKey.getKeysetId());
            if (keyset == null) {
                LOGGER.error("Unable to find keyset with keyset id " + keysetKey.getKeysetId());
                return;
            };
            int siteId = keyset.getSiteId();
            result.computeIfPresent(siteId, (privateSiteId, privateSiteSet) -> {
                privateSiteSet.add(keysetKey);
                return privateSiteSet;
            });
            // Add special site keyset keys to all operators
            if(isSpecialSite(siteId)) {
                result.forEach((privateSiteId, privateSiteSet) -> {
                    privateSiteSet.add(keysetKey);
                });
            }
            //Add the key to all allowed sites
            Set<Integer> allowedSites = keyset.getAllowedSites();
            if(allowedSites != null)
            {
                allowedSites.forEach(allowedSiteId -> {
                    result.computeIfPresent(allowedSiteId, (privateSiteId, privateSiteSet) -> {
                        privateSiteSet.add(keysetKey);
                        return privateSiteSet;
                    });
                });
            }
        });
        return result;
    }
}
