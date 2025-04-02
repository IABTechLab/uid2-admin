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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class SaltService implements IService {
    public static class SimulationResult {
        public Map<Long, Integer> saltLifetimeCounts = new TreeMap<>();
        public Map<Long, Integer> daysRemainingCounts = new TreeMap<>();
    }

    public static Instant NOW = Instant.ofEpochMilli(1743459126294L);

    private static final Logger LOGGER = LoggerFactory.getLogger(SaltService.class);
    private static final ObjectMapper OBJECT_MAPPER = Mapper.getInstance();

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

        router.post("/api/salt/rotate").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleSaltRotate(ctx);
            }
        }, Role.SUPER_USER, Role.SECRET_ROTATION));

        router.post("/api/salt/simulate").blockingHandler(auth.handle((ctx) -> {
            synchronized (writeLock) {
                this.handleSaltSimulate(ctx);
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

    private void handleSaltSimulate(RoutingContext rc) {
        try {
            // Sanitise input
            final Optional<Double> fraction = RequestUtil.getDouble(rc, "fraction");
            if (fraction.isEmpty()) return;
            final Duration[] minAges = RequestUtil.getDurations(rc, "min_ages_in_seconds");
            if (minAges == null) return;
            final Optional<Integer> period = RequestUtil.getInteger(rc, "period");
            if (period.isEmpty()) return;

            // Load snapshots
            this.saltProvider.loadContent();
            List<RotatingSaltProvider.SaltSnapshot> snapshots = this.saltProvider.getSnapshots();
            RotatingSaltProvider.SaltSnapshot lastSnapshot = snapshots.getLast();

            SimulationResult result = new SimulationResult();
            for (SaltEntry saltEntry : lastSnapshot.getAllRotatingSalts()) {
                long age = Duration.between(Instant.ofEpochMilli(saltEntry.getLastUpdated()), NOW).toDays();
                long nextCutoff = Math.ceilDiv(age, period.get()) * period.get();
                long refreshFrom = Instant.ofEpochMilli(saltEntry.getLastUpdated()).plus(Duration.ofDays(nextCutoff)).toEpochMilli();
                saltEntry.setRefreshFrom(refreshFrom);
                result.saltLifetimeCounts.put(age, result.saltLifetimeCounts.getOrDefault(age, 0) + 1);

                long daysToRefresh = Duration.between(NOW, Instant.ofEpochMilli(refreshFrom)).toDays();
                result.daysRemainingCounts.put(daysToRefresh, result.daysRemainingCounts.getOrDefault(daysToRefresh, 0) + 1);
            }


//            String location = "salts/backfilled_salts.txt";
//            Path newSaltsFile = Files.createTempFile("operators", ".txt");
//            LOGGER.info("Temp path: {}", newSaltsFile.toAbsolutePath());
//            try (BufferedWriter w = Files.newBufferedWriter(newSaltsFile)) {
//                for (SaltEntry entry : lastSnapshot.getAllRotatingSalts()) {
//                    w.write(
//                            entry.getId() + ","
//                                    + entry.getSalt() + ","
//                                    + entry.getLastUpdated() + ","
//                                    + entry.getRefreshFrom() + "\n");
//                }
//            }
//            cloudStorage.upload(newSaltsFile.toString(), location);

            // Forward Simulation
            for (int i = 0; i < 100; i++) {
                ISaltRotation.Result rotationResult = saltRotation.rotateSaltsSimulation(
                        lastSnapshot, minAges, fraction.get(), period.get(), i);
                if (!rotationResult.hasSnapshot()) {
                    ResponseUtil.error(rc, 200, rotationResult.getReason());
                    return;
                }

                lastSnapshot = rotationResult.getSnapshot();

                LOGGER.info("=====================");
            }

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

            // force refresh
            this.saltProvider.loadContent();

            // mark all the referenced files as ready to archive
            storageManager.archiveSaltLocations();

            final List<RotatingSaltProvider.SaltSnapshot> snapshots = this.saltProvider.getSnapshots();
            final RotatingSaltProvider.SaltSnapshot lastSnapshot = snapshots.getLast();
            final ISaltRotation.Result result = saltRotation.rotateSalts(
                    lastSnapshot, minAges, fraction.get());
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
}