package com.uid2.admin.vertx.api.cstg;

import com.google.common.collect.Streams;
import com.google.inject.Inject;
import com.uid2.admin.secret.IKeypairManager;
import com.uid2.admin.vertx.ResponseUtil;
import com.uid2.admin.vertx.api.IRouteProvider;
import com.uid2.admin.vertx.api.UrlParameterProviders;
import com.uid2.admin.vertx.api.annotations.ApiMethod;
import com.uid2.admin.vertx.api.annotations.Method;
import com.uid2.admin.vertx.api.annotations.Path;
import com.uid2.admin.vertx.api.annotations.Roles;
import com.uid2.admin.vertx.service.ClientSideKeypairService;
import com.uid2.shared.auth.Role;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import lombok.val;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetClientSideKeypairsBySite implements IRouteProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(GetClientSideKeypairsBySite.class);

    private final IKeypairManager keypairManager;


    @Inject
    public GetClientSideKeypairsBySite(IKeypairManager keypairManager) {
        this.keypairManager = keypairManager;
    }

    @Path("/sites/:siteId/client-side-keypairs")
    @Method(ApiMethod.GET)
    @Roles({Role.ADMINISTRATOR, Role.SHARING_PORTAL})
    public Handler<RoutingContext> getHandler() {
        return UrlParameterProviders.provideSiteId(this::handleGetClientSideKeys);
    }

    public void handleGetClientSideKeys(RoutingContext rc, int siteId) {
        val keypairs = keypairManager.getKeypairsBySite(siteId);
        if (keypairs != null) {
            val result = Streams.stream(keypairs)
                    .map(kp -> ClientSideKeypairResponse.fromClientSiteKeypair(kp))
                    .toArray(ClientSideKeypairResponse[]::new);
            rc.json(result);
        }
        else {
            ResponseUtil.error(rc, 404, "No keypairs available for site ID: " + siteId);
        }
    }
}