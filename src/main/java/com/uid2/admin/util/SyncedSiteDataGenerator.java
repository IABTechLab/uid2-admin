package com.uid2.admin.util;

import com.uid2.admin.model.Site;
import com.uid2.admin.model.SyncedSiteDataMap;
import com.uid2.shared.Const;
import com.uid2.shared.auth.ClientKey;
import com.uid2.shared.auth.EncryptionKeyAcl;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.EncryptionKey;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Given global sets of data, generate Synced Sites data for private operators
 * Note: only generate Synced Site data for each site that has at least 1 private operator
 */
public final class SyncedSiteDataGenerator {

    private SyncedSiteDataGenerator() {
    }

    public static SyncedSiteDataMap<ClientKey> generateClientKeyData(
            Collection<OperatorKey> operators,
            Collection<ClientKey> clients) {
        final SyncedSiteDataMap<ClientKey> result = initialiseSyncedSiteDataSet(operators);

        // For each client key that is enabled, add it to every Synced Site
        clients.forEach(c -> {
            if (!c.isDisabled()) {
                result.computeIfPresent(c.getSiteId(), (syncedSiteId, syncedSiteSet) -> {
                    syncedSiteSet.add(c);
                    return syncedSiteSet;
                });
            }
        });

        return result;
    }

    public static SyncedSiteDataMap<EncryptionKey> generateEncryptionKeyData(
            Collection<OperatorKey> operators,
            List<EncryptionKey> keys,
            Map<Integer, EncryptionKeyAcl> acls,
            Collection<ClientKey> clients) {
        final SyncedSiteDataMap<EncryptionKey> result = initialiseSyncedSiteDataSet(operators);

        // If it is for a Special Site, add this key to every Synced Site
        keys.stream()
                .filter(k -> isSpecialSite(k.getSiteId()))
                .forEach(k -> result.forEach((syncedSiteId, syncedSiteSet) -> syncedSiteSet.add(k)));

        // Else, add it to corresponding Synced Site
        keys.stream()
                .filter(k -> !isSpecialSite(k.getSiteId()))
                .forEach(k -> result.computeIfPresent(k.getSiteId(), (syncedSiteId, syncedSiteSet) -> {
                    syncedSiteSet.add(k);
                    return syncedSiteSet;
                }));

        // Filter OUT special keys and filter IN Reader Site keys ONLY
        final Set<Integer> readerSites = clients.stream()
                .filter(c -> c.hasRole(Role.ID_READER))
                .map(ClientKey::getSiteId)
                .collect(Collectors.toSet());
        keys.stream()
                .filter(k -> !isSpecialSite(k.getSiteId()) && readerSites.contains(k.getSiteId()))
                .forEach(encryptionKey -> processAclPermissionsForEncryptionKey(encryptionKey, result, acls));

        return result;
    }

    // acls is Map<SiteId, EncryptionKeyAcl>
    public static SyncedSiteDataMap<EncryptionKeyAcl> generateEncryptionKeyAclData(
            Collection<OperatorKey> operators,
            Map<Integer, EncryptionKeyAcl> acls) {
        final SyncedSiteDataMap<EncryptionKeyAcl> result = initialiseSyncedSiteDataSet(operators);

        acls.forEach((siteId, acl) -> {
            // Add it to site file for its site_id
            result.computeIfPresent(siteId, (syncedSiteId, syncedSiteSet) -> {
                syncedSiteSet.add(acl);
                return syncedSiteSet;
            });

            if (acl.getIsWhitelist()) {
                // If it's a whitelist, also write it to every site file for the whitelist
                acl.getAccessList().forEach(whiteListedSiteId ->
                        result.computeIfPresent(whiteListedSiteId, (syncedSiteId, syncedSiteSet) -> {
                            // Avoid adding duplicate as it could be added above already
                            if(syncedSiteId != siteId) {
                                syncedSiteSet.add(acl);
                            }
                            return syncedSiteSet;
                        }));
            } else { // Blacklisted
                // If it's a blacklist, also write it to every site file except those on the blacklist
                final Set<Integer> blacklisted = acl.getAccessList();
                result.forEach((syncedSiteId, syncedSiteSet) -> {
                    // Avoid adding duplicate as it could be added above already
                    if (!blacklisted.contains(syncedSiteId) && syncedSiteId != siteId) {
                        syncedSiteSet.add(acl);
                    }
                });
            }
        });
        return result;
    }

    public static SyncedSiteDataMap<Site> generateSiteData(Collection<Site> sites, Collection<OperatorKey> operators) {
        final SyncedSiteDataMap<Site> result = initialiseSyncedSiteDataSet(operators);

        sites.forEach(s -> {
            // Special case
            if (s.getId() == Const.Data.AdvertisingTokenSiteId) {
                // If its id is 2, add it for every site
                result.forEach((syncedSiteId, syncedSiteData) -> syncedSiteData.add(s));
            } else {
                // Add it to its own site data file only
                result.computeIfPresent(s.getId(), (syncedSiteId, syncedSiteSet) -> {
                    syncedSiteSet.add(s);
                    return syncedSiteSet;
                });
            }
        });

        return result;
    }

    private static <T> SyncedSiteDataMap<T> initialiseSyncedSiteDataSet(Collection<OperatorKey> operators) {
        SyncedSiteDataMap<T> result = new SyncedSiteDataMap<>();
        operators.forEach(o -> {
            // TODO: Should we check if site is disabled?
            if (o.isPrivateOperator()
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
            SyncedSiteDataMap<EncryptionKey> syncedSiteEncryptionKeyMap,
            Map<Integer, EncryptionKeyAcl> acls) {
        if(acls.containsKey(encryptionKey.getSiteId())) {
            final EncryptionKeyAcl acl = acls.get(encryptionKey.getSiteId());
            if (acl.getIsWhitelist()) {
                // If it is a whitelist, write this key to every site_id on the whitelist
                acl.getAccessList().stream()
                        .filter(whiteListedSiteId -> whiteListedSiteId != encryptionKey.getSiteId())
                        .forEach(whiteListedSiteId ->
                                syncedSiteEncryptionKeyMap.computeIfPresent(whiteListedSiteId, (syncedSiteId, syncedSiteSet) -> {
                                    syncedSiteSet.add(encryptionKey);
                                    return syncedSiteSet;
                                }));
            } else { // Blacklisted
                // If it is a blacklist, write this key to every site_id that is not on the blacklist
                final Set<Integer> blacklisted = acl.getAccessList();
                syncedSiteEncryptionKeyMap.forEach((syncedSiteId, syncedSiteSet) -> {
                    if (!blacklisted.contains(syncedSiteId) && syncedSiteId != encryptionKey.getSiteId()) {
                        syncedSiteSet.add(encryptionKey);
                    }
                });
            }
        } else {
            // If no keys_acl are for this site_id, add it to each site
            syncedSiteEncryptionKeyMap.forEach((syncedSiteId, syncedSiteSet) -> {
                if (syncedSiteId != encryptionKey.getSiteId()) {
                    syncedSiteSet.add(encryptionKey);
                }
            });
        }
    }

}
