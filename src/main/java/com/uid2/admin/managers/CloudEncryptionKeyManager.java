package com.uid2.admin.managers;

import com.uid2.admin.store.writer.CloudEncryptionKeyStoreWriter;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.model.CloudEncryptionKey;
import com.uid2.shared.secret.IKeyGenerator;
import com.uid2.shared.store.reader.RotatingCloudEncryptionKeyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import java.time.Instant;
import java.util.*;

public class CloudEncryptionKeyManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(CloudEncryptionKeyManager.class);

    private final RotatingCloudEncryptionKeyProvider RotatingCloudEncryptionKeyProvider;
    private final CloudEncryptionKeyStoreWriter cloudEncryptionKeyStoreWriter;
    private final IKeyGenerator keyGenerator;

    public CloudEncryptionKeyManager(RotatingCloudEncryptionKeyProvider RotatingCloudEncryptionKeyProvider, CloudEncryptionKeyStoreWriter cloudEncryptionKeyStoreWriter, IKeyGenerator keyGenerator) {
        this.RotatingCloudEncryptionKeyProvider = RotatingCloudEncryptionKeyProvider;
        this.cloudEncryptionKeyStoreWriter = cloudEncryptionKeyStoreWriter;
        this.keyGenerator = keyGenerator;
    }

    CloudEncryptionKey generateCloudEncryptionKey(int siteId, long activates, long created) throws Exception {
        int newKeyId = getNextKeyId();
        String secret = generateSecret();
        return new CloudEncryptionKey(newKeyId, siteId, activates, created, secret);
    }

    String generateSecret() throws Exception {
        //Generate a 32-byte key for AesGcm
        return keyGenerator.generateRandomKeyString(32);
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

    // Method to create and upload an S3 key that activates immediately for a specific site, for emergency rotation
    public CloudEncryptionKey createAndAddImmediate3Key(int siteId) throws Exception {
        int newKeyId = getNextKeyId();
        long created = Instant.now().getEpochSecond();
        CloudEncryptionKey newKey = new CloudEncryptionKey(newKeyId, siteId, created, created, generateSecret());
        addCloudEncryptionKey(newKey);
        return newKey;
    }

    public CloudEncryptionKey getCloudEncryptionKeyByKeyIdentifier(int keyIdentifier) {
        return RotatingCloudEncryptionKeyProvider.getAll().get(keyIdentifier);
    }

    public Optional<CloudEncryptionKey> getCloudEncryptionKeyBySiteId(int siteId) {
        return RotatingCloudEncryptionKeyProvider.getAll().values().stream()
                .filter(key -> key.getSiteId() == siteId)
                .findFirst();
    }

    public List<CloudEncryptionKey> getAllCloudEncryptionKeysBySiteId(int siteId) {
        return RotatingCloudEncryptionKeyProvider.getAll().values().stream()
                .filter(key -> key.getSiteId() == siteId)
                .collect(Collectors.toList());
    }

    public Map<Integer, CloudEncryptionKey> getAllCloudEncryptionKeys() {
        return RotatingCloudEncryptionKeyProvider.getAll();
    }

    public boolean doesSiteHaveKeys(int siteId) {
        Map<Integer, CloudEncryptionKey> allKeys = RotatingCloudEncryptionKeyProvider.getAll();
        if (allKeys == null) {
            return false;
        }
        return allKeys.values().stream().anyMatch(key -> key.getSiteId() == siteId);
    }

    int countKeysForSite(int siteId) {
        Map<Integer, CloudEncryptionKey> allKeys = RotatingCloudEncryptionKeyProvider.getAll();
        return (int) allKeys.values().stream().filter(key -> key.getSiteId() == siteId).count();
    }

    public void generateKeysForOperators(Collection<OperatorKey> operatorKeys, long keyActivateInterval, int keyCountPerSite) throws Exception {
        this.RotatingCloudEncryptionKeyProvider.loadContent();

        if (operatorKeys == null || operatorKeys.isEmpty()) {
            throw new IllegalArgumentException("Operator keys collection must not be null or empty");
        }
        if (keyActivateInterval <= 0) {
            throw new IllegalArgumentException("Key activate interval must be greater than zero");
        }
        if (keyCountPerSite <= 0) {
            throw new IllegalArgumentException("Key count per site must be greater than zero");
        }

        // Extract all the unique site IDs from input operator keys collection
        Set<Integer> uniqueSiteIds = new HashSet<>();
        for (OperatorKey operatorKey : operatorKeys) {
            uniqueSiteIds.add(operatorKey.getSiteId());
        }

        for (Integer siteId : uniqueSiteIds) {
            // Check if the site ID already exists in the S3 key provider and has fewer than the required number of keys
            int currentKeyCount = countKeysForSite(siteId);
            if (currentKeyCount < keyCountPerSite) {
                int keysToGenerate = keyCountPerSite - currentKeyCount;
                for (int i = 0; i < keysToGenerate; i++) {
                    long created = Instant.now().getEpochSecond();
                    long activated = created + (i * keyActivateInterval);
                    CloudEncryptionKey cloudEncryptionKey = generateCloudEncryptionKey(siteId, activated, created);
                    addCloudEncryptionKey(cloudEncryptionKey);
                }
                LOGGER.info("Generated " + keysToGenerate + " keys for site ID " + siteId);
            } else {
                LOGGER.info("Site ID " + siteId + " already has the required number of keys. Skipping key generation.");
            }
        }
    }
}