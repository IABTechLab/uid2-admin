package com.uid2.admin.job.jobsync;

import com.uid2.admin.job.model.Job;
import com.uid2.admin.model.PrivateSiteDataMap;
import com.uid2.admin.model.Site;
import com.uid2.admin.store.SiteStoreFactory;
import com.uid2.admin.store.reader.StoreReader;
import com.uid2.admin.util.PrivateSiteUtil;
import com.uid2.shared.auth.OperatorKey;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class SiteSyncJob implements Job {
    private final Collection<OperatorKey> globalOperators;
    private final Collection<Site> globalSites;
    private final SiteStoreFactory storeFactory;

    public SiteSyncJob(SiteStoreFactory storeFactory, Collection<Site> globalSites, Collection<OperatorKey> globalOperators) {
        this.storeFactory = storeFactory;
        this.globalSites = globalSites;
        this.globalOperators = globalOperators;
    }

    @Override
    public String getId() {
        return "global-to-site-scope-sync-sites";
    }

    @Override
    public void execute() throws Exception {
        PrivateSiteDataMap<Site> desiredState = PrivateSiteUtil.getSites(globalSites, globalOperators);
        PrivateSiteDataMap<Site> currentState = getCurrentState(desiredState.keySet());
        List<Integer> sitesToWrite = getSitesToWrite(desiredState, currentState);
        write(desiredState, sitesToWrite);
    }

    private void write(PrivateSiteDataMap<Site> desiredState, Collection<Integer> sitesToWrite) throws Exception {
        for (Integer addedSite : sitesToWrite) {
            storeFactory.getWriter(addedSite).upload(desiredState.get(addedSite));
        }
    }

    private static List<Integer> getSitesToWrite(
            PrivateSiteDataMap<Site> desiredState,
            PrivateSiteDataMap<Site> currentState) {
        return desiredState.keySet().stream().filter(siteId -> {
            boolean isNewSite = !currentState.containsKey(siteId);
            if (isNewSite) {
                return true;
            }

            return !areEqual(desiredState.get(siteId), currentState.get(siteId));
        }).collect(Collectors.toList());
    }

    private static boolean areEqual(Collection<Site> current, Collection<Site> desired) {
        return current.size() == desired.size() && current.containsAll(desired);
    }

    private PrivateSiteDataMap<Site> getCurrentState(Collection<Integer> siteIds) throws Exception {
        PrivateSiteDataMap<Site> currentState = new PrivateSiteDataMap<>();
        for (Integer siteId : siteIds) {
            StoreReader<Site> reader = storeFactory.getReader(siteId);
            if (reader.getMetadata() != null) {
                reader.loadContent();
                currentState.put(siteId, reader.getAll());
            }
        }
        return currentState;
    }
}
