package com.uid2.admin.cloudencryption;

import com.uid2.admin.model.CloudEncryptionKeySummary;
import com.uid2.admin.store.writer.CloudEncryptionKeyStoreWriter;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.auth.RotatingOperatorKeyProvider;
import com.uid2.shared.model.CloudEncryptionKey;
import com.uid2.shared.store.reader.RotatingCloudEncryptionKeyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CloudEncryptionKeyManager {
    private final RotatingCloudEncryptionKeyProvider keyProvider;
    private final RotatingOperatorKeyProvider operatorKeyProvider;
    private final CloudEncryptionKeyStoreWriter keyWriter;
    private final CloudKeyStatePlanner planner;
    private Set<OperatorKey> operatorKeys;
    private Set<CloudEncryptionKey> existingKeys;

    private static final Logger LOGGER = LoggerFactory.getLogger(CloudEncryptionKeyManager.class);

    public CloudEncryptionKeyManager(
            RotatingCloudEncryptionKeyProvider keyProvider,
            CloudEncryptionKeyStoreWriter keyWriter,
            RotatingOperatorKeyProvider operatorKeyProvider,
            CloudKeyStatePlanner planner) {
        this.keyProvider = keyProvider;
        this.operatorKeyProvider = operatorKeyProvider;
        this.keyWriter = keyWriter;
        this.planner = planner;
    }

    // For any site that has an operator create a new key activating in one hour
    // Keep up to 10 most recent old keys per site, delete the rest
    public void rotateKeys(boolean shouldFail) throws Exception {
        try {
            refreshCloudData();
            var desiredKeys = planner.planRotation(existingKeys, operatorKeys);
            if (shouldFail) {
                throw new Exception("Failing key rotation on demand due to `fail` query param being passed");
            }
            writeKeys(desiredKeys);
            var diff = CloudEncryptionKeyDiff.calculateDiff(existingKeys, desiredKeys);
            LOGGER.info("Key rotation complete. Diff: {}", diff);
        } catch (Exception e) {
            LOGGER.error("Key rotation failed", e);
            throw e;
        }
    }

    // For any site that has an operator, if there are no keys, create a key activating now
    public void backfillKeys() throws Exception {
        try {
            refreshCloudData();
            var desiredKeys = planner.planBackfill(existingKeys, operatorKeys);
            writeKeys(desiredKeys);
            var diff = CloudEncryptionKeyDiff.calculateDiff(existingKeys, desiredKeys);
            LOGGER.info("Key backfill complete. Diff: {}", diff);
        } catch (Exception e) {
            LOGGER.error("Key backfill failed", e);
            throw e;
        }
    }

    public Set<CloudEncryptionKeySummary> getKeySummaries() throws Exception {
        refreshCloudData();
        return existingKeys.stream().map(CloudEncryptionKeySummary::fromFullKey).collect(Collectors.toSet());
    }

    private void writeKeys(Set<CloudEncryptionKey> desiredKeys) throws Exception {
        var keysForWriting = desiredKeys.stream().collect(Collectors.toMap(
                CloudEncryptionKey::getId,
                Function.identity())
        );
        keyWriter.upload(keysForWriting, null);
    }

    private void refreshCloudData() throws Exception {
        keyProvider.loadContent();
        operatorKeyProvider.loadContent(operatorKeyProvider.getMetadata());
        operatorKeys = new HashSet<>(operatorKeyProvider.getAll());
        existingKeys = new HashSet<>(keyProvider.getAll().values());
    }
}