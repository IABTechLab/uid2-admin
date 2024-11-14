package com.uid2.admin.store.factory;

import com.uid2.admin.store.writer.StoreWriter;
import com.uid2.shared.store.reader.RotatingCloudEncryptionKeyProvider;
import com.uid2.shared.store.reader.StoreReader;

public interface EncryptedStoreFactory<T> extends StoreFactory<T> {
    StoreWriter<T> getEncryptedWriter(Integer siteId, boolean isPublic);
    StoreReader<T>  getEncryptedReader (Integer siteId, boolean isPublic);
    RotatingCloudEncryptionKeyProvider getCloudEncryptionProvider();
}