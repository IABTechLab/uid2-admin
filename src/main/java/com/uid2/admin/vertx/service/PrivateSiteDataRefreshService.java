package com.uid2.admin.vertx.service;

import com.uid2.admin.job.JobDispatcher;
import com.uid2.admin.job.jobsync.PrivateSiteDataSyncJob;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.auth.Role;
import com.uid2.shared.middleware.AuthMiddleware;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class PrivateSiteDataRefreshService implements IService {
    private final AuthMiddleware auth;
    private final WriteLock writeLock;
    private final JsonObject config;

    public PrivateSiteDataRefreshService(
            AuthMiddleware auth,
            WriteLock writeLock,
            JsonObject config) {
        this.auth = auth;
        this.writeLock = writeLock;
        this.config = config;
    }

    @Override
    public void setupRoutes(Router router) {
        // this can be called by a k8s cronjob setup such as
        // https://gitlab.adsrvr.org/uid2/k8-deployment/-/tree/master/uid2
        router.post("/api/private-sites/refresh").blockingHandler(auth.handle((ctx) -> {
                    synchronized (writeLock) {
                        this.handlePrivateSiteDataGenerate(ctx);
                    }
                },
                //can be other role
                Role.ADMINISTRATOR));

        router.post("/api/private-sites/refreshNow").blockingHandler(auth.handle((ctx) -> {
                    synchronized (writeLock) {
                        this.handlePrivateSiteDataGenerateNow(ctx);
                    }
                },
                //can be other role
                Role.ADMINISTRATOR));
    }

    private void handlePrivateSiteDataGenerate(RoutingContext rc) {
        try {
            PrivateSiteDataSyncJob job = new PrivateSiteDataSyncJob(config, writeLock);
            JobDispatcher.getInstance().enqueue(job);
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handlePrivateSiteDataGenerateNow(RoutingContext rc) {
        try {
            PrivateSiteDataSyncJob job = new PrivateSiteDataSyncJob(config, writeLock);
            JobDispatcher.getInstance().enqueue(job);
            JobDispatcher.getInstance().executeNextJob(3);
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }
}
