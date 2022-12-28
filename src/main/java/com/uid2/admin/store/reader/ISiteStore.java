package com.uid2.admin.store.reader;

import com.uid2.admin.model.Site;

import java.util.Collection;

public interface ISiteStore {
    Collection<Site> getAllSites();
    Site getSite(int siteId);
}
