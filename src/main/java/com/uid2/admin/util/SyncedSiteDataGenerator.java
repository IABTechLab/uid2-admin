package com.uid2.admin.util;

import com.uid2.admin.model.Site;
import com.uid2.shared.Const;
import com.uid2.shared.auth.ClientKey;
import com.uid2.shared.auth.EncryptionKeyAcl;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.model.EncryptionKey;

import java.util.*;

/**
 * Given global sets of data, generate Synced Sites data for private operators
 * Note: only generate Synced Site data for each site that has at least 1 private operator
 */
public class SyncedSiteDataGenerator {

    public Map<Integer, Collection<ClientKey>> generateClientKeyData(Collection<Site> sites, Collection<OperatorKey> operators, Collection<ClientKey> keys) {
        Map<Integer, Collection<ClientKey>> result = new HashMap<>();
        initialiseSyncedSiteSites(operators, result);

        keys.forEach(k -> {
            if (!k.isDisabled()) {
                // For each client key that is enabled
                // Add it to /clients/site/{site_id}/clients.json
                result.computeIfPresent(k.getSiteId(), (syncedSiteId, syncedSiteSet) -> {
                    syncedSiteSet.add(k);
                    return syncedSiteSet;
                });
            }
        });
        return result;
    }

    public Map<Integer, Collection<EncryptionKey>> generateEncryptionKeyData(Collection<Site> sites, Collection<OperatorKey> operators, List<EncryptionKey> keys, Map<Integer, EncryptionKeyAcl> acls) {
        Map<Integer, Collection<EncryptionKey>> result = new HashMap<>();
        initialiseSyncedSiteSites(operators, result);

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

        //TODO deal with reader sites



        return result;
    }

    //acls is Map<SiteId, EncryptionKeyAcl>
    public Map<Integer, Collection<EncryptionKeyAcl>> generateEncryptionKeyAclData(Collection<Site> sites, Collection<OperatorKey> operators, Map<Integer, EncryptionKeyAcl> acls) {
        Map<Integer, Collection<EncryptionKeyAcl>> result = new HashMap<>();
        initialiseSyncedSiteSites(operators, result);

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
                        syncedSiteSet.add(acl);
                        return syncedSiteSet;
                    });
                });
            } else //blacklisted
            {
                // If it's a blacklist also write it to every site file except those on the blacklist
                final Set<Integer> blacklisted = acl.getAccessList();
                result.forEach((syncedSiteId, syncedSiteSet) -> {
                    if (!blacklisted.contains(syncedSiteId)) {
                        syncedSiteSet.add(acl);
                    }
                });
            }
        });
        return result;
    }

    public Map<Integer, Collection<Site>> generateSiteData
            (Collection<Site> sites, Collection<OperatorKey> operators) {
        Map<Integer, Collection<Site>> result = new HashMap<>();

        initialiseSyncedSiteSites(operators, result);

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
    private static <T> void initialiseSyncedSiteSites
    (Collection<OperatorKey> operators, Map<Integer, Collection<T>> result) {
        operators.forEach(o ->
        {
            // TODO should we check if site is disabled?
            if (o.isPrivateOperator() && o.getSiteId() != null && result.get(o.getSiteId()) == null) {
                result.put(o.getSiteId(), new HashSet());
            }
        });
    }

    private static boolean isSpecialSite(int siteId) {
        return siteId == Const.Data.RefreshKeySiteId || siteId == Const.Data.MasterKeySiteId || siteId == Const.Data.AdvertisingTokenSiteId;
    }


}
