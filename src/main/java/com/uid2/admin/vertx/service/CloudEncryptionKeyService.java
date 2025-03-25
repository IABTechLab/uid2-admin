package com.uid2.admin.vertx.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uid2.admin.auth.AdminAuthMiddleware;
import com.uid2.admin.cloudEncryption.CloudKeyRotationStrategy;
import com.uid2.admin.model.CloudEncryptionKeyListResponse;
import com.uid2.admin.model.CloudEncryptionKeySummary;
import com.uid2.admin.store.writer.CloudEncryptionKeyStoreWriter;
import com.uid2.admin.vertx.Endpoints;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.CloudEncryptionKey;
import com.uid2.shared.store.reader.RotatingCloudEncryptionKeyProvider;
import com.uid2.shared.store.reader.RotatingSiteStore;
import com.uid2.shared.util.Mapper;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CloudEncryptionKeyService implements IService {
    private final AdminAuthMiddleware auth;
    private final RotatingCloudEncryptionKeyProvider keyProvider;
    private final RotatingSiteStore siteProvider;
    private final CloudEncryptionKeyStoreWriter keyWriter;
    private final CloudKeyRotationStrategy rotationStrategy;
    private static final ObjectMapper OBJECT_MAPPER = Mapper.getInstance();

    public CloudEncryptionKeyService(
            AdminAuthMiddleware auth,
            RotatingCloudEncryptionKeyProvider keyProvider,
            CloudEncryptionKeyStoreWriter keyWriter,
            RotatingSiteStore siteProvider,
            CloudKeyRotationStrategy rotationStrategy
    ) {
        this.auth = auth;
        this.keyProvider = keyProvider;
        this.siteProvider = siteProvider;
        this.keyWriter = keyWriter;
        this.rotationStrategy = rotationStrategy;
    }

    @Override
    public void setupRoutes(Router router) {
        router.get(Endpoints.CLOUD_ENCRYPTION_KEY_LIST.toString()).handler(
                auth.handle(this::handleList, Role.MAINTAINER)
        );

        router.post(Endpoints.CLOUD_ENCRYPTION_KEY_ROTATE.toString()).handler(
                auth.handle(this::handleRotate, Role.MAINTAINER)
        );
    }

    private void handleRotate(RoutingContext rc) {
        try {
            keyProvider.loadContent();
            siteProvider.loadContent();
            var allSites = siteProvider.getAllSites();
            var existingKeys = keyProvider.getAll().values();

            var desiredKeys = rotationStrategy.computeDesiredKeys(existingKeys, allSites);
            writeKeys(desiredKeys);

            rc.response().end();
        } catch (Exception e) {
            rc.fail(500, e);
        }
    }

    private void writeKeys(Set<CloudEncryptionKey> desiredKeys) throws Exception {
        var keysForWriting = desiredKeys.stream().collect(Collectors.toMap(
                CloudEncryptionKey::getId,
                Function.identity())
        );
        keyWriter.upload(keysForWriting, null);
    }

    private void handleList(RoutingContext rc) {
        try {
            keyProvider.loadContent();

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
