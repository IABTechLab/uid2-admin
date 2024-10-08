package com.uid2.admin.managers;

import com.uid2.admin.store.writer.S3KeyStoreWriter;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.model.S3Key;
import com.uid2.shared.secret.IKeyGenerator;
import com.uid2.shared.secret.SecureKeyGenerator;
import com.uid2.shared.store.reader.RotatingS3KeyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import java.time.Instant;
import java.util.*;

public class S3KeyManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3KeyManager.class);

    private final RotatingS3KeyProvider s3KeyProvider;
    private final S3KeyStoreWriter s3KeyStoreWriter;
    private final IKeyGenerator keyGenerator;

    public S3KeyManager(RotatingS3KeyProvider s3KeyProvider, S3KeyStoreWriter s3KeyStoreWriter, IKeyGenerator keyGenerator) {
        this.s3KeyProvider = s3KeyProvider;
        this.s3KeyStoreWriter = s3KeyStoreWriter;
        this.keyGenerator = keyGenerator;
    }

    S3Key generateS3Key(int siteId, long activates, long created) throws Exception {
        int newKeyId = getNextKeyId();
        String secret = generateSecret();
        return new S3Key(newKeyId, siteId, activates, created, secret);
    }

    String generateSecret() throws Exception {
        //Generate a 32-byte key for AesGcm
        return keyGenerator.generateRandomKeyString(32);
    }

    void addS3Key(S3Key s3Key) throws Exception {
        Map<Integer, S3Key> s3Keys = new HashMap<>(s3KeyProvider.getAll());
        s3Keys.put(s3Key.getId(), s3Key);
        s3KeyStoreWriter.upload(s3Keys, null);
    }

    int getNextKeyId() {
        Map<Integer, S3Key> s3Keys = s3KeyProvider.getAll();
        if (s3Keys == null || s3Keys.isEmpty()) {
            return 1;
        }
        return s3Keys.keySet().stream().max(Integer::compareTo).orElse(0) + 1;
    }

    // Method to create and upload an S3 key that activates immediately for a specific site, for emergency rotation
    public S3Key createAndAddImmediate3Key(int siteId) throws Exception {
        int newKeyId = getNextKeyId();
        long created = Instant.now().getEpochSecond();
        S3Key newKey = new S3Key(newKeyId, siteId, created, created, generateSecret());
        addS3Key(newKey);
        return newKey;
    }

    public S3Key getS3KeyByKeyIdentifier(int keyIdentifier) {
        return s3KeyProvider.getAll().get(keyIdentifier);
    }

    public Optional<S3Key> getS3KeyBySiteId(int siteId) {
        return s3KeyProvider.getAll().values().stream()
                .filter(key -> key.getSiteId() == siteId)
                .findFirst();
    }

    public List<S3Key> getAllS3KeysBySiteId(int siteId) {
        return s3KeyProvider.getAll().values().stream()
                .filter(key -> key.getSiteId() == siteId)
                .collect(Collectors.toList());
    }

    public Map<Integer, S3Key> getAllS3Keys() {
        return s3KeyProvider.getAll();
    }

    public boolean doesSiteHaveKeys(int siteId) {
        Map<Integer, S3Key> allKeys = s3KeyProvider.getAll();
        if (allKeys == null) {
            return false;
        }
        return allKeys.values().stream().anyMatch(key -> key.getSiteId() == siteId);
    }

    int countKeysForSite(int siteId) {
        Map<Integer, S3Key> allKeys = s3KeyProvider.getAll();
        return (int) allKeys.values().stream().filter(key -> key.getSiteId() == siteId).count();
    }

    public void generateKeysForOperators(Collection<OperatorKey> operatorKeys, long keyActivateInterval, int keyCountPerSite) throws Exception {
        this.s3KeyProvider.loadContent();

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
                    S3Key s3Key = generateS3Key(siteId, activated, created);
                    addS3Key(s3Key);
                }
                LOGGER.info("Generated " + keysToGenerate + " keys for site ID " + siteId);
            } else {
                LOGGER.info("Site ID " + siteId + " already has the required number of keys. Skipping key generation.");
            }
        }
    }
}