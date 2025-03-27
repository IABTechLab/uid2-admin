package com.uid2.admin.cloudencryption;

import com.google.common.collect.Streams;
import com.uid2.admin.store.Clock;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.model.CloudEncryptionKey;

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
                Collection<CloudEncryptionKey> existingCloudKeys,
                Collection<OperatorKey> operatorKeys
        ) {
                var keyGenerator = new CloudEncryptionKeyGenerator(clock, secretGenerator, existingCloudKeys);
                Map<Integer, Set<CloudEncryptionKey>> existingKeysBySite = existingCloudKeys
                        .stream()
                        .collect(Collectors.groupingBy(CloudEncryptionKey::getSiteId, Collectors.toSet()));

                return operatorKeys
                        .stream()
                        .map(OperatorKey::getSiteId)
                        .distinct()
                        .flatMap(siteId -> desiredKeysForSite(siteId, keyGenerator, existingKeysBySite.getOrDefault(siteId, Set.of())))
                        .collect(Collectors.toSet());
        }

        private Stream<CloudEncryptionKey> desiredKeysForSite(
                Integer siteId,
                CloudEncryptionKeyGenerator keyGenerator,
                Set<CloudEncryptionKey> existingKeys
        ) {
                var existingKeysToRetain = keyRetentionStrategy.selectKeysToRetain(existingKeys);
                var newKey = keyGenerator.makeNewKey(siteId);
                return Streams.concat(existingKeysToRetain.stream(), Stream.of(newKey));
        }
}
