package com.uid2.admin.auth;

import java.util.Map;

public class AdminKeysetSnapshot {
    private final Map<Integer, AdminKeyset> keysets;

    public AdminKeysetSnapshot(Map<Integer, AdminKeyset> keysets) {this.keysets =keysets;}

    public Map<Integer, AdminKeyset> getAllKeysets() { return keysets; }
}
