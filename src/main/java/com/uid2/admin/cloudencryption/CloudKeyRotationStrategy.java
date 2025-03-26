package com.uid2.admin.cloudencryption;

import com.google.common.collect.Streams;
import com.uid2.admin.store.Clock;
import com.uid2.shared.model.CloudEncryptionKey;
import com.uid2.shared.model.Site;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CloudKeyRotationStrategy {
        private final CloudSecretGenerator secretGenerator;
        private final Clock clock;
        private final CloudKeyRetentionStrategy keyRetentionStrategy;

        public CloudKeyRotationStrategy(
                CloudSecretGenerator secretGenerator,
                Clock clock,
                CloudKeyRetentionStrategy keyRetentionStrategy
        ) {
                this.secretGenerator = secretGenerator;
                this.clock = clock;
                this.keyRetentionStrategy = keyRetentionStrategy;
        }

        public Set<CloudEncryptionKey> computeDesiredKeys(
                Collection<CloudEncryptionKey> existingKeys,
                Collection<Site> sites
        ) {
                var idGenerator = new KeyIdGenerator(existingKeys);
                Map<Integer, Set<CloudEncryptionKey>> existingKeysBySite = existingKeys
                        .stream()
                        .collect(Collectors.groupingBy(CloudEncryptionKey::getSiteId, Collectors.toSet()));

                return sites
                        .stream()
                        .map(Site::getId)
                        .distinct()
                        .flatMap(siteId -> desiredKeysForSite(siteId, idGenerator, existingKeysBySite.getOrDefault(siteId, Set.of())))
                        .collect(Collectors.toSet());
        }

        private Stream<CloudEncryptionKey> desiredKeysForSite(
                Integer siteId,
                KeyIdGenerator idGenerator,
                Set<CloudEncryptionKey> existingKeys
        ) {
                var existingKeysToRetain = keyRetentionStrategy.selectKeysToRetain(existingKeys);
                var newKey = makeNewKey(siteId, idGenerator);
                return Streams.concat(existingKeysToRetain.stream(), Stream.of(newKey));
        }

        private CloudEncryptionKey makeNewKey(Integer siteId, KeyIdGenerator idGenerator) {
                var nowSeconds = clock.getEpochSecond();
                var keyId = idGenerator.nextId();
                var secret = secretGenerator.generate();
                return new CloudEncryptionKey(keyId, siteId, nowSeconds, nowSeconds, secret);
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
