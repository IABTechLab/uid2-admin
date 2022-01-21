// Copyright (c) 2021 The Trade Desk, Inc
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice,
//    this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice,
//    this list of conditions and the following disclaimer in the documentation
//    and/or other materials provided with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
// POSSIBILITY OF SUCH DAMAGE.

package com.uid2.admin.vertx.service;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.model.Site;
import com.uid2.admin.store.IStorageManager;
import com.uid2.admin.store.RotatingSiteStore;
import com.uid2.admin.vertx.JsonUtil;
import com.uid2.admin.vertx.RequestUtil;
import com.uid2.admin.vertx.ResponseUtil;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.Const;
import com.uid2.shared.auth.ClientKey;
import com.uid2.shared.auth.Role;
import com.uid2.shared.middleware.AuthMiddleware;
import com.uid2.shared.store.IClientKeyProvider;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.*;
import java.util.stream.Collectors;

public class SiteService implements IService {
    private final AuthMiddleware auth;
    private final WriteLock writeLock;
    private final IStorageManager storageManager;
    private final RotatingSiteStore siteProvider;
    private final IClientKeyProvider clientKeyProvider;
    private final ObjectWriter jsonWriter = JsonUtil.createJsonWriter();

    public SiteService(AuthMiddleware auth,
                       WriteLock writeLock,
                       IStorageManager storageManager,
                       RotatingSiteStore siteProvider,
                       IClientKeyProvider clientKeyProvider) {
        this.auth = auth;
        this.writeLock = writeLock;
        this.storageManager = storageManager;
        this.siteProvider = siteProvider;
        this.clientKeyProvider = clientKeyProvider;
    }

    @Override
    public void setupRoutes(Router router) {
        router.get("/api/site/list").handler(
                auth.handle(this::handleSiteList, Role.CLIENTKEY_ISSUER));
        router.post("/api/site/add").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleSiteAdd(ctx);
            }
        }, Role.CLIENTKEY_ISSUER));
        router.post("/api/site/enable").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleSiteEnable(ctx);
            }
        }, Role.CLIENTKEY_ISSUER));
    }

    private void handleSiteList(RoutingContext rc) {
        try {
            JsonArray ja = new JsonArray();
            final Collection<Site> sites = this.siteProvider.getAllSites().stream()
                    .sorted(Comparator.comparing(Site::getName))
                    .collect(Collectors.toList());
            final Map<Integer, List<ClientKey>> clientKeys = this.clientKeyProvider.getAll().stream()
                    .collect(Collectors.groupingBy(ClientKey::getSiteId));
            final List<ClientKey> emptySiteKeys = new ArrayList<>();
            for (Site site : sites) {
                JsonObject jo = new JsonObject();
                ja.add(jo);

                jo.put("id", site.getId());
                jo.put("name", site.getName());
                jo.put("enabled", site.isEnabled());

                List<ClientKey> clients = clientKeys.getOrDefault(site.getId(), emptySiteKeys);
                JsonArray jr = new JsonArray();
                clients.stream()
                        .map(c -> c.getRoles()).flatMap(Set::stream).collect(Collectors.toSet())
                        .forEach(r -> jr.add(r));

                jo.put("roles", jr);
                jo.put("client_count", clients.size());
            }

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(ja.encode());
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleSiteAdd(RoutingContext rc) {
        try {
            // refresh manually
            siteProvider.loadContent();

            final String name = rc.queryParam("name").isEmpty() ? "" : rc.queryParam("name").get(0).trim();
            if (name == null || name.isEmpty()) {
                ResponseUtil.error(rc, 400, "must specify a valid site name");
                return;
            }

            Optional<Site> existingSite = this.siteProvider.getAllSites()
                    .stream().filter(c -> c.getName().equals(name))
                    .findFirst();
            if (existingSite.isPresent()) {
                ResponseUtil.error(rc, 400, "site existed");
                return;
            }

            boolean enabled = false;
            List<String> enabledFlags = rc.queryParam("enabled");
            if (!enabledFlags.isEmpty()) {
                try {
                    enabled = Boolean.valueOf(enabledFlags.get(0));
                } catch (Exception ex) {
                    ResponseUtil.error(rc, 400, "unable to parse enabled " + ex.getMessage());
                    return;
                }
            }

            final List<Site> sites = this.siteProvider.getAllSites()
                    .stream().sorted(Comparator.comparingInt(Site::getId))
                    .collect(Collectors.toList());
            final int siteId = 1 + sites.stream().mapToInt(Site::getId).max().orElse(Const.Data.AdvertisingTokenSiteId);
            final Site newSite = new Site(siteId, name, enabled);

            // add site to the array
            sites.add(newSite);

            // upload to storage
            storageManager.uploadSites(siteProvider, sites);

            // respond with new client created
            rc.response().end(jsonWriter.writeValueAsString(newSite));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleSiteEnable(RoutingContext rc) {
        try {
            // refresh manually
            siteProvider.loadContent();

            final Site existingSite = RequestUtil.getSite(rc, "id", siteProvider);
            if (existingSite == null) {
                return;
            }

            Boolean enabled = false;
            List<String> enabledFlags = rc.queryParam("enabled");
            if (!enabledFlags.isEmpty()) {
                try {
                    enabled = Boolean.valueOf(enabledFlags.get(0));
                } catch (Exception ex) {
                    ResponseUtil.error(rc, 400, "unable to parse enabled " + ex.getMessage());
                    return;
                }
            }

            final List<Site> sites = this.siteProvider.getAllSites()
                    .stream().sorted(Comparator.comparingInt(Site::getId))
                    .collect(Collectors.toList());

            if (existingSite.isEnabled() != enabled) {
                existingSite.setEnabled(enabled);
                storageManager.uploadSites(siteProvider, sites);
            }

            rc.response().end(jsonWriter.writeValueAsString(existingSite));
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }
}
