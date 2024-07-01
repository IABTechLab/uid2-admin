package com.uid2.admin.store.factory;

import com.uid2.admin.store.writer.EnclaveStoreWriter;
import com.uid2.admin.store.writer.StoreWriter;
import com.uid2.shared.store.reader.RotatingS3KeyProvider;
import com.uid2.shared.store.reader.StoreReader;

public interface StoreFactory<T> {

    StoreReader<T> getReader(Integer siteId);
    StoreWriter<T> getWriter(Integer siteId);
}
