package com.uid2.admin.store.factory;

import com.uid2.admin.store.writer.StoreWriter;
import com.uid2.shared.store.reader.RotatingS3KeyProvider;

public interface EncryptedStoreFactory<T> extends StoreFactory<T> {
    StoreWriter<T> getEncryptedWriter(Integer siteId, boolean isPublic);
    RotatingS3KeyProvider getS3Provider();
}