package com.uid2.admin.model;

import java.util.Collection;
import java.util.HashMap;

/**
 * Map<SiteId, Collection<Config>> which has an entry for each site that has at least 1 private operator
 */
public class SyncedSiteDataMap<T> extends HashMap<Integer, Collection<T>> {
}
