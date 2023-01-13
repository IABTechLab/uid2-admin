package com.uid2.admin.store.parser;

import com.uid2.admin.model.Site;
import com.uid2.shared.Utils;
import com.uid2.shared.store.parser.Parser;
import com.uid2.shared.store.parser.ParsingResult;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class SiteParser implements Parser<Map<Integer, Site>> {
    @Override
    public ParsingResult<Map<Integer, Site>> deserialize(InputStream inputStream) throws IOException {
        final JsonArray sitesSpec = Utils.toJsonArray(inputStream);
        final HashMap<Integer, Site> sites = new HashMap<>();
        for (int i = 0; i < sitesSpec.size(); ++i) {
            JsonObject siteSpec = sitesSpec.getJsonObject(i);
            final int siteId = siteSpec.getInteger("id");
            final String name = siteSpec.getString("name");
            final Boolean enabled = siteSpec.getBoolean("enabled", false);
            final Site site = new Site(siteId, name, enabled);
            sites.put(site.getId(), site);
        }
        return new ParsingResult<>(sites, sites.size());
    }
}
