package com.uid2.admin.vertx.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uid2.admin.auth.AdminAuthMiddleware;
import com.uid2.admin.secret.ISaltRotation;
import com.uid2.admin.store.writer.SaltStoreWriter;
import com.uid2.admin.vertx.RequestUtil;
import com.uid2.admin.vertx.ResponseUtil;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.SaltEntry;
import com.uid2.shared.store.RotatingSaltProvider;
import com.uid2.shared.util.Mapper;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class SaltService implements IService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SaltService.class);
    private static final ObjectMapper OBJECT_MAPPER = Mapper.getInstance();
    private static final int REFRESH_PERIOD = 30;

    private final AdminAuthMiddleware auth;
    private final WriteLock writeLock;
    private final SaltStoreWriter storageManager;
    private final RotatingSaltProvider saltProvider;
    private final ISaltRotation saltRotation;

    public SaltService(AdminAuthMiddleware auth,
                       WriteLock writeLock,
                       SaltStoreWriter storageManager,
                       RotatingSaltProvider saltProvider,
                       ISaltRotation saltRotation) {
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

        router.post("/api/salt/backfill").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleSaltBackfill(ctx);
            }
        }, Role.SUPER_USER, Role.SECRET_ROTATION));

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

    private void handleSaltBackfill(RoutingContext rc) {
        try {
            this.saltProvider.loadContent();
            List<RotatingSaltProvider.SaltSnapshot> snapshots = this.saltProvider.getSnapshots();
            RotatingSaltProvider.SaltSnapshot lastSnapshot = snapshots.getLast();

            Instant now = Instant.now();
            BackfillResult result = new BackfillResult();

            for (SaltEntry saltEntry : lastSnapshot.getAllRotatingSalts()) {
                long age = Duration.between(Instant.ofEpochMilli(saltEntry.getLastUpdated()), now).toDays();
                long nextCutoff = Math.ceilDiv(age, REFRESH_PERIOD) * REFRESH_PERIOD; // Round age to the nearest multiple of 30
                long refreshFrom = Instant.ofEpochMilli(saltEntry.getLastUpdated()).plus(nextCutoff, ChronoUnit.DAYS).toEpochMilli();
                saltEntry.setRefreshFrom(refreshFrom);
                result.getSaltLifetimeCounts().put(age, result.getSaltLifetimeCounts().getOrDefault(age, 0) + 1);

                long daysToRefresh = Duration.between(now, Instant.ofEpochMilli(refreshFrom)).toDays();
                result.getDaysRemainingCounts().put(daysToRefresh, result.getDaysRemainingCounts().getOrDefault(daysToRefresh, 0) + 1);
            }

            storageManager.upload(lastSnapshot);

            rc.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(OBJECT_MAPPER.writeValueAsString(result));
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

            // Force refresh
            this.saltProvider.loadContent();

            // Mark all the referenced files as ready to archive
            storageManager.archiveSaltLocations();

            final List<RotatingSaltProvider.SaltSnapshot> snapshots = this.saltProvider.getSnapshots();
            final RotatingSaltProvider.SaltSnapshot lastSnapshot = snapshots.getLast();
            final ISaltRotation.Result result = saltRotation.rotateSalts(
                    lastSnapshot, minAges, fraction.get(), REFRESH_PERIOD);
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
                .map(SaltEntry::getLastUpdated)
                .min(Long::compare).orElse(null));
        jo.put("max_last_updated", Arrays.stream(snapshot.getAllRotatingSalts())
                .map(SaltEntry::getLastUpdated)
                .max(Long::compare).orElse(null));
        return jo;
    }

    @Getter
    private static class BackfillResult {
        private final Map<Long, Integer> saltLifetimeCounts;
        private final Map<Long, Integer> daysRemainingCounts;

        public BackfillResult() {
            saltLifetimeCounts = new TreeMap<>();
            daysRemainingCounts = new TreeMap<>();
        }
    }
}
