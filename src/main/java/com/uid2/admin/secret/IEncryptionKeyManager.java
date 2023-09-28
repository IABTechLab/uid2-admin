package com.uid2.admin.secret;

import com.uid2.shared.model.EncryptionKey;

public interface IEncryptionKeyManager {
    EncryptionKey addSiteKey(int siteId) throws Exception;
}
