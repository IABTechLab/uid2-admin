package com.uid2.admin.job.jobsync;

import com.uid2.admin.job.model.Job;
import com.uid2.admin.model.PrivateSiteDataMap;
import com.uid2.admin.store.ClientKeyStoreFactory;
import com.uid2.admin.util.PrivateSiteUtil;
import com.uid2.shared.auth.ClientKey;
import com.uid2.shared.auth.OperatorKey;
import com.uid2.shared.store.reader.RotatingClientKeyProvider;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class ClientKeySyncJob implements Job {
    private final Collection<OperatorKey> globalOperators;
    private final Collection<ClientKey> globalClientKeys;
    private final ClientKeyStoreFactory clientKeyStoreFactory;

    public ClientKeySyncJob(ClientKeyStoreFactory clientKeyStoreFactory, Collection<ClientKey> globalClientKeys, Collection<OperatorKey> globalOperators) {
        this.clientKeyStoreFactory = clientKeyStoreFactory;
        this.globalClientKeys = globalClientKeys;
        this.globalOperators = globalOperators;
    }

    @Override
    public String getId() {
        return "global-to-site-scope-sync-clientKeys";
    }

    @Override
    public void execute() throws Exception {
        PrivateSiteDataMap<ClientKey> desiredState = PrivateSiteUtil.getClientKeys(globalOperators, globalClientKeys);
        PrivateSiteDataMap<ClientKey> currentState = getCurrentState(desiredState.keySet());
        write(desiredState, getSitesToWrite(desiredState, currentState));
    }

    private void write(PrivateSiteDataMap<ClientKey> desiredState, Collection<Integer> sitesToWrite) throws Exception {
        for (Integer addedSite : sitesToWrite) {
            clientKeyStoreFactory.getWriter(addedSite).upload(desiredState.get(addedSite));
        }
    }

    private static List<Integer> getSitesToWrite(
            PrivateSiteDataMap<ClientKey> desiredState,
            PrivateSiteDataMap<ClientKey> currentState) {
        return desiredState.keySet().stream().filter(siteId -> {
            boolean isNewSite = !currentState.containsKey(siteId);
            if (isNewSite) {
                return true;
            }

            return !areEqual(desiredState.get(siteId), currentState.get(siteId));
        }).collect(Collectors.toList());
    }

    private static boolean areEqual(Collection<ClientKey> current, Collection<ClientKey> desired) {
        return current.size() == desired.size() && current.containsAll(desired);
    }

    private PrivateSiteDataMap<ClientKey> getCurrentState(Collection<Integer> siteIds) throws Exception {
        PrivateSiteDataMap<ClientKey> currentState = new PrivateSiteDataMap<>();
        for (Integer siteId : siteIds) {
            RotatingClientKeyProvider reader = clientKeyStoreFactory.getReader(siteId);
            if (reader.getMetadata() != null) {
                reader.loadContent();
                currentState.put(siteId, reader.getAll());
            }
        }
        return currentState;
    }
}
