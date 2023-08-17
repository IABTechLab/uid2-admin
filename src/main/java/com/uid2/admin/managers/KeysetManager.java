package com.uid2.admin.managers;

import com.uid2.admin.secret.IKeysetKeyManager;
import com.uid2.admin.store.writer.KeysetStoreWriter;
import com.uid2.shared.Const;
import com.uid2.shared.auth.ClientKey;
import com.uid2.shared.auth.Keyset;
import com.uid2.shared.auth.Role;
import com.uid2.shared.store.reader.RotatingKeysetProvider;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;

import static java.lang.Math.max;

public class KeysetManager {

    private final RotatingKeysetProvider keysetProvider;
    private final KeysetStoreWriter keysetStoreWriter;
    private final IKeysetKeyManager keysetKeyManager;

    private final boolean enableKeysets;

    public KeysetManager(RotatingKeysetProvider keysetProvider, KeysetStoreWriter keysetStoreWriter,
                         IKeysetKeyManager keysetKeyManager, boolean enableKeysets) {
        this.keysetProvider = keysetProvider;
        this.keysetStoreWriter = keysetStoreWriter;
        this.keysetKeyManager  = keysetKeyManager;
        this.enableKeysets = enableKeysets;
    }


    public static final String MasterKeysetName = "Master";
    public static final String RefreshKeysetName = "Refresh";
    public static final String FallbackPublisherKeysetName = "Fallback Publisher";


    public static Keyset lookUpKeyset(int siteId, Map<Integer, Keyset> keysets) {
        for (Keyset keyset: keysets.values()) {
            if(keyset.getSiteId() == siteId && keyset.isDefault()) {
                return keyset;
            }
        }
        return null;
    }

    public static Integer getMaxKeyset(Map<Integer, Keyset> keysets) {
        // keyset id 1/2/3 are assigned for master/refresh/default publisher encryption key ids,
        // so we always reserve these 3 keyset ids for them
        if(keysets.isEmpty()) return 3;
        return max(Collections.max(keysets.keySet()), 3);
    }

    public static Keyset createDefaultKeyset(int siteId, int keysetId) {
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
        return new Keyset(keysetId, siteId, name, null, Instant.now().getEpochSecond(), true, true);
    }

    public void createKeysetForClient(ClientKey client) throws Exception{
        if(!enableKeysets) return;
        final Map<Integer, Keyset> collection = this.keysetProvider.getSnapshot().getAllKeysets();
        for(Keyset keyset : collection.values()) {
            if(keyset.getSiteId() == client.getSiteId()) {
                // A keyset already exists for the site ID
                return;
            }
        }

        if(client.hasRole(Role.GENERATOR)) {
            createAndAddDefaultKeyset(client.getSiteId());
        } else if(client.hasRole(Role.SHARER)) {
            createAndAddKeyset(client.getSiteId(), new HashSet<>());
        }
    }

    private Keyset createAndAddKeyset(Integer siteId, Set<Integer> allowedSites) throws Exception{
        if(!enableKeysets) return null;
        int newKeysetId = getNextKeysetId();
        Keyset keyset = new Keyset(newKeysetId, siteId, "", allowedSites, Instant.now().getEpochSecond(), true, true);
        addOrReplaceKeyset(keyset);
        return keyset;
    }

    public int getNextKeysetId() {
        return KeysetManager.getMaxKeyset(this.keysetProvider.getSnapshot().getAllKeysets()) + 1;
    }

    private Keyset createAndAddDefaultKeyset(Integer siteId) throws Exception{
        if(!enableKeysets) return null;

        this.keysetProvider.loadContent();
        int newKeysetId = getNextKeysetId();
        Keyset newKeyset = KeysetManager.createDefaultKeyset(siteId, newKeysetId);
        addOrReplaceKeyset(newKeyset);
        return newKeyset;
    }

    public void addOrReplaceKeyset(Keyset keyset) throws Exception{
        if(!enableKeysets) return;

        Map<Integer, Keyset> collection = this.keysetProvider.getSnapshot().getAllKeysets();

        collection.put(keyset.getKeysetId(), keyset);
        keysetStoreWriter.upload(collection, null);
        this.keysetKeyManager.addKeysetKey(keyset.getKeysetId());
    }
}
