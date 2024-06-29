package com.uid2.admin.store;

import com.uid2.admin.model.PrivateSiteDataMap;
import com.uid2.admin.store.factory.StoreFactory;
import com.uid2.admin.store.writer.ScopedStoreWriter;
import com.uid2.shared.model.KeysetKey;
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
    
    //up load if change for plain text files
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
                try {
                    reader.loadContent();
                    currentState.put(siteId, reader.getAll());
                } catch (Exception e) {
                }
            }
        }
        return currentState;
    }
    
    //upload Encrypted for encyrpted files
    public void uploadEncrypted(Map<Integer, T> desiredState, JsonObject extraMeta) throws Exception {
        writeEncrypted(desiredState, extraMeta);
    }
    
    private void writeEncrypted(Map<Integer, T> desiredState, JsonObject extraMeta) throws Exception {
        for (Map.Entry<Integer, T> entry : desiredState.entrySet()) {
            Integer siteId = entry.getKey();
            if (siteId != null) {
                factory.getEncryptedWriter(siteId).upload(desiredState.get(siteId), extraMeta);
            }
        }
    }

    public static <K, V> boolean areMapsEqual(Map<K, V> a, Map<K, V> b) {
        return a.size() == b.size() && a.entrySet().stream().allMatch(b.entrySet()::contains);
    }

    public static <T> boolean areCollectionsEqual(Collection<T> a, Collection<T> b) {
        return a.size() == b.size() && a.stream().allMatch(b::contains);
    }

    public void uploadGeneral(Map<Integer, T> desiredState, JsonObject extraMeta) throws Exception {
        if (factory.getS3Provider() != null) {
            // If S3 provider is available, use encrypted upload
            uploadEncrypted(desiredState, extraMeta);
        } else {
            // Otherwise, use plaintext upload
            uploadIfChanged(desiredState, extraMeta);
        }
    }
}