package com.uid2.admin.store.parser;

import com.uid2.admin.auth.AdminKeyset;
import com.uid2.admin.auth.AdminKeysetSnapshot;
import com.uid2.shared.model.ClientType;
import com.uid2.shared.store.parser.Parser;
import com.uid2.shared.store.parser.ParsingResult;
import com.uid2.shared.Utils;
import com.uid2.shared.auth.KeysetSnapshot;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;

public class AdminKeysetParser implements Parser<AdminKeysetSnapshot> {
    @Override
    public ParsingResult<AdminKeysetSnapshot> deserialize(InputStream inputStream) throws IOException {
        final JsonArray adminKeysetsSpec = Utils.toJsonArray(inputStream);
        final HashMap<Integer, AdminKeyset> keysetIdToAdminKeyset = new HashMap<>();
        for(int i = 0; i < adminKeysetsSpec.size(); i++) {
            final JsonObject adminKeysetSpec = adminKeysetsSpec.getJsonObject(i);
            final JsonObject keysetSpec = adminKeysetSpec.getJsonObject("keyset");
            final Integer keysetId = keysetSpec.getInteger("keyset_id");
            final Integer siteId = keysetSpec.getInteger("site_id");
            final String name = keysetSpec.getString("name");

            final JsonArray allowedSitesSpec = keysetSpec.getJsonArray("allowed_sites");
            HashSet<Integer> allowedSites = new HashSet<>();
            if (allowedSitesSpec == null) {
                allowedSites = null;
            } else {
                for (int j = 0; j < allowedSitesSpec.size(); j++) {
                    allowedSites.add(allowedSitesSpec.getInteger(j));
                }
            }

            final JsonArray allowedTypeSpec = adminKeysetSpec.getJsonArray("allowed_types");

            HashSet<ClientType> allowedTypes = new HashSet<>();
            for (int j = 0; j < allowedTypeSpec.size(); ++j) {
                String value = allowedTypeSpec.getString(j).toUpperCase();
                allowedTypes.add(Enum.valueOf(ClientType.class, value));
            }

            long created = keysetSpec.getLong("created");
            final boolean enabled = keysetSpec.getBoolean("enabled");
            final boolean isDefault = keysetSpec.getBoolean("default");

            keysetIdToAdminKeyset.put(keysetId, new AdminKeyset(keysetId, siteId, name, allowedSites, created, enabled, isDefault, allowedTypes));
        }
        return new ParsingResult<>(new AdminKeysetSnapshot(keysetIdToAdminKeyset), adminKeysetsSpec.size());
    }
}
