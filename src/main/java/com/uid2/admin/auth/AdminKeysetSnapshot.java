package com.uid2.admin.auth;

import java.util.Map;

public class AdminKeysetSnapshot {
    private final Map<Integer, AdminKeyset> keysetIdToAdminKeyset;

    public AdminKeysetSnapshot(Map<Integer, AdminKeyset> keysetIdToAdminKeyset) {this.keysetIdToAdminKeyset = keysetIdToAdminKeyset;}

    public Map<Integer, AdminKeyset> getAllKeysets() { return keysetIdToAdminKeyset; }
}
