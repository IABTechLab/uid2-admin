package com.uid2.admin.secret;

import com.uid2.shared.model.KeysetKey;

public interface IKeysetKeyManager {

    public KeysetKey addKeysetKey(int keysetId) throws Exception;
}
