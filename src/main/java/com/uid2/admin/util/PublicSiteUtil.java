package com.uid2.admin.util;

import com.uid2.admin.legacy.LegacyClientKey;
import com.uid2.admin.model.PrivateSiteDataMap;
import com.uid2.shared.auth.EncryptionKeyAcl;
import com.uid2.shared.auth.Keyset;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.auth.OperatorType;
import com.uid2.shared.model.EncryptionKey;
import com.uid2.shared.model.KeysetKey;
import com.uid2.shared.model.SaltEntry;
import com.uid2.shared.model.Site;
import com.uid2.shared.store.RotatingSaltProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class PublicSiteUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(PrivateSiteUtil.class);


    static <T> PrivateSiteDataMap<T> getPublicSitesMap(Collection<OperatorKey> operators) {
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

    public static PrivateSiteDataMap<Site> getPublicSites(
            Collection<Site> sites,
            Collection<OperatorKey> operators) {
        final PrivateSiteDataMap<Site> result = getPublicSitesMap(operators);

        result.forEach((publicSiteId, publicSiteData) -> {
            publicSiteData.addAll(sites);
        });

        return result;
    }


    public static PrivateSiteDataMap<LegacyClientKey> getPublicClients(
            Collection<LegacyClientKey> clients,
            Collection<OperatorKey> operators) {
        final PrivateSiteDataMap<LegacyClientKey> result = getPublicSitesMap(operators);

        result.forEach((publicSiteId, publicSiteData) -> {
            publicSiteData.addAll(clients);
        });

        return result;
    }

    public static HashMap<Integer, Map<Integer, EncryptionKeyAcl>> getPublicKeyAcls(
            Map<Integer, EncryptionKeyAcl> acls,
            Collection<OperatorKey> operators) {
        final HashMap<Integer, Map<Integer, EncryptionKeyAcl>> result = new HashMap<>();

        operators.forEach(o -> {
            if (o.getOperatorType() == OperatorType.PUBLIC
                    && o.getSiteId() != null && !result.containsKey(o.getSiteId())) {
                result.put(o.getSiteId(), new HashMap<>());
            }
        });

        acls.forEach((aclSiteId, acl) -> {
            result.forEach((publicSiteId, publicSiteData) -> {
                publicSiteData.put(aclSiteId, acl);
            });
        });

        return result;
    }

    public static HashMap<Integer, Map<Integer, Keyset>> getPublicKeysets(
            Map<Integer, Keyset> keysets,
            Collection<OperatorKey> operators) {
        final HashMap<Integer, Map<Integer, Keyset>> result = new HashMap<>();

        operators.forEach(o -> {
            if (o.getOperatorType() == OperatorType.PUBLIC
                    && o.getSiteId() != null && !result.containsKey(o.getSiteId())) {
                result.put(o.getSiteId(), new HashMap<>());
            }
        });

        keysets.forEach((keysetId, keyset) -> {
            result.forEach((publicSiteId, publicSiteData) -> {
                publicSiteData.put(keysetId, keyset);
            });
        });

        return result;
    }


    public static PrivateSiteDataMap<EncryptionKey> getPublicEncryptionKeys(
            Collection<EncryptionKey> keys,
            Collection<OperatorKey> operators) {
        final PrivateSiteDataMap<EncryptionKey> result = getPublicSitesMap(operators);

        keys.forEach(k -> {
            result.forEach((publicSiteId, publicSiteData) -> {
                publicSiteData.add(k);
            });
        });

        return result;
    }

    public static PrivateSiteDataMap<KeysetKey> getPublicKeysetKeys(
            Collection<KeysetKey> keysetKeys,
            Collection<OperatorKey> operators) {
        final PrivateSiteDataMap<KeysetKey> result = getPublicSitesMap(operators);

        keysetKeys.forEach(keysetKey -> {
            result.forEach((publicSiteId, publicSiteData) -> {
                publicSiteData.add(keysetKey);
            });
        });

        return result;
    }

    public static PrivateSiteDataMap<RotatingSaltProvider.SaltSnapshot> getPublicSaltEntries(
            Collection<RotatingSaltProvider.SaltSnapshot> globalSaltEntries,
            Collection<OperatorKey> operators) {
        final PrivateSiteDataMap<RotatingSaltProvider.SaltSnapshot> result = getPublicSitesMap(operators);

        globalSaltEntries.forEach(saltEntry -> {
            result.forEach((publicSiteId, publicSiteData) -> {
                publicSiteData.add(saltEntry);
            });
        });

        return result;
    }
}
