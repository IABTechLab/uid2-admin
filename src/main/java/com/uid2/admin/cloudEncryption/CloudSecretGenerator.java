package com.uid2.admin.cloudEncryption;

import com.uid2.shared.secret.SecureKeyGenerator;

public class CloudSecretGenerator {
    private final SecureKeyGenerator keyGenerator;

    // The SecureKeyGenerator is preferable to the IKeyGenerator interface as it doesn't throw Exception
    public CloudSecretGenerator(SecureKeyGenerator keyGenerator) {
        this.keyGenerator = keyGenerator;
    }

    public String generate() {
        //Generate a 32-byte key for AesGcm
        return keyGenerator.generateRandomKeyString(32);
    }
}
