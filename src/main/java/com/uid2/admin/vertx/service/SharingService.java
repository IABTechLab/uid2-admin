package com.uid2.admin.vertx.service;

import com.uid2.admin.auth.AdminKeyset;
import com.uid2.admin.secret.IKeysetKeyManager;
import com.uid2.admin.store.reader.RotatingAdminKeysetStore;
import com.uid2.admin.store.writer.AdminKeysetWriter;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.admin.managers.KeysetManager;
import com.uid2.admin.store.writer.KeysetStoreWriter;
import com.uid2.admin.vertx.ResponseUtil;
import com.uid2.shared.Const;
import com.uid2.shared.auth.Role;
import com.uid2.shared.middleware.AuthMiddleware;
import com.uid2.shared.model.ClientType;
import com.uid2.shared.model.SiteUtil;
import com.uid2.shared.store.reader.RotatingSiteStore;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class SharingService implements IService {
    private final AuthMiddleware auth;

    private final WriteLock writeLock;
    private final RotatingAdminKeysetStore keysetProvider;
    private final RotatingSiteStore siteProvider;
    private final KeysetManager keysetManager;
    private static final Logger LOGGER = LoggerFactory.getLogger(SharingService.class);

    private final boolean enableKeysets;

    public SharingService(AuthMiddleware auth,
                          WriteLock writeLock,
                          RotatingAdminKeysetStore keysetProvider,
                          KeysetManager keysetManager,
                          RotatingSiteStore siteProvider,
                          boolean enableKeyset) {
        this.auth = auth;
        this.writeLock = writeLock;
        this.keysetProvider = keysetProvider;
        this.keysetManager = keysetManager;
        this.siteProvider = siteProvider;
        this.enableKeysets = enableKeyset;
    }

    @Override
    public void setupRoutes(Router router) {
        if(!enableKeysets) return;
        router.get("/api/sharing/lists").handler(
                auth.handle(this::handleListAllAllowedSites, Role.SHARING_PORTAL)
        );
        router.get("/api/sharing/list/:siteId").handler(
                auth.handle(this::handleListAllowedSites, Role.SHARING_PORTAL)
        );
        router.post("/api/sharing/list/:siteId").handler(
                auth.handle(this::handleSetAllowedSites, Role.SHARING_PORTAL)
        );

        router.get("/api/sharing/keysets").handler(
                auth.handle(this::handleListAllKeysets, Role.ADMINISTRATOR)
        );
        router.post("/api/sharing/keyset").handler(
                auth.handle(this::handleSetKeyset, Role.ADMINISTRATOR)
        );
        router.get("/api/sharing/keyset/:keyset_id").handler(
                auth.handle(this::handleListKeyset, Role.ADMINISTRATOR)
        );
    }

    private void handleSetKeyset(RoutingContext rc) {
        synchronized (writeLock) {
            try {
                keysetProvider.loadContent();
                siteProvider.loadContent();
            } catch (Exception e) {
                ResponseUtil.errorInternal(rc, "Failed to load keysets", e);
                return;
            }

            final Map<Integer, AdminKeyset> keysetsById = this.keysetProvider.getSnapshot().getAllKeysets();

            final JsonObject body = rc.body().asJsonObject();

            final JsonArray allowedSites = body.getJsonArray("allowed_sites");
            final JsonArray allowedTypes = body.getJsonArray("allowed_types");
            final Integer requestKeysetId = body.getInteger("keyset_id");
            final Integer requestSiteId = body.getInteger("site_id");
            final String requestName = body.getString("name", "");

            if ((requestKeysetId == null && requestSiteId == null)
                    || (requestKeysetId != null && requestSiteId != null)) {
                ResponseUtil.error(rc, 400, "You must specify exactly one of: keyset_id, site_id");
                return;
            }

            final int siteId;
            final Integer keysetId;
            final String name;

            if(requestSiteId != null) {
                siteId = requestSiteId;
                name = requestName;
                // Check if the site id is valid
                if (!isSiteIdEditable(siteId))  {
                    ResponseUtil.error(rc, 400, "Site id " + siteId + " not valid");
                    return;
                }

                // Trying to add a keyset for a site that already has one
                if (keysetsById.values().stream().anyMatch(k -> k.getSiteId() == siteId)) {
                    ResponseUtil.error(rc, 400, "Keyset already exists for site: " + siteId);
                    return;
                }
                keysetId = null;
                if (keysetsById.values().stream().anyMatch(item -> // for multiple keysets. See commented out SharingServiceTest#KeysetSetNewIdenticalNameAndSiteId
                        item.getSiteId() == siteId && item.getName().equalsIgnoreCase(name))) {
                    ResponseUtil.error(rc, 400, "Keyset with same site_id and name already exists");
                    return;
                }
            } else {
                AdminKeyset keyset = keysetsById.get(requestKeysetId);
                if (keyset == null) {
                    ResponseUtil.error(rc, 404, "Could not find keyset for keyset_id: " + requestKeysetId);
                    return;
                }
                keysetId = requestKeysetId;
                if(isSpecialKeyset(keysetId)) {
                    ResponseUtil.error(rc, 400, "Keyset id: " + keysetId + " is not valid");
                    return;
                }
                siteId = keyset.getSiteId();
                name = requestName.equals("") ? keyset.getName() : requestName;
            }

            try {
                AdminKeyset newKeyset = setAdminKeyset(rc, allowedSites, allowedTypes,  siteId,  keysetId, name);
                if(newKeyset == null) return;
                JsonObject jo = jsonFullKeyset(newKeyset);
                rc.response()
                        .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .end(jo.encode());
            } catch (Exception e) {
                rc.fail(500, e);
            }
        }
    }

    // Returns if a keyset is one of the reserved ones
    private static boolean isSpecialKeyset(int keysetId) {
        return keysetId == Const.Data.MasterKeysetId || keysetId == Const.Data.RefreshKeysetId
                || keysetId == Const.Data.FallbackPublisherKeysetId;
    }

    // Returns if a site ID is not a special site and it does exist
    private boolean isSiteIdEditable(int siteId) {
        return SiteUtil.isValidSiteId(siteId) && siteProvider.getSite(siteId) != null;
    }


    private void handleListKeyset(RoutingContext rc) {
        int keysetId;
        try {
            keysetId = Integer.parseInt(rc.pathParam("keyset_id"));
        } catch (Exception e) {
            LOGGER.warn("Failed to parse a keyset_id from list request", e);
            rc.fail(400, e);
            return;
        }

        AdminKeyset keyset = this.keysetProvider.getSnapshot().getAllKeysets().get(keysetId);

        if (keyset == null) {
            ResponseUtil.error(rc, 404, "Failed to find keyset for keyset_id: " + keysetId);
            return;
        }

        JsonObject jo = jsonFullKeyset(keyset);
        rc.response()
                .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end(jo.encode());
    }

    private AdminKeyset getDefaultKeyset(Map<Integer, AdminKeyset> keysets, Integer siteId) {
           for(AdminKeyset keyset: keysets.values()) {
               if(keyset.getSiteId() == siteId && keyset.isDefault()) {
                   return keyset;
               }
           }
           return null;
    }

    private void handleListAllKeysets(RoutingContext rc) {
        try {
            JsonArray ja = new JsonArray();
            Map<Integer, AdminKeyset> collection = this.keysetProvider.getSnapshot().getAllKeysets();
            for (Map.Entry<Integer, AdminKeyset> keyset : collection.entrySet()) {
                JsonObject jo = jsonFullKeyset(keyset.getValue());
                ja.add(jo);
            }

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(ja.encode());
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleListAllowedSites(RoutingContext rc) {
        int siteId;
        try {
            siteId = Integer.parseInt(rc.pathParam("siteId"));
        } catch (Exception e) {
            LOGGER.warn("Failed to parse a site id from list request", e);
            rc.fail(400, e);
            return;
        }

        AdminKeyset keyset = getDefaultKeyset(this.keysetProvider.getSnapshot().getAllKeysets(), siteId);

        if (keyset == null) {
            LOGGER.warn("Failed to find keyset for site id: " + siteId);
            rc.fail(404);
            return;
        }

        JsonObject jo = new JsonObject();
        Set<Integer> allowedSites = keyset.getAllowedSites();
        jo.put("allowed_sites", allowedSites != null ? allowedSites.stream().sorted().toArray() : null);
        jo.put("allowed_types", keyset.getAllowedTypes());
        jo.put("hash", keyset.hashCode());

        rc.response()
                .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end(jo.encode());
    }

    private void handleListAllAllowedSites(RoutingContext rc) {
        try {
            JsonArray ja = new JsonArray();
            Map<Integer, AdminKeyset> collection = this.keysetProvider.getSnapshot().getAllKeysets();
            for (Map.Entry<Integer, AdminKeyset> keyset : collection.entrySet()) {
                JsonObject jo = new JsonObject();
                jo.put("keyset_id", keyset.getValue().getKeysetId());
                jo.put("site_id", keyset.getValue().getSiteId());
                Set<Integer> allowedSites = keyset.getValue().getAllowedSites();
                jo.put("allowed_sites", allowedSites != null ? allowedSites.stream().sorted().toArray() : null);
                jo.put("allowed_types", keyset.getValue().getAllowedTypes());
                jo.put("hash", keyset.getValue().hashCode());
                ja.add(jo);
            }

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(ja.encode());
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleSetAllowedSites(RoutingContext rc) {
        synchronized (writeLock) {
            int siteId;
            try {
                siteId = Integer.parseInt(rc.pathParam("siteId"));
            } catch (Exception e) {
                LOGGER.warn("Failed to parse a site id from list request", e);
                rc.fail(400, e);
                return;
            }

            if (!isSiteIdEditable(siteId))  {
                ResponseUtil.error(rc, 400, "Site id " + siteId + " not valid");
                return;
            }

            try {
                keysetProvider.loadContent();
            } catch (Exception e) {
                ResponseUtil.errorInternal(rc, "Failed to load keysets", e);
                return;
            }


           final Map<Integer, AdminKeyset> keysetsById = this.keysetProvider.getSnapshot().getAllKeysets();
           AdminKeyset keyset = getDefaultKeyset(keysetsById, siteId);

           final JsonObject body = rc.body().asJsonObject();
           final JsonArray allowedSites = body.getJsonArray("allowed_sites");
           final JsonArray allowedTypes = body.getJsonArray("allowed_types");
           final int hash = body.getInteger("hash");

            if (keyset != null && hash != keyset.hashCode()) {
                rc.fail(409);
                return;
            }

            Integer keysetId = null;
            String name;

            if (keyset == null) {
                name = "";
            } else {
                keysetId = keyset.getKeysetId();
                name = keyset.getName();
            }

            try {
                AdminKeyset newKeyset = setAdminKeyset(rc, allowedSites, allowedTypes,  siteId,  keysetId, name);
                if(newKeyset == null) return;
                JsonObject jo = new JsonObject();
                jo.put("allowed_sites", allowedSites);
                jo.put("allowed_types", newKeyset.getAllowedTypes());
                jo.put("hash", newKeyset.hashCode());

                rc.response()
                        .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .end(jo.encode());
            } catch (Exception e) {
                rc.fail(500, e);
            }
        }
    }

    private AdminKeyset setAdminKeyset(RoutingContext rc, JsonArray allowedSites, JsonArray allowedTypes,
                                       Integer siteId, Integer keysetId, String name)
            throws Exception{
        Set<Integer> existingSites = new HashSet<>();

        if (keysetId == null) {
            keysetId = this.keysetManager.getNextKeysetId();
            name = "";
        } else {
            existingSites = this.keysetProvider.getSnapshot().getAllKeysets().get(keysetId).getAllowedSites();
        }

        final Set<Integer> newlist;

        if (allowedSites != null){
            final Set<Integer> existingAllowedSites = existingSites;
            OptionalInt firstInvalidSite = allowedSites.stream()
                    .mapToInt(s -> (Integer) s).filter(s -> !existingAllowedSites.contains(s) && !isSiteIdEditable(s)).findFirst();
            if (firstInvalidSite.isPresent()) {
                ResponseUtil.error(rc, 400, "Site id " + firstInvalidSite.getAsInt() + " not valid");
                return null;
            }

            boolean containsDuplicates = allowedSites.stream().distinct().count() < allowedSites.stream().count();
            if (containsDuplicates) {
                ResponseUtil.error(rc, 400, "Duplicate site_ids not permitted");
                return null;
            }

            newlist = allowedSites.stream()
                    .mapToInt(s -> (Integer) s)
                    .filter(s -> !Objects.equals(s, siteId))
                    .boxed()
                    .collect(Collectors.toSet());
        } else {
            newlist = null;
        }

        Set<ClientType> newAllowedTypes = null;
        if(allowedTypes == null || allowedTypes.isEmpty()) {
            newAllowedTypes = new HashSet<>();
        } else {
            try {
                newAllowedTypes = allowedTypes.stream()
                        .map(s -> Enum.valueOf(ClientType.class, s.toString()))
                        .collect(Collectors.toSet());
            } catch (Exception e) {
                ResponseUtil.error(rc, 400, "Invalid Client Type");
                return null;
            }
        }

        final AdminKeyset newKeyset = new AdminKeyset(keysetId, siteId, name,
                newlist, Instant.now().getEpochSecond(), true, true, newAllowedTypes);

        this.keysetManager.addOrReplaceKeyset(newKeyset);
        return newKeyset;
    }

    private JsonObject jsonFullKeyset(AdminKeyset keyset) {
        JsonObject jo = new JsonObject();
        jo.put("keyset_id", keyset.getKeysetId());
        jo.put("site_id", keyset.getSiteId());
        jo.put("name", keyset.getName());
        jo.put("allowed_sites", keyset.getAllowedSites());
        jo.put("allowed_types", keyset.getAllowedTypes());
        jo.put("created", keyset.getCreated());
        jo.put("is_enabled", keyset.isEnabled());
        jo.put("is_default", keyset.isDefault());
        return jo;
    }
}
