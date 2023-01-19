package com.uid2.admin.model;

import java.util.Collection;
import java.util.HashMap;
import java.util.Set;

/**
 * Private Site is defined as a site that has at least 1 private operator.
 * Data type: Map<SiteId, Collection<Config>>
 */
public class PrivateSiteDataMap<T> extends HashMap<Integer, Collection<T>> {
    public PrivateSiteDataMap<T> with(Integer siteId, Set<T> data) {
        this.put(siteId, data);
        return this;
    }
}
