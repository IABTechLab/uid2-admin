package com.uid2.admin.job.jobsync;

import com.uid2.admin.job.model.Job;
import com.uid2.admin.model.PrivateSiteDataMap;
import com.uid2.admin.store.EncryptionKeyStoreFactory;
import com.uid2.admin.util.MaxKeyUtil;
import com.uid2.admin.util.PrivateSiteUtil;
import com.uid2.shared.auth.ClientKey;
import com.uid2.shared.auth.EncryptionKeyAcl;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.model.EncryptionKey;
import com.uid2.shared.store.reader.RotatingKeyStore;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class EncryptionKeySyncJob implements Job {
    private final Collection<OperatorKey> globalOperators;
    private final Collection<EncryptionKey> globalEncryptionKeys;
    private final Collection<ClientKey> globalClientKeys;
    private final Map<Integer, EncryptionKeyAcl> globalAcls;
    private final EncryptionKeyStoreFactory factory;
    private final Integer globalMaxKeyId;

    public EncryptionKeySyncJob(EncryptionKeyStoreFactory factory,
                                Collection<EncryptionKey> globalEncryptionKeys,
                                Collection<ClientKey> globalClientKeys,
                                Collection<OperatorKey> globalOperators,
                                Map<Integer, EncryptionKeyAcl> globalAcls,
                                Integer globalMaxKeyId) {
        this.factory = factory;
        this.globalEncryptionKeys = globalEncryptionKeys;
        this.globalClientKeys = globalClientKeys;
        this.globalOperators = globalOperators;
        this.globalAcls = globalAcls;
        this.globalMaxKeyId = globalMaxKeyId;
    }

    @Override
    public String getId() {
        return "global-to-site-scope-sync-encryptionKeys";
    }

    @Override
    public void execute() throws Exception {
        PrivateSiteDataMap<EncryptionKey> desiredState = PrivateSiteUtil.getEncryptionKeys(globalOperators, globalEncryptionKeys, globalAcls, globalClientKeys);
        PrivateSiteDataMap<EncryptionKey> currentState = getCurrentState(desiredState.keySet());
        write(desiredState, getSitesToWrite(desiredState, currentState));
    }

    private void write(PrivateSiteDataMap<EncryptionKey> desiredState, Collection<Integer> sitesToWrite) throws Exception {
        for (Integer addedSite : sitesToWrite) {
            Collection<EncryptionKey> siteKeys = desiredState.get(addedSite);
            int maxKeyId = MaxKeyUtil.getMaxKeyId(siteKeys, this.globalMaxKeyId);
            factory.getWriter(addedSite).upload(siteKeys, maxKeyId);
        }
    }

    private static List<Integer> getSitesToWrite(
            PrivateSiteDataMap<EncryptionKey> desiredState,
            PrivateSiteDataMap<EncryptionKey> currentState) {
        return desiredState.keySet().stream().filter(siteId -> {
            boolean isNewSite = !currentState.containsKey(siteId);
            if (isNewSite) {
                return true;
            }

            return !areEqual(desiredState.get(siteId), currentState.get(siteId));
        }).collect(Collectors.toList());
    }

    private static boolean areEqual(Collection<EncryptionKey> current, Collection<EncryptionKey> desired) {
        return current.size() == desired.size() && current.containsAll(desired);
    }

    private PrivateSiteDataMap<EncryptionKey> getCurrentState(Collection<Integer> siteIds) throws Exception {
        PrivateSiteDataMap<EncryptionKey> currentState = new PrivateSiteDataMap<>();
        for (Integer siteId : siteIds) {
            RotatingKeyStore reader = factory.getReader(siteId);
            if (reader.getMetadata() != null) {
                reader.loadContent();
                currentState.put(siteId, reader.getSnapshot().getActiveKeySet());
            }
        }
        return currentState;
    }
}
