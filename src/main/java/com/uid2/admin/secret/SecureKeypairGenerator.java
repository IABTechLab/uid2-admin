package com.uid2.admin.secret;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;

public class SecureKeypairGenerator implements IKeypairGenerator {

    private static final char[] BASE58_CHARS =
            "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();

    public SecureKeypairGenerator() { }

    @Override
    public KeyPair generateKeypair() throws Exception {
        final String ecdhCurvenameString = "secp256r1";
        final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        final ECGenParameterSpec ecParameterSpec = new ECGenParameterSpec(ecdhCurvenameString);
        keyPairGenerator.initialize(ecParameterSpec);
        final KeyPair ecdhKeyPair = keyPairGenerator.genKeyPair();
        return ecdhKeyPair;
    }

    @Override
    public String generateRandomSubscriptionId() {
        final SecureRandom random = new SecureRandom();
        return random
                .ints(10, 0, BASE58_CHARS.length)
                .mapToObj(i -> BASE58_CHARS[i])
                .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
                .toString();
    }
}
