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

public class CloudKeyStatePlanner {
        private final CloudSecretGenerator secretGenerator;
        private final Clock clock;
        private final CloudKeyRetentionStrategy keyRetentionStrategy;
        private static final int ONE_HOUR_IN_SECONDS = 3600;

        public CloudKeyStatePlanner(
                CloudSecretGenerator secretGenerator,
                Clock clock,
                CloudKeyRetentionStrategy keyRetentionStrategy
        ) {
                this.secretGenerator = secretGenerator;
                this.clock = clock;
                this.keyRetentionStrategy = keyRetentionStrategy;
        }

        public Set<CloudEncryptionKey> planRotation(
                Set<CloudEncryptionKey> existingKeys,
                Set<OperatorKey> operatorKeys
        ) {
                var keyGenerator = new CloudEncryptionKeyGenerator(clock, secretGenerator, existingKeys);
                Map<Integer, Set<CloudEncryptionKey>> existingKeysBySite = existingKeys
                        .stream()
                        .collect(Collectors.groupingBy(CloudEncryptionKey::getSiteId, Collectors.toSet()));

                return siteIdsWithOperators(operatorKeys)
                        .flatMap(siteId -> planRotationForSite(siteId, keyGenerator, existingKeysBySite.getOrDefault(siteId, Set.of())))
                        .collect(Collectors.toSet());
        }

        public Set<CloudEncryptionKey> planBackfill(
                Set<CloudEncryptionKey> existingKeys,
                Set<OperatorKey> operatorKeys
        ) {
                var keyGenerator = new CloudEncryptionKeyGenerator(clock, secretGenerator, existingKeys);
                var siteIdsWithKeys = existingKeys.stream().map(CloudEncryptionKey::getSiteId).collect(Collectors.toSet());
                var sitesWithoutKeys = siteIdsWithOperators(operatorKeys).filter(siteId -> !siteIdsWithKeys.contains(siteId));
                var newKeys = sitesWithoutKeys.map((Integer siteId) -> keyGenerator.makeNewKey(siteId, 0));
                return Streams.concat(existingKeys.stream(), newKeys).collect(Collectors.toSet());
        }

        private Stream<CloudEncryptionKey> planRotationForSite(
                Integer siteId,
                CloudEncryptionKeyGenerator keyGenerator,
                Set<CloudEncryptionKey> existingKeys
        ) {
                var existingKeysToRetain = keyRetentionStrategy.selectKeysToRetain(existingKeys);
                var newKey = keyGenerator.makeNewKey(siteId, ONE_HOUR_IN_SECONDS);
                return Streams.concat(existingKeysToRetain.stream(), Stream.of(newKey));
        }

        private static Stream<Integer> siteIdsWithOperators(Collection<OperatorKey> operatorKeys) {
                return operatorKeys
                        .stream()
                        .map(OperatorKey::getSiteId)
                        .distinct();
        }
}
