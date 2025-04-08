package com.uid2.admin.cloudencryption;

import com.uid2.admin.store.Clock;
import com.uid2.shared.model.CloudEncryptionKey;

import java.util.Collection;

public class CloudEncryptionKeyGenerator {
    private final Clock clock;
    private final CloudSecretGenerator secretGenerator;
    private final KeyIdGenerator idGenerator;

    public CloudEncryptionKeyGenerator(
            Clock clock,
            CloudSecretGenerator secretGenerator,
            Collection<CloudEncryptionKey> existingKeys) {
        this.clock = clock;
        this.secretGenerator = secretGenerator;
        this.idGenerator = new KeyIdGenerator(existingKeys);
    }

    public CloudEncryptionKey makeNewKey(Integer siteId, Integer activatesInSeconds) {
        var created = clock.getEpochSecond();
        var activates = created + activatesInSeconds;
        var keyId = idGenerator.nextId();
        var secret = secretGenerator.generate();
        return new CloudEncryptionKey(keyId, siteId, activates, created, secret);
    }

    private static class KeyIdGenerator {
        private int lastId;

        public KeyIdGenerator(Collection<CloudEncryptionKey> existingKeys) {
            this.lastId = existingKeys.stream().map(CloudEncryptionKey::getId).max(Integer::compareTo).orElse(0);
        }

        public int nextId() {
            return ++lastId;
        }
    }
}
