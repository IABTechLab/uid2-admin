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
    private Collection<OperatorKey> operatorKeys;
    private Collection<CloudEncryptionKey> existingKeys;

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

    private static final Logger logger = LoggerFactory.getLogger(CloudEncryptionKeyManager.class);

    // For any site that has an operator create a new key activating now
    // Keep up to 5 most recent old keys per site, delete the rest
    public void rotateKeys() throws Exception {
        refreshCloudData();
        var desiredKeys = planner.planRotation(existingKeys, operatorKeys);
        writeKeys(desiredKeys);
    }

    // For any site that has an operator, if there are no keys, create a key activating now
    public void backfillKeys() throws Exception {
        refreshCloudData();
        var desiredKeys = planner.planBackfill(existingKeys, operatorKeys);
        writeKeys(desiredKeys);
    }

    public List<CloudEncryptionKeySummary> getKeySummaries() throws Exception {
        refreshCloudData();
        return existingKeys.stream().map(CloudEncryptionKeySummary::fromFullKey).toList();
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
        operatorKeys = operatorKeyProvider.getAll();
        existingKeys = keyProvider.getAll().values();
    }
}