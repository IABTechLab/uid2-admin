package com.uid2.admin.secret;

import java.security.KeyPair;

public interface IKeypairGenerator {
    public KeyPair generateRandomKeypair() throws Exception;

    public String generateRandomSubscriptionId();
}
