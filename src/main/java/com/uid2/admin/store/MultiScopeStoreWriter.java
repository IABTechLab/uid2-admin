package com.uid2.admin.store;

import com.uid2.admin.model.PrivateSiteDataMap;
import com.uid2.admin.store.factory.StoreFactory;
import com.uid2.admin.store.writer.EncryptedScopedStoreWriter;
import com.uid2.admin.store.writer.ScopedStoreWriter;
import com.uid2.shared.model.KeysetKey;

import com.uid2.admin.store.factory.EncryptedStoreFactory;
import com.uid2.shared.model.Site;
import com.uid2.shared.store.reader.StoreReader;
import io.vertx.core.json.JsonObject;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class MultiScopeStoreWriter<T> {
    private final FileManager fileManager;
    private final StoreFactory<T> factory;
    private final BiFunction<T, T, Boolean> areEqual;
    public MultiScopeStoreWriter(FileManager fileManager, StoreFactory<T> factory, BiFunction<T, T, Boolean> areEqual) {
        this.fileManager = fileManager;
        this.factory = factory;
        this.areEqual = areEqual;
    }

    public void uploadWithEncryptionOrChanges(Map<Integer, T> desiredState, JsonObject extraMeta) throws Exception {
        if (supportsEncryption() && ((EncryptedStoreFactory<T>)factory).getS3Provider() != null) {
            //upload encrypted files and make them all site specific
            uploadEncrypted(desiredState, extraMeta);
        } else {
            //upload plain text files
            uploadIfChanged(desiredState, extraMeta);
        }
    }


    public void uploadIfChanged(Map<Integer, T> desiredState, JsonObject extraMeta) throws Exception {
        Map<Integer, T> currentState = getCurrentState(desiredState.keySet());
        List<Integer> sitesToWrite = getSitesToWrite(desiredState, currentState);
        write(desiredState, sitesToWrite, extraMeta);
    }

    private void write(Map<Integer, T> desiredState, Collection<Integer> sitesToWrite, JsonObject extraMeta) throws Exception {
        for (Integer siteToWrite : sitesToWrite) {
            factory.getWriter(siteToWrite).upload(desiredState.get(siteToWrite), extraMeta);
        }
    }

    private List<Integer> getSitesToWrite(
            Map<Integer, T> desiredState,
            Map<Integer, T> currentState) {
        return desiredState.keySet().stream().filter(siteId -> {
            boolean isNewSite = !currentState.containsKey(siteId);
            if (isNewSite) {
                return true;
            }
            return !this.areEqual.apply(desiredState.get(siteId), currentState.get(siteId));
        }).collect(Collectors.toList());
    }

    private Map<Integer, T> getCurrentState(Collection<Integer> siteIds) throws Exception {
        Map<Integer, T> currentState = new HashMap<>();
        for (Integer siteId : siteIds) {
            StoreReader<T> reader = factory.getReader(siteId);
            if (fileManager.isPresent(reader.getMetadataPath())) {
                reader.loadContent();
                currentState.put(siteId, reader.getAll());
            }
        }
        return currentState;
    }

    public void uploadEncrypted(Map<Integer, T> desiredState, JsonObject extraMeta) throws Exception {
        EncryptedStoreFactory<T> encryptedFactory = (EncryptedStoreFactory<T>) factory;
        for (Map.Entry<Integer, T> entry : desiredState.entrySet()) {
            Integer siteId = entry.getKey();
            if (siteId != null) {
                encryptedFactory.getEncryptedWriter(siteId,false).upload(desiredState.get(siteId), extraMeta);
            }
        }
    }

    public void uploadPublicWithEncryption(Map<Integer, T> desiredPublicState, JsonObject extraMeta) throws Exception {
        EncryptedStoreFactory<T> encryptedFactory = (EncryptedStoreFactory<T>) factory;
        for (Map.Entry<Integer, T> entry : desiredPublicState.entrySet()) {
            Integer siteId = entry.getKey();
            if (siteId != null) {
                encryptedFactory.getEncryptedWriter(siteId,true).upload(desiredPublicState.get(siteId), extraMeta);
            }
        }
    }

    private boolean supportsEncryption() {
        return factory instanceof EncryptedStoreFactory;
    }

    public static <K, V> boolean areMapsEqual(Map<K, V> a, Map<K, V> b) {
        return a.size() == b.size() && a.entrySet().stream().allMatch(b.entrySet()::contains);
    }

    public static <T> boolean areCollectionsEqual(Collection<T> a, Collection<T> b) {
        return a.size() == b.size() && a.stream().allMatch(b::contains);
    }

}