package com.uid2.admin.util;

import com.uid2.admin.model.Site;
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
public class SyncedSiteDataGenerator {

    public static Map<Integer, Collection<ClientKey>> generateClientKeyData(Collection<Site> sites, Collection<OperatorKey> operators, Collection<ClientKey> clients) {
        Map<Integer, Collection<ClientKey>> result = initialiseSyncedSiteDataSet(operators);

        clients.forEach(c -> {
            if (!c.isDisabled()) {
                // For each client key that is enabled
                // Add it to every Synced Site
                result.computeIfPresent(c.getSiteId(), (syncedSiteId, syncedSiteSet) -> {
                    syncedSiteSet.add(c);
                    return syncedSiteSet;
                });
            }
        });
        return result;
    }

    public static Map<Integer, Collection<EncryptionKey>> generateEncryptionKeyData(Collection<Site> sites, Collection<OperatorKey> operators, List<EncryptionKey> keys, Map<Integer, EncryptionKeyAcl> acls, Collection<ClientKey> clients) {
        Map<Integer, Collection<EncryptionKey>> result = initialiseSyncedSiteDataSet(operators);

        //If it is for a Special Site
        //Add this key to every Synced Site
        keys.stream().filter(k -> isSpecialSite(k.getSiteId())).forEach(k ->
        {
            result.forEach((syncedSiteId, syncedSiteSet) -> {
                syncedSiteSet.add(k);
            });
        });

        // Else add it to corresponding Synced Site
        keys.stream().filter(k -> !isSpecialSite(k.getSiteId())).forEach(k ->
        {
            result.computeIfPresent(k.getSiteId(), (syncedSiteId, syncedSiteSet) -> {
                syncedSiteSet.add(k);
                return syncedSiteSet;
            });
        });

        Set<Integer> readerSites = clients.stream().filter(c -> c.hasRole(Role.ID_READER)).map(ck -> ck.getSiteId()).collect(Collectors.toSet());
        //filter OUT special keys and filter IN Reader Site keys ONLY
        keys.stream().filter(k -> !isSpecialSite(k.getSiteId()) && readerSites.contains(k.getSiteId())).forEach(encryptionKey ->
        {
            if(acls.containsKey(encryptionKey.getSiteId())) {
                EncryptionKeyAcl acl = acls.get(encryptionKey.getSiteId());
                if (acl.getIsWhitelist()) {
                    //If it is a whitelist write this key to every site_id on the whitelist
                    //the filter below is to stop adding duplicate which could have been inserted for its corresponding synced site
                    acl.getAccessList().stream().filter(whiteListedSiteId -> whiteListedSiteId != encryptionKey.getSiteId()).forEach(whiteListedSiteId ->
                    {
                        result.computeIfPresent(whiteListedSiteId, (syncedSiteId, syncedSiteSet) -> {
                            syncedSiteSet.add(encryptionKey);
                            return syncedSiteSet;
                        });
                    });
                } else //blacklisted
                {
                    //If it is a blacklist write this key to every site_id that is not on the blacklist
                    final Set<Integer> blacklisted = acl.getAccessList();
                    result.forEach((syncedSiteId, syncedSiteSet) -> {
                        //stop adding duplicate which could have been inserted for its corresponding synced site earlier
                        if (!blacklisted.contains(syncedSiteId) && syncedSiteId != encryptionKey.getSiteId()) {
                            syncedSiteSet.add(encryptionKey);
                        }
                    });
                }
            }
            else {
                //If no keys_acl are for this site_id
                //Add it to each site
                result.forEach((syncedSiteId, syncedSiteSet) -> {
                    //stop adding duplicate which could have been inserted for its corresponding synced site earlier
                    if (syncedSiteId != encryptionKey.getSiteId()) {
                        syncedSiteSet.add(encryptionKey);
                    }
                });
            }
        });
        return result;
    }

    //acls is Map<SiteId, EncryptionKeyAcl>
    public static Map<Integer, Collection<EncryptionKeyAcl>> generateEncryptionKeyAclData(Collection<Site> sites, Collection<OperatorKey> operators, Map<Integer, EncryptionKeyAcl> acls) {
        Map<Integer, Collection<EncryptionKeyAcl>> result = initialiseSyncedSiteDataSet(operators);

        acls.forEach((siteId, acl) -> {
            //Add it to site file for its site_id
            result.computeIfPresent(siteId, (syncedSiteId, syncedSiteSet) -> {
                syncedSiteSet.add(acl);
                return syncedSiteSet;
            });
            if (acl.getIsWhitelist()) {
                // If it's a whitelist also write it to every site file for the whitelist
                acl.getAccessList().forEach(whiteListedSiteId ->
                {
                    result.computeIfPresent(whiteListedSiteId, (syncedSiteId, syncedSiteSet) -> {
                        // avoid adding duplicate as it could be added above already
                        if(syncedSiteId != siteId)
                        {
                            syncedSiteSet.add(acl);
                        }
                        return syncedSiteSet;
                    });
                });
            } else //blacklisted
            {
                // If it's a blacklist also write it to every site file except those on the blacklist
                final Set<Integer> blacklisted = acl.getAccessList();
                result.forEach((syncedSiteId, syncedSiteSet) -> {
                    // avoid adding duplicate as it could be added above already
                    if (!blacklisted.contains(syncedSiteId) && syncedSiteId != siteId ) {
                        syncedSiteSet.add(acl);
                    }
                });
            }
        });
        return result;
    }

    public static Map<Integer, Collection<Site>> generateSiteData(Collection<Site> sites, Collection<OperatorKey> operators) {
        Map<Integer, Collection<Site>> result = initialiseSyncedSiteDataSet(operators);

        sites.forEach(s ->
        {
            //special case
            if (s.getId() == Const.Data.AdvertisingTokenSiteId) {
                // If its id is 2
                // Add it for every site
                result.forEach((syncedSiteId, syncedSiteData) -> syncedSiteData.add(s));
            } else {
                //Add it to its own site data file only
                result.computeIfPresent(s.getId(), (syncedSiteId, syncedSiteSet) ->
                {
                    syncedSiteSet.add(s);
                    return syncedSiteSet;
                });
            }
        });
        return result;
    }

    /**
     * Initialise a Map<SiteId, Collection<T>> object which has an entry for each site that has at least 1 private operator
     */
    private static <T> Map<Integer, Collection<T>> initialiseSyncedSiteDataSet(Collection<OperatorKey> operators) {
        Map<Integer, Collection<T>> result = new HashMap<>();
        operators.forEach(o ->
        {
            // TODO should we check if site is disabled?
            if (o.isPrivateOperator() && o.getSiteId() != null && result.get(o.getSiteId()) == null) {
                result.put(o.getSiteId(), new HashSet());
            }
        });
        return result;
    }

    private static boolean isSpecialSite(int siteId) {
        return siteId == Const.Data.RefreshKeySiteId || siteId == Const.Data.MasterKeySiteId || siteId == Const.Data.AdvertisingTokenSiteId;
    }


}
