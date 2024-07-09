package com.uid2.admin.store.factory;

import com.uid2.admin.store.writer.StoreWriter;
import com.uid2.shared.auth.Keyset;
import com.uid2.shared.store.EncryptedScopedStoreReader;
import com.uid2.shared.store.reader.RotatingS3KeyProvider;
import com.uid2.shared.store.reader.StoreReader;

import java.util.Map;

public interface EncryptedStoreFactory<T> extends StoreFactory<T> {
    StoreWriter<T> getEncryptedWriter(Integer siteId, boolean isPublic);
    StoreReader<T>  getEncryptedReader (Integer siteId, boolean isPublic);
    RotatingS3KeyProvider getS3Provider();
}