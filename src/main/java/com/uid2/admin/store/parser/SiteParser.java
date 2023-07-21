package com.uid2.admin.store.parser;

import com.uid2.admin.model.ClientType;
import com.uid2.admin.model.Site;
import com.uid2.shared.Utils;
import com.uid2.shared.store.parser.Parser;
import com.uid2.shared.store.parser.ParsingResult;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
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
            JsonArray clientTypeSpec = siteSpec.getJsonArray("types");
            HashSet<ClientType> clientTypes = new HashSet<>();
            if(clientTypeSpec != null) {
                for(int j = 0; j < clientTypeSpec.size(); j++) {
                    clientTypes.add(Enum.valueOf(ClientType.class, clientTypeSpec.getString(j)));
                }
            }

            final Site site = new Site(siteId, name, enabled, clientTypes);
            sites.put(site.getId(), site);
        }
        return new ParsingResult<>(sites, sites.size());
    }
}
