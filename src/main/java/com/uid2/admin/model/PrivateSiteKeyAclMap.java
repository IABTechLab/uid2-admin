package com.uid2.admin.model;

import java.util.Collection;
import java.util.HashMap;

/**
 * Private Site is defined as a site that has at least 1 private operator.
 * Data type: Map<SiteId, Collection<Config>>
 */
public class PrivateSiteDataMap<T> extends HashMap<Integer, Collection<T>> {
}
