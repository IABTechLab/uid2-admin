package com.uid2.admin.cloudEncryption;

import com.uid2.admin.store.writer.CloudEncryptionKeyStoreWriter;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.model.CloudEncryptionKey;
import com.uid2.shared.store.reader.RotatingCloudEncryptionKeyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

public class CloudEncryptionKeyManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(CloudEncryptionKeyManager.class);

    private final RotatingCloudEncryptionKeyProvider RotatingCloudEncryptionKeyProvider;
    private final CloudEncryptionKeyStoreWriter cloudEncryptionKeyStoreWriter;
    private final CloudSecretGenerator secretGenerator;

    public CloudEncryptionKeyManager(
            RotatingCloudEncryptionKeyProvider RotatingCloudEncryptionKeyProvider,
            CloudEncryptionKeyStoreWriter cloudEncryptionKeyStoreWriter,
            CloudSecretGenerator keyGenerator
    ) {
        this.RotatingCloudEncryptionKeyProvider = RotatingCloudEncryptionKeyProvider;
        this.cloudEncryptionKeyStoreWriter = cloudEncryptionKeyStoreWriter;
        this.secretGenerator = keyGenerator;
    }

    // Ensures there are `keyCountPerSite` sites for each site corresponding of operatorKeys. If there are less - create new ones.
    // Give all new keys for each site `activationInterval` seconds between activations, starting now
    public void generateKeysForOperators(
            Collection<OperatorKey> operatorKeys,
            long activationInterval,
            int keyCountPerSite
    ) throws Exception {
        this.RotatingCloudEncryptionKeyProvider.loadContent();

        if (operatorKeys == null || operatorKeys.isEmpty()) {
            throw new IllegalArgumentException("Operator keys collection must not be null or empty");
        }
        if (activationInterval <= 0) {
            throw new IllegalArgumentException("Key activate interval must be greater than zero");
        }
        if (keyCountPerSite <= 0) {
            throw new IllegalArgumentException("Key count per site must be greater than zero");
        }

        for (Integer siteId : uniqueSiteIdsForOperators(operatorKeys)) {
            ensureEnoughKeysForSite(activationInterval, keyCountPerSite, siteId);
        }
    }

    private void ensureEnoughKeysForSite(long activationInterval, int keyCountPerSite, Integer siteId) throws Exception {
        // Check if the site ID already exists in the S3 key provider and has fewer than the required number of keys
        int currentKeyCount = countKeysForSite(siteId);
        if (currentKeyCount >= keyCountPerSite) {
            LOGGER.info("Site ID {} already has the required number of keys. Skipping key generation.", siteId);
            return;
        }

        int keysToGenerate = keyCountPerSite - currentKeyCount;
        for (int i = 0; i < keysToGenerate; i++) {
            addKey(activationInterval, siteId, i);
        }
        LOGGER.info("Generated {} keys for site ID {}", keysToGenerate, siteId);
    }

    private void addKey(long keyActivateInterval, Integer siteId, int keyIndex) throws Exception {
        long created = Instant.now().getEpochSecond();
        long activated = created + (keyIndex * keyActivateInterval);
        CloudEncryptionKey cloudEncryptionKey = generateCloudEncryptionKey(siteId, activated, created);
        addCloudEncryptionKey(cloudEncryptionKey);
    }

    private static Set<Integer> uniqueSiteIdsForOperators(Collection<OperatorKey> operatorKeys) {
        Set<Integer> uniqueSiteIds = new HashSet<>();
        for (OperatorKey operatorKey : operatorKeys) {
            uniqueSiteIds.add(operatorKey.getSiteId());
        }
        return uniqueSiteIds;
    }

    CloudEncryptionKey generateCloudEncryptionKey(int siteId, long activates, long created) throws Exception {
        int newKeyId = getNextKeyId();
        String secret = secretGenerator.generate();
        return new CloudEncryptionKey(newKeyId, siteId, activates, created, secret);
    }

    void addCloudEncryptionKey(CloudEncryptionKey cloudEncryptionKey) throws Exception {
        Map<Integer, CloudEncryptionKey> cloudEncryptionKeys = new HashMap<>(RotatingCloudEncryptionKeyProvider.getAll());
        cloudEncryptionKeys.put(cloudEncryptionKey.getId(), cloudEncryptionKey);
        cloudEncryptionKeyStoreWriter.upload(cloudEncryptionKeys, null);
    }

    int getNextKeyId() {
        Map<Integer, CloudEncryptionKey> cloudEncryptionKeys = RotatingCloudEncryptionKeyProvider.getAll();
        if (cloudEncryptionKeys == null || cloudEncryptionKeys.isEmpty()) {
            return 1;
        }
        return cloudEncryptionKeys.keySet().stream().max(Integer::compareTo).orElse(0) + 1;
    }

    int countKeysForSite(int siteId) {
        Map<Integer, CloudEncryptionKey> allKeys = RotatingCloudEncryptionKeyProvider.getAll();
        return (int) allKeys.values().stream().filter(key -> key.getSiteId() == siteId).count();
    }
}