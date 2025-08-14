package com.uid2.admin.vertx.service;

import com.uid2.admin.auth.AdminAuthMiddleware;
import com.uid2.admin.salt.SaltRotation;
import com.uid2.admin.salt.TargetDate;
import com.uid2.admin.store.writer.SaltStoreWriter;
import com.uid2.admin.vertx.RequestUtil;
import com.uid2.admin.vertx.ResponseUtil;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.audit.AuditParams;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.SaltEntry;
import com.uid2.shared.store.salt.RotatingSaltProvider;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static com.uid2.admin.vertx.Endpoints.*;

public class SaltService implements IService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SaltService.class);
    private static final Duration[] SALT_ROTATION_AGE_THRESHOLDS = new Duration[]{
            Duration.ofDays(30),
            Duration.ofDays(60),
            Duration.ofDays(90),
            Duration.ofDays(120),
            Duration.ofDays(150),
            Duration.ofDays(180),
            Duration.ofDays(210),
            Duration.ofDays(240),
            Duration.ofDays(270),
            Duration.ofDays(300),
            Duration.ofDays(330),
            Duration.ofDays(360),
            Duration.ofDays(390)
    };

    private final AdminAuthMiddleware auth;
    private final WriteLock writeLock;
    private final SaltStoreWriter storageManager;
    private final RotatingSaltProvider saltProvider;
    private final SaltRotation saltRotation;


    public SaltService(AdminAuthMiddleware auth,
                       WriteLock writeLock,
                       SaltStoreWriter storageManager,
                       RotatingSaltProvider saltProvider,
                       SaltRotation saltRotation) {
        this.auth = auth;
        this.writeLock = writeLock;
        this.storageManager = storageManager;
        this.saltProvider = saltProvider;
        this.saltRotation = saltRotation;
    }

    @Override
    public void setupRoutes(Router router) {
        router.get(API_SALT_SNAPSHOTS.toString()).handler(
                auth.handle(this::handleSaltSnapshots, Role.MAINTAINER));

//        router.post(API_SALT_REBUILD.toString()).blockingHandler(auth.handle(ctx -> {
//            synchronized (writeLock) {
//                this.handleSaltRebuild(ctx);
//            }
//        }, new AuditParams(List.of(), Collections.emptyList()), Role.MAINTAINER));
//
//        router.post(API_SALT_ROTATE.toString()).blockingHandler(auth.handle((ctx) -> {
//            synchronized (writeLock) {
//                this.handleSaltRotate(ctx);
//            }
//        }, new AuditParams(List.of("fraction", "min_ages_in_seconds", "target_date"), Collections.emptyList()), Role.SUPER_USER, Role.SECRET_ROTATION));
    }

    private void handleSaltSnapshots(RoutingContext rc) {
        try {
            final JsonArray ja = new JsonArray();
            saltProvider.getSnapshots().stream()
                    .forEachOrdered(s -> ja.add(toJson(s)));

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(ja.encode());
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            rc.fail(500, e);
        }
    }

    private void handleSaltRebuild(RoutingContext rc) {
        try {
            Instant now = Instant.now();

            // force refresh
            saltProvider.loadContent();

            // mark all the referenced files as ready to archive
            storageManager.archiveSaltLocations();

            // Unlike in regular salt rotation, this should be based on the currently effective snapshot.
            // The latest snapshot may be in the future, and we may have changes that shouldn't be activated yet.
            var effectiveSnapshot = saltProvider.getSnapshot(now);

            var result = saltRotation.rotateSaltsZero(effectiveSnapshot, TargetDate.now(), now);
            if (!result.hasSnapshot()) {
                ResponseUtil.error(rc, 200, result.getReason());
                return;
            }

            storageManager.upload(result.getSnapshot());

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(toJson(result.getSnapshot()).encode());
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            rc.fail(500, e);
        }
    }

    private void handleSaltRotate(RoutingContext rc) {
        try {
            final Optional<Double> fraction = RequestUtil.getDouble(rc, "fraction");
            if (fraction.isEmpty()) return;

            LOGGER.info("Salt rotation age thresholds in seconds: {}", Arrays.stream(SALT_ROTATION_AGE_THRESHOLDS).map(Duration::toSeconds).collect(Collectors.toList()));

            final TargetDate targetDate =
                    RequestUtil.getDate(rc, "target_date", DateTimeFormatter.ISO_LOCAL_DATE)
                            .map(TargetDate::new)
                            .orElse(TargetDate.now().plusDays(1));

            // Force refresh
            saltProvider.loadContent();

            // Mark all the referenced files as ready to archive
            storageManager.archiveSaltLocations();

            final List<RotatingSaltProvider.SaltSnapshot> snapshots = saltProvider.getSnapshots();
            final RotatingSaltProvider.SaltSnapshot lastSnapshot = snapshots.getLast();

            final SaltRotation.Result result = saltRotation.rotateSalts(lastSnapshot, SALT_ROTATION_AGE_THRESHOLDS, fraction.get(), targetDate);
            if (!result.hasSnapshot()) {
                ResponseUtil.error(rc, 200, result.getReason());
                return;
            }

            storageManager.upload(result.getSnapshot());

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(toJson(result.getSnapshot()).encode());
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            rc.fail(500, e);
        }
    }

    private JsonObject toJson(RotatingSaltProvider.SaltSnapshot snapshot) {
        JsonObject jo = new JsonObject();
        jo.put("effective", snapshot.getEffective().toEpochMilli());
        jo.put("expires", snapshot.getExpires().toEpochMilli());
        jo.put("salts_count", snapshot.getAllRotatingSalts().length);
        jo.put("min_last_updated", Arrays.stream(snapshot.getAllRotatingSalts())
                .map(SaltEntry::lastUpdated)
                .min(Long::compare).orElse(null));
        jo.put("max_last_updated", Arrays.stream(snapshot.getAllRotatingSalts())
                .map(SaltEntry::lastUpdated)
                .max(Long::compare).orElse(null));
        return jo;
    }
}