package com.uid2.admin.legacy;

import com.uid2.shared.auth.IAuthorizableProvider;

import java.util.Collection;

public interface ILegacyClientKeyProvider extends IAuthorizableProvider {
    LegacyClientKey getClientKey(String key);
    LegacyClientKey getClientKeyFromHash(String hash);
    Collection<LegacyClientKey> getAll();
    LegacyClientKey getOldestClientKey(int siteId);
}
