package com.uid2.admin.cloudencryption;

import com.uid2.admin.job.model.Job;

public class CloudEncryptionKeyRotationJob extends Job {
    private final CloudEncryptionKeyManager keyManager;

    public CloudEncryptionKeyRotationJob(CloudEncryptionKeyManager keyManager) {
        this.keyManager = keyManager;
    }

    @Override
    public String getId() {
        return "cloud-encryption-key-rotation";
    }

    @Override
    public void execute() throws Exception {
        keyManager.rotateKeys();
    }
}
