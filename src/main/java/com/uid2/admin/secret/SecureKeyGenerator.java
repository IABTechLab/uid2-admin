package com.uid2.admin.secret;

import com.uid2.shared.Utils;

import java.security.SecureRandom;

public class SecureKeyGenerator implements IKeyGenerator {
    public SecureKeyGenerator() {}

    @Override
    public byte[] generateRandomKey(int keyLen) {
        final SecureRandom random = new SecureRandom();
        final byte[] bytes = new byte[keyLen];
        random.nextBytes(bytes);
        return bytes;
    }

    @Override
    public String generateRandomKeyString(int keyLen) {
        return Utils.toBase64String(generateRandomKey(keyLen));
    }
}
