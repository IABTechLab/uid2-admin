package com.uid2.admin.secret;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;

public class SecureKeypairGenerator implements IKeypairGenerator {
    public SecureKeypairGenerator() {}

    @Override
    public KeyPair generateRandomKeypair() throws Exception {
        final String ecdhCurvenameString = "secp256r1";
        // standard curvennames
        // secp256r1 [NIST P-256, X9.62 prime256v1]
        // secp384r1 [NIST P-384]
        // secp521r1 [NIST P-521]
        final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC", "SunEC");
        final ECGenParameterSpec ecParameterSpec = new ECGenParameterSpec(ecdhCurvenameString);
        keyPairGenerator.initialize(ecParameterSpec);
        final KeyPair ecdhKeyPair = keyPairGenerator.genKeyPair();
        return ecdhKeyPair;
    }
}
