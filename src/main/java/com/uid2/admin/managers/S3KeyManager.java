package com.uid2.admin.managers;

import com.uid2.admin.store.writer.S3KeyStoreWriter;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.model.S3Key;
import com.uid2.shared.secret.IKeyGenerator;
import com.uid2.shared.store.reader.RotatingS3KeyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public S3Key generateS3Key(int siteId, long activates, long created) throws Exception {
        int newKeyId = getNextKeyId();
        S3Key newS3Key = new S3Key();
        newS3Key.setId(newKeyId);
        newS3Key.setSiteId(siteId);
        newS3Key.setActivates(activates);
        newS3Key.setCreated(created);
        newS3Key.setSecret(generateSecret());
        return newS3Key;
    }

    private String generateSecret() throws Exception {
        // Assuming want to generate a 32-byte key
        return keyGenerator.generateRandomKeyString(32);
    }

    public void addS3Key(S3Key s3Key) throws Exception {
        Map<Integer, S3Key> s3Keys = new HashMap<>(s3KeyProvider.getAll());
        s3Keys.put(s3Key.getId(), s3Key);
        s3KeyStoreWriter.upload(s3Keys, null);
    }

    public void addOrUpdateS3Key(S3Key s3Key) throws Exception {
        Map<Integer, S3Key> s3Keys = s3KeyProvider.getAll();
        if (s3Keys == null) {
            s3Keys = new HashMap<>();
        } else {
            s3Keys = new HashMap<>(s3Keys);
        }
        s3Keys.put(s3Key.getId(), s3Key);
        s3KeyStoreWriter.upload(s3Keys, null);
    }


    protected int getNextKeyId() {
        Map<Integer, S3Key> s3Keys = s3KeyProvider.getAll();
        if (s3Keys == null || s3Keys.isEmpty()) {
            return 1;
        }
        return s3Keys.keySet().stream().max(Integer::compareTo).orElse(0) + 1;
    }


    public S3Key getS3Key(int id) {
        return s3KeyProvider.getAll().get(id);
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

    public void generateKeysForOperators(Collection<OperatorKey> operatorKeys, long keyActivateInterval, int keyCountPerSite) throws Exception {
        Set<Integer> uniqueSiteIds = new HashSet<>();
        for (OperatorKey operatorKey : operatorKeys) {
            uniqueSiteIds.add(operatorKey.getSiteId());
        }

        for (Integer siteId : uniqueSiteIds) {
            if (!doesSiteHaveKeys(siteId)) {
                for (int i = 0; i < keyCountPerSite; i++) {
                    long created = Instant.now().getEpochSecond();
                    long activated = created + (i * keyActivateInterval);
                    addOrUpdateS3Key(generateS3Key(siteId, activated, created));
                }
            }
        }
    }


}
