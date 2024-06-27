package com.uid2.admin.store;

import com.uid2.admin.store.factory.StoreFactory;
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

    public void uploadIfChanged(Map<Integer, T> desiredState, JsonObject extraMeta) throws Exception {
        Map<Integer, T> currentState = getCurrentState(desiredState.keySet());
        List<Integer> sitesToWrite = getSitesToWrite(desiredState, currentState);
        write(desiredState, sitesToWrite, extraMeta);
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

    private void write(Map<Integer, T> desiredState, Collection<Integer> sitesToWrite, JsonObject extraMeta) throws Exception {
        for (Integer addedSite : sitesToWrite) {
            factory.getWriter(addedSite).upload(desiredState.get(addedSite), extraMeta);
            factory.getEncryptedWriter(addedSite).upload(desiredState.get(addedSite), extraMeta);

        }
    }

    public static <K, V> boolean areMapsEqual(Map<K, V> a, Map<K, V> b) {
        return a.size() == b.size() && a.entrySet().stream().allMatch(b.entrySet()::contains);
    }

    public static <T> boolean areCollectionsEqual(Collection<T> a, Collection<T> b) {
        return a.size() == b.size() && a.stream().allMatch(b::contains);
    }
}
