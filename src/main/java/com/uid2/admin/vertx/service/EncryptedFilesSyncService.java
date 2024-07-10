package com.uid2.admin.vertx.service;

import com.uid2.admin.auth.AdminAuthMiddleware;
import com.uid2.admin.job.JobDispatcher;
import com.uid2.admin.job.jobsync.EncryptedFilesSyncJob;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.auth.Role;
import com.uid2.shared.store.reader.RotatingS3KeyProvider;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.concurrent.CompletableFuture;

public class EncryptedFilesSyncService implements IService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EncryptedFilesSyncService.class);

    private final AdminAuthMiddleware auth;
    private final JobDispatcher jobDispatcher;
    private final WriteLock writeLock;
    private final JsonObject config;
    private final RotatingS3KeyProvider s3KeyProvider;

    public EncryptedFilesSyncService(
            AdminAuthMiddleware auth,
            JobDispatcher jobDispatcher,
            WriteLock writeLock,
            JsonObject config,
            RotatingS3KeyProvider s3KeyProvider) {
        this.auth = auth;
        this.jobDispatcher = jobDispatcher;
        this.writeLock = writeLock;
        this.config = config;
        this.s3KeyProvider =s3KeyProvider;
    }

    @Override
    public void setupRoutes(Router router) {
        router.post("/api/encrypted-files/sync").blockingHandler(auth.handle((ctx) -> {
                    synchronized (writeLock) {
                        this.handleEncryptedFileSync(ctx);
                    }
                },
                //????
                Role.MAINTAINER, Role.PRIVATE_OPERATOR_SYNC));

        router.post("/api/encrypted-files/syncNow").blockingHandler(auth.handle(
                this::handleEncryptedFileSyncNow,
                Role.PRIVILEGED));
    }

    private void handleEncryptedFileSync(RoutingContext rc) {
        try {
            EncryptedFilesSyncJob encryptedFileSyncJob = new EncryptedFilesSyncJob(config, writeLock, s3KeyProvider);
            jobDispatcher.enqueue(encryptedFileSyncJob);

            rc.response().end("OK");
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }
    private void handleEncryptedFileSyncNow(RoutingContext rc) {
        try {
            EncryptedFilesSyncJob encryptedFileSyncJob = new EncryptedFilesSyncJob(config, writeLock,s3KeyProvider);
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
