package com.uid2.admin.vertx.service;

import com.uid2.admin.auth.AdminAuthMiddleware;
import com.uid2.admin.job.JobDispatcher;
import com.uid2.admin.job.jobsync.EncryptedFilesSyncJob;
import com.uid2.admin.job.jobsync.PrivateSiteDataSyncJob;
import com.uid2.admin.job.jobsync.keyset.ReplaceSharingTypesWithSitesJob;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.auth.Role;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.concurrent.CompletableFuture;

import static com.uid2.admin.vertx.Endpoints.API_PRIVATE_SITES_REFRESH;
import static com.uid2.admin.vertx.Endpoints.API_PRIVATE_SITES_REFRESH_NOW;

public class PrivateSiteDataRefreshService implements IService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PrivateSiteDataRefreshService.class);

    private final AdminAuthMiddleware auth;
    private final JobDispatcher jobDispatcher;
    private final WriteLock writeLock;
    private final JsonObject config;

    public PrivateSiteDataRefreshService(
            AdminAuthMiddleware auth,
            JobDispatcher jobDispatcher,
            WriteLock writeLock,
            JsonObject config) {
        this.auth = auth;
        this.jobDispatcher = jobDispatcher;
        this.writeLock = writeLock;
        this.config = config;
    }

    @Override
    public void setupRoutes(Router router) {
        // this can be called by a scheduled task
        router.post(API_PRIVATE_SITES_REFRESH.toString()).blockingHandler(auth.handle((ctx) -> {
                    synchronized (writeLock) {
                        this.handlePrivateSiteDataGenerate(ctx);
                    }
                },
                //can be other role
            Role.MAINTAINER, Role.PRIVATE_OPERATOR_SYNC));

        router.post(API_PRIVATE_SITES_REFRESH_NOW.toString()).blockingHandler(auth.handle(
                this::handlePrivateSiteDataGenerateNow,
                //can be other role
            Role.PRIVILEGED));
    }

    private void handlePrivateSiteDataGenerate(RoutingContext rc) {
        try {
            ReplaceSharingTypesWithSitesJob replaceSharingTypesWithSitesJob = new ReplaceSharingTypesWithSitesJob(config, writeLock);
            jobDispatcher.enqueue(replaceSharingTypesWithSitesJob);

            PrivateSiteDataSyncJob job = new PrivateSiteDataSyncJob(config, writeLock);
            jobDispatcher.enqueue(job);

            rc.response().end("OK");
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            rc.fail(500, e);
        }
    }

    private void handlePrivateSiteDataGenerateNow(RoutingContext rc) {
        try {
            ReplaceSharingTypesWithSitesJob replaceSharingTypesWithSitesJob = new ReplaceSharingTypesWithSitesJob(config, writeLock);
            jobDispatcher.enqueue(replaceSharingTypesWithSitesJob);
            CompletableFuture<Boolean> replaceSharingTypesWithSitesJobFuture = jobDispatcher.executeNextJob();
            replaceSharingTypesWithSitesJobFuture.get();

            PrivateSiteDataSyncJob privateSiteDataSyncJob = new PrivateSiteDataSyncJob(config, writeLock);
            jobDispatcher.enqueue(privateSiteDataSyncJob);
            CompletableFuture<Boolean> privateSiteDataSyncJobFuture = jobDispatcher.executeNextJob();
            privateSiteDataSyncJobFuture.get();

            CompletableFuture<Boolean> encryptedFileSyncJobFuture = jobDispatcher.executeNextJob();
            encryptedFileSyncJobFuture.get();

            rc.response().end("OK");
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            rc.fail(500, e);
        }
    }
}
