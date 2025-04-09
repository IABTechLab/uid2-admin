package com.uid2.admin.cloudencryption;

import com.uid2.admin.job.model.Job;

public class CloudEncryptionKeyRotationJob extends Job {
    private final CloudEncryptionKeyManager keyManager;
    private final boolean shouldFail;

    public CloudEncryptionKeyRotationJob(CloudEncryptionKeyManager keyManager, boolean shouldFail) {
        this.keyManager = keyManager;
        this.shouldFail = shouldFail;
    }

    @Override
    public String getId() {
        return "cloud-encryption-key-rotation";
    }

    @Override
    public void execute() throws Exception {
        keyManager.rotateKeys(shouldFail);
    }
}
