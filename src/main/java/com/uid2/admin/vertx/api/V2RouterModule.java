package com.uid2.admin.vertx.api;

import com.uid2.admin.secret.IKeypairManager;
import com.uid2.admin.vertx.api.cstg.GetClientSideKeypairsBySite;
import com.uid2.shared.middleware.AuthMiddleware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class V2RouterModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(V2RouterModule.class);

    private final IKeypairManager keypairManager;
    private final AuthMiddleware authMiddleware;

    public V2RouterModule(IKeypairManager keypairManager, AuthMiddleware authMiddleware) {
        this.keypairManager = keypairManager;
        this.authMiddleware = authMiddleware;
    }

    protected IRouteProvider[] getRouteProviders() {
        return new IRouteProvider[] {
                new GetClientSideKeypairsBySite(keypairManager)
        };
    }

    public V2Router getRouter() {
        return new V2Router(getRouteProviders(), authMiddleware);
    }
}
