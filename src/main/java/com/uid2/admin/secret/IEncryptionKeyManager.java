package com.uid2.admin.secret;

import com.uid2.shared.model.EncryptionKey;

public interface IEncryptionKeyManager {
    EncryptionKey addSiteKey(int siteId) throws Exception;

    /**
     * Creates a site key, if none exists. If created, the key is active immediately.
     */
    EncryptionKey createSiteKeyIfNoneExists(int siteId) throws Exception;
}
