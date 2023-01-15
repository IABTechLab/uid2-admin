package com.uid2.admin.job.jobsync;

import com.uid2.admin.job.model.Job;
import com.uid2.admin.model.PrivateSiteDataMap;
import com.uid2.admin.store.KeyAclStoreFactory;
import com.uid2.admin.util.PrivateSiteUtil;
import com.uid2.shared.auth.EncryptionKeyAcl;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.store.reader.RotatingKeyAclProvider;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class KeyAclSyncJob implements Job {
    private final Collection<OperatorKey> globalOperators;
    private final Map<Integer, EncryptionKeyAcl> globalAcls;
    private final KeyAclStoreFactory factory;

    public KeyAclSyncJob(KeyAclStoreFactory factory,
                         Collection<OperatorKey> globalOperators,
                         Map<Integer, EncryptionKeyAcl> globalAcls) {
        this.factory = factory;
        this.globalOperators = globalOperators;
        this.globalAcls = globalAcls;
    }

    @Override
    public String getId() {
        return "global-to-site-scope-sync-keyAcls";
    }

    @Override
    public void execute() throws Exception {
        HashMap<Integer, Map<Integer, EncryptionKeyAcl>> desiredState = PrivateSiteUtil.getEncryptionKeyAcls2(globalOperators, globalAcls);
        HashMap<Integer, Map<Integer, EncryptionKeyAcl>> currentState = getCurrentState(desiredState.keySet());
        write(desiredState, getSitesToWrite(desiredState, currentState));
    }

    private void write(HashMap<Integer, Map<Integer, EncryptionKeyAcl>> desiredState, Collection<Integer> sitesToWrite) throws Exception {
        for (Integer addedSite : sitesToWrite) {
            factory.getWriter(addedSite).upload(desiredState.get(addedSite));
        }
    }

    private static List<Integer> getSitesToWrite(
            HashMap<Integer, Map<Integer, EncryptionKeyAcl>> desiredState,
            HashMap<Integer, Map<Integer, EncryptionKeyAcl>> currentState) {
        return desiredState.keySet().stream().filter(siteId -> {
            boolean isNewSite = !currentState.containsKey(siteId);
            if (isNewSite) {
                return true;
            }

            return !areEqual(desiredState.get(siteId), currentState.get(siteId));
        }).collect(Collectors.toList());
    }

    private static boolean areEqual(Map<Integer, EncryptionKeyAcl> current, Map<Integer, EncryptionKeyAcl> desired) {
        return current.size() == desired.size() && current.entrySet().containsAll(desired.entrySet());
    }

    private HashMap<Integer, Map<Integer, EncryptionKeyAcl>> getCurrentState(Collection<Integer> siteIds) throws Exception {
        HashMap<Integer, Map<Integer, EncryptionKeyAcl>> currentState = new HashMap<>();
        for (Integer siteId : siteIds) {
            RotatingKeyAclProvider reader = factory.getReader(siteId);
            if (reader.getMetadata() != null) {
                reader.loadContent();
                currentState.put(siteId, reader.getSnapshot().getAllAcls());
            }
        }
        return currentState;
    }
}
