package com.uid2.admin.cloudencryption;

import com.uid2.shared.model.CloudEncryptionKey;

import java.util.Set;

public interface CloudKeyRetentionStrategy {
    Set<CloudEncryptionKey> selectKeysToRetain(Set<CloudEncryptionKey> keysForSite);
}
