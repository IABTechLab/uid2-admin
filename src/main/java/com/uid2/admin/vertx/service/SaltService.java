package com.uid2.admin.vertx.service;

import com.uid2.admin.auth.AdminAuthMiddleware;
import com.uid2.admin.salt.SaltRotation;
import com.uid2.admin.salt.TargetDate;
import com.uid2.admin.store.writer.SaltStoreWriter;
import com.uid2.admin.vertx.RequestUtil;
import com.uid2.admin.vertx.ResponseUtil;
import com.uid2.admin.vertx.WriteLock;
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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class SaltService implements IService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SaltService.class);

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
        router.get("/api/salt/snapshots").handler(
                auth.handle(this::handleSaltSnapshots, Role.MAINTAINER));

        router.post("/api/salt/rotate").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleSaltRotate(ctx);
            }
        }, Role.SUPER_USER, Role.SECRET_ROTATION));
    }

    private void handleSaltSnapshots(RoutingContext rc) {
        try {
            final JsonArray ja = new JsonArray();
            this.saltProvider.getSnapshots().stream()
                    .forEachOrdered(s -> ja.add(toJson(s)));

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(ja.encode());
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            rc.fail(500, e);
        }
    }

    private void handleSaltRotate(RoutingContext rc) {
        try {
            final Optional<Double> fraction = RequestUtil.getDouble(rc, "fraction");
            if (fraction.isEmpty()) return;
            final Duration[] minAges = RequestUtil.getDurations(rc, "min_ages_in_seconds");
            if (minAges == null) return;


            final TargetDate targetDate =
                    RequestUtil.getDate(rc, "target_date", DateTimeFormatter.ISO_LOCAL_DATE)
                            .map(TargetDate::new)
                            .orElse(TargetDate.now().plusDays(1))
            ;

            // force refresh
            this.saltProvider.loadContent();

            // mark all the referenced files as ready to archive
            storageManager.archiveSaltLocations();

            final List<RotatingSaltProvider.SaltSnapshot> snapshots = this.saltProvider.getSnapshots();
            final RotatingSaltProvider.SaltSnapshot lastSnapshot = snapshots.getLast();

            final SaltRotation.Result result = saltRotation.rotateSalts(lastSnapshot, minAges, fraction.get(), targetDate);
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