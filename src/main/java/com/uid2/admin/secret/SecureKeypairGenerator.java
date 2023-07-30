package com.uid2.admin.secret;

import com.uid2.shared.secure.gcpoidc.Environment;
import com.uid2.shared.secure.gcpoidc.IdentityScope;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.util.stream.Collectors;

public class SecureKeypairGenerator implements IKeypairGenerator {

    private final String publicKeyPrefix;
    private final String privateKeyPrefix;

    private static final char[] BASE58_CHARS =
            "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();

    public SecureKeypairGenerator(IdentityScope identityScope, Environment environment) {
        StringBuilder pub = new StringBuilder();
        StringBuilder priv = new StringBuilder();
        if (identityScope == IdentityScope.UID2) {
            pub.append("UID2-");
            priv.append("UID2-");
        } else {
            pub.append("EUID-");
            priv.append("EUID-");
        }
        pub.append("X-");
        priv.append("Y-");
        if(environment == Environment.Integration){
            pub.append("I-");
            priv.append("I-");
        } else if(environment == Environment.Production) {
            pub.append("P-");
            priv.append("P-");
        } else if (environment == Environment.Test) {
            pub.append("T-");
            priv.append("T-");
        } else if (environment == Environment.Local) {
            pub.append("L-");
            priv.append("L-");
        }
        this.privateKeyPrefix = priv.toString();
        this.publicKeyPrefix = pub.toString();
    }

    @Override
    public KeyPair generateRandomKeypair() throws Exception {
        final String ecdhCurvenameString = "secp256r1";
        final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC", "SunEC");
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

    @Override
    public String getPublicKeyPrefix() {
        return publicKeyPrefix;
    }

    @Override
    public String getPrivateKeyPrefix() {
        return privateKeyPrefix;
    }
}
