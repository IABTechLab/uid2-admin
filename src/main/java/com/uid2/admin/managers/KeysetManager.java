package com.uid2.admin.managers;

import com.uid2.admin.secret.IKeysetKeyManager;
import com.uid2.admin.store.writer.KeysetStoreWriter;
import com.uid2.admin.util.KeysetUtil;
import com.uid2.shared.auth.ClientKey;
import com.uid2.shared.auth.Keyset;
import com.uid2.shared.auth.Role;
import com.uid2.shared.store.reader.RotatingKeysetProvider;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;

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

    public void createKeysetForClient(ClientKey client) throws Exception{
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

    public Keyset createAndAddKeyset(Integer siteId, Set<Integer> allowedSites) throws Exception{
        if(!enableKeysets) return null;
        int newKeysetId = KeysetUtil.getMaxKeyset(this.keysetProvider.getSnapshot().getAllKeysets()) + 1;
        Keyset keyset = new Keyset(newKeysetId, siteId, "", allowedSites, Instant.now().getEpochSecond(), true, true);
        addOrReplaceKeyset(keyset);
        return keyset;
    }

    public Keyset createAndAddDefaultKeyset(Integer siteId) throws Exception{
        if(!enableKeysets) return null;

        this.keysetProvider.loadContent();
        int newKeysetId = KeysetUtil.getMaxKeyset(this.keysetProvider.getSnapshot().getAllKeysets()) + 1;
        Keyset newKeyset = KeysetUtil.createDefaultKeyset(siteId, newKeysetId);
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
