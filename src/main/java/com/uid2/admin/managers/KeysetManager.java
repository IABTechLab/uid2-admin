package com.uid2.admin.managers;

import com.uid2.admin.secret.IKeysetKeyManager;
import com.uid2.admin.store.writer.KeysetStoreWriter;
import com.uid2.admin.util.KeysetUtil;
import com.uid2.shared.auth.Keyset;
import com.uid2.shared.store.reader.RotatingKeysetProvider;

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

    public Keyset createDefaultKeyset(Integer siteId) throws Exception{
        this.keysetProvider.loadContent();
        int newKeysetId = KeysetUtil.getMaxKeyset(this.keysetProvider.getSnapshot().getAllKeysets()) + 1;
        Keyset newKeyset = KeysetUtil.createDefaultKeyset(siteId, newKeysetId);
        addKeysetOrReplaceKeysets(newKeyset);
        return newKeyset;
    }

    public void addKeysetOrReplaceKeysets(Keyset keyset) throws Exception{
        final Map<Integer, Keyset> collection = this.keysetProvider.getSnapshot().getAllKeysets();

        collection.put(keyset.getKeysetId(), keyset);
        keysetStoreWriter.upload(collection, null);
        this.keysetKeyManager.addKeysetKey(keyset.getKeysetId());
    }
}
