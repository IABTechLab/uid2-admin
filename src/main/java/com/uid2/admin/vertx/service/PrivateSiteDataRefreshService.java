package com.uid2.admin.vertx.service;

import com.uid2.admin.auth.AdminAuthMiddleware;
import com.uid2.admin.job.JobDispatcher;
import com.uid2.admin.job.jobsync.EncryptedFilesSyncJob;
import com.uid2.admin.job.jobsync.PrivateSiteDataSyncJob;
import com.uid2.admin.job.jobsync.keyset.ReplaceSharingTypesWithSitesJob;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.auth.Role;
import com.uid2.shared.store.reader.RotatingCloudEncryptionKeyProvider;
import com.uid2.shared.store.reader.RotatingCloudEncryptionKeyProvider;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.concurrent.CompletableFuture;

public class PrivateSiteDataRefreshService implements IService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PrivateSiteDataRefreshService.class);

    private final AdminAuthMiddleware auth;
    private final JobDispatcher jobDispatcher;
    private final WriteLock writeLock;
    private final JsonObject config;
    private final RotatingCloudEncryptionKeyProvider RotatingCloudEncryptionKeyProvider;

    public PrivateSiteDataRefreshService(
            AdminAuthMiddleware auth,
            JobDispatcher jobDispatcher,
            WriteLock writeLock,
            JsonObject config,
            RotatingCloudEncryptionKeyProvider RotatingCloudEncryptionKeyProvider) {
        this.auth = auth;
        this.jobDispatcher = jobDispatcher;
        this.writeLock = writeLock;
        this.config = config;
        this.RotatingCloudEncryptionKeyProvider = RotatingCloudEncryptionKeyProvider;
    }

    @Override
    public void setupRoutes(Router router) {
        // this can be called by a scheduled task
        router.post("/api/private-sites/refresh").blockingHandler(auth.handle((ctx) -> {
                    synchronized (writeLock) {
                        this.handlePrivateSiteDataGenerate(ctx);
                    }
                },
                //can be other role
            Role.MAINTAINER, Role.PRIVATE_OPERATOR_SYNC));

        router.post("/api/private-sites/refreshNow").blockingHandler(auth.handle(
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

            EncryptedFilesSyncJob encryptedFileSyncJob = new EncryptedFilesSyncJob(config, writeLock, RotatingCloudEncryptionKeyProvider);
            jobDispatcher.enqueue(encryptedFileSyncJob);

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

            EncryptedFilesSyncJob encryptedFileSyncJob = new EncryptedFilesSyncJob(config, writeLock, RotatingCloudEncryptionKeyProvider);
            jobDispatcher.enqueue(encryptedFileSyncJob);
            CompletableFuture<Boolean> encryptedFileSyncJobFuture = jobDispatcher.executeNextJob();
            encryptedFileSyncJobFuture.get();

            rc.response().end("OK");
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            rc.fail(500, e);
        }
    }
}
