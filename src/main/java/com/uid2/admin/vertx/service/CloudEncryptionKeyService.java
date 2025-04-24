package com.uid2.admin.vertx.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uid2.admin.auth.AdminAuthMiddleware;
import com.uid2.admin.cloudencryption.CloudEncryptionKeyManager;
import com.uid2.admin.cloudencryption.CloudEncryptionKeyRotationJob;
import com.uid2.admin.job.JobDispatcher;
import com.uid2.admin.model.CloudEncryptionKeyListResponse;
import com.uid2.admin.vertx.Endpoints;
import com.uid2.shared.auth.Role;
import com.uid2.shared.util.Mapper;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class CloudEncryptionKeyService implements IService {
    private final AdminAuthMiddleware auth;
    private final CloudEncryptionKeyManager keyManager;
    private final JobDispatcher jobDispatcher;
    private static final ObjectMapper OBJECT_MAPPER = Mapper.getInstance();

    public CloudEncryptionKeyService(
            AdminAuthMiddleware auth,
            CloudEncryptionKeyManager keyManager,
            JobDispatcher jobDispatcher) {
        this.auth = auth;
        this.keyManager = keyManager;
        this.jobDispatcher = jobDispatcher;
    }

    @Override
    public void setupRoutes(Router router) {
        router.get(Endpoints.CLOUD_ENCRYPTION_KEY_METADATA.toString()).handler(
                auth.handle(this::handleMetadata, Role.MAINTAINER));

        router.get(Endpoints.CLOUD_ENCRYPTION_KEY_LIST.toString()).handler(
                auth.handle(this::handleList, Role.MAINTAINER)
        );

        router.post(Endpoints.CLOUD_ENCRYPTION_KEY_ROTATE.toString()).handler(
                auth.handle(this::handleRotate, Role.MAINTAINER, Role.SECRET_ROTATION)
        );
    }

    private void handleMetadata(RoutingContext rc) {
        try {
            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(keyManager.getMetadata().encode());
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleList(RoutingContext rc) {
        try {
            var response = new CloudEncryptionKeyListResponse(keyManager.getKeySummaries());
            respondWithJson(rc, response);
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void handleRotate(RoutingContext rc) {
        try {
            var shouldFail = !rc.queryParam("fail").isEmpty();

            jobDispatcher.enqueue(new CloudEncryptionKeyRotationJob(keyManager, shouldFail));
            var isSuccess = jobDispatcher.executeNextJob().get();
            if (isSuccess) {
                rc.response().end();
            } else {
                rc.response()
                        .setStatusCode(500)
                        .putHeader("Content-Type", "text/plain")
                        .end("Failed to rotate cloud encryption keys");
            }
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private static void respondWithJson(RoutingContext rc, CloudEncryptionKeyListResponse response) throws JsonProcessingException {
        rc.response()
                .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end(OBJECT_MAPPER.writeValueAsString(response));
    }
}
