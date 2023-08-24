package com.uid2.admin.managers;

import com.uid2.admin.model.ClientType;
import com.uid2.admin.secret.IKeysetKeyManager;
import com.uid2.admin.store.reader.RotatingAdminKeysetStore;
import com.uid2.admin.store.writer.AdminKeysetWriter;
import com.uid2.shared.Const;
import com.uid2.shared.auth.ClientKey;
import com.uid2.shared.auth.Keyset;
import com.uid2.shared.auth.Role;
import com.uid2.admin.auth.AdminKeyset;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;

import static java.lang.Math.max;

public class KeysetManager {

    private final RotatingAdminKeysetStore keysetProvider;
    private final AdminKeysetWriter keysetStoreWriter;
    private final IKeysetKeyManager keysetKeyManager;

    private final boolean enableKeysets;

    public KeysetManager(RotatingAdminKeysetStore keysetProvider, AdminKeysetWriter keysetStoreWriter,
                         IKeysetKeyManager keysetKeyManager, boolean enableKeysets) {
        this.keysetProvider = keysetProvider;
        this.keysetStoreWriter = keysetStoreWriter;
        this.keysetKeyManager  = keysetKeyManager;
        this.enableKeysets = enableKeysets;
    }


    public static final String MasterKeysetName = "Master";
    public static final String RefreshKeysetName = "Refresh";
    public static final String FallbackPublisherKeysetName = "Fallback Publisher";


    public static AdminKeyset lookUpKeyset(int siteId, Map<Integer, AdminKeyset> keysets) {
        for (AdminKeyset keyset: keysets.values()) {
            if(keyset.getSiteId() == siteId && keyset.isDefault()) {
                return keyset;
            }
        }
        return null;
    }

    public static Integer getMaxKeyset(Map<Integer, AdminKeyset> keysets) {
        // keyset id 1/2/3 are assigned for master/refresh/default publisher encryption key ids,
        // so we always reserve these 3 keyset ids for them
        if(keysets.isEmpty()) return 3;
        return max(Collections.max(keysets.keySet()), 3);
    }

    public static AdminKeyset createDefaultKeyset(int siteId, int keysetId) {
        String name = "";

        //only set if both siteId and keysetId match our expectation according to the requirements
        //or otherwise we know there's a bug in other codes
        if(siteId == Const.Data.MasterKeySiteId && keysetId == Const.Data.MasterKeysetId) {
            name = MasterKeysetName;
        }
        else if(siteId == Const.Data.RefreshKeySiteId && keysetId == Const.Data.RefreshKeysetId) {
            name = RefreshKeysetName;
        }
        else if(siteId == Const.Data.AdvertisingTokenSiteId && keysetId == Const.Data.FallbackPublisherKeysetId) {
            name = FallbackPublisherKeysetName;
        }
        return new AdminKeyset(keysetId, siteId, name, null, Instant.now().getEpochSecond(),
                true, true, Set.of());
    }

    public static Keyset adminKeysetToKeyset(AdminKeyset adminKeyset, Map<ClientType, Set<Integer>> siteIdsByType) {
        Set<Integer> allowedList = new HashSet<>();
        if(adminKeyset.getAllowedSites() == null) {
            return adminKeyset;
        }

        allowedList.addAll(adminKeyset.getAllowedSites());

        for (ClientType type : adminKeyset.getAllowedTypes()) {
            allowedList.addAll(siteIdsByType.get(type));
        }
        return new Keyset(adminKeyset.getKeysetId(), adminKeyset.getSiteId(), adminKeyset.getName(), allowedList,
                adminKeyset.getCreated(), adminKeyset.isEnabled(), adminKeyset.isDefault());
    }

    public AdminKeyset createKeysetForClient(ClientKey client) throws Exception{
        if(!enableKeysets) return null;
        final Map<Integer, AdminKeyset> collection = this.keysetProvider.getSnapshot().getAllKeysets();
        for(AdminKeyset keyset : collection.values()) {
            if(keyset.getSiteId() == client.getSiteId()) {
                // A keyset already exists for the site ID
                return keyset;
            }
        }

        if(client.hasRole(Role.GENERATOR)) {
            return createAndAddDefaultKeyset(client.getSiteId());
        }
        if(client.hasRole(Role.SHARER)) {
            return createAndAddKeyset(client.getSiteId(), new HashSet<>(), new HashSet<>());
        }
        return null;
    }

    public AdminKeyset createAndAddKeyset(Integer siteId, Set<Integer> allowedSites, Set<ClientType> allowedTypes) throws Exception{
        if(!enableKeysets) return null;
        int newKeysetId = getNextKeysetId();
        AdminKeyset keyset = new AdminKeyset(newKeysetId, siteId, "", allowedSites,
                Instant.now().getEpochSecond(), true, true, allowedTypes);
        addOrReplaceKeyset(keyset);
        return keyset;
    }

    public int getNextKeysetId() {
        return KeysetManager.getMaxKeyset(this.keysetProvider.getSnapshot().getAllKeysets()) + 1;
    }

    public AdminKeyset createAndAddDefaultKeyset(Integer siteId) throws Exception{
        if(!enableKeysets) return null;

        this.keysetProvider.loadContent();
        int newKeysetId = getNextKeysetId();
        AdminKeyset newKeyset = KeysetManager.createDefaultKeyset(siteId, newKeysetId);
        addOrReplaceKeyset(newKeyset);
        return newKeyset;
    }

    public void addOrReplaceKeyset(AdminKeyset keyset) throws Exception{
        if(!enableKeysets) return;

        Map<Integer, AdminKeyset> collection = this.keysetProvider.getSnapshot().getAllKeysets();

        collection.put(keyset.getKeysetId(), keyset);
        keysetStoreWriter.upload(collection, null);
        this.keysetKeyManager.addKeysetKey(keyset.getKeysetId());
    }
}
