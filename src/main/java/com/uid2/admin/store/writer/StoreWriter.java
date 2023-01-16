package com.uid2.admin.store.writer;

public interface StoreWriter<T> {
    void upload(T data) throws Exception;
}
