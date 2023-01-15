package com.uid2.admin.vertx.service;

import com.uid2.admin.job.JobDispatcher;
import com.uid2.admin.job.jobsync.OverallSyncJob;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.auth.Role;
import com.uid2.shared.middleware.AuthMiddleware;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class PrivateSiteDataRefreshService implements IService {
    private final AuthMiddleware auth;
    private final WriteLock writeLock;

    public PrivateSiteDataRefreshService(AuthMiddleware auth,
                                            WriteLock writeLock) {
        this.auth = auth;
        this.writeLock = writeLock;
    }

    @Override
    public void setupRoutes(Router router) {
        router.post("/api/private_sites/refresh").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handlePrivateSiteDataGenerate(ctx);
            }
        },
        //can be other role
        Role.ADMINISTRATOR));
    }

    private void handlePrivateSiteDataGenerate(RoutingContext rc) {
        try {
            OverallSyncJob job = new OverallSyncJob();
            JobDispatcher.getInstance().enqueue(job);
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }
}
