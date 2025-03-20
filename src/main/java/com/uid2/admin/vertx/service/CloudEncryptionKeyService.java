package com.uid2.admin.vertx.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uid2.admin.auth.AdminAuthMiddleware;
import com.uid2.admin.model.CloudEncryptionKeyListResponse;
import com.uid2.admin.model.CloudEncryptionKeySummary;
import com.uid2.admin.vertx.Endpoints;
import com.uid2.shared.auth.Role;
import com.uid2.shared.store.reader.RotatingCloudEncryptionKeyProvider;
import com.uid2.shared.util.Mapper;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CloudEncryptionKeyService implements IService {
    private final AdminAuthMiddleware auth;
    private final RotatingCloudEncryptionKeyProvider keyProvider;
    private static final ObjectMapper OBJECT_MAPPER = Mapper.getInstance();

    public CloudEncryptionKeyService(AdminAuthMiddleware auth, RotatingCloudEncryptionKeyProvider keyProvider) {
        this.auth = auth;
        this.keyProvider = keyProvider;
    }

    @Override
    public void setupRoutes(Router router) {
        router.get(Endpoints.CLOUD_ENCRYPTION_KEY_LIST.toString()).handler(
                auth.handle(this::handleList, Role.MAINTAINER)
        );
    }

    private void handleList(RoutingContext rc) {
        try {
            var keySummaries = keyProvider.getAll()
                    .values()
                    .stream()
                    .map(CloudEncryptionKeySummary::fromFullKey)
                    .toList();
            CloudEncryptionKeyListResponse response = new CloudEncryptionKeyListResponse(keySummaries);
            respondWithJson(rc, response);
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
