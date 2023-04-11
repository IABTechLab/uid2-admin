package com.uid2.admin.secret;

public interface IKeyGenerator {
    byte[] generateRandomKey(int keyLen) throws Exception;
    String generateRandomKeyString(int keyLen) throws Exception;
}
