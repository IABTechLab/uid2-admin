package com.uid2.admin.secret;

import com.uid2.shared.Utils;

public interface IKeyGenerator {
    byte[] generateRandomKey(int keyLen) throws Exception;

    String generateRandomKeyString(int keyLen) throws Exception;
}
