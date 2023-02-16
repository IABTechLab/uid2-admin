package com.uid2.admin.vertx.service;

import com.uid2.admin.secret.ISaltRotation;
import com.uid2.admin.store.writer.SaltStoreWriter;
import com.uid2.admin.vertx.RequestUtil;
import com.uid2.admin.vertx.ResponseUtil;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.shared.auth.Role;
import com.uid2.shared.middleware.AuthMiddleware;
import com.uid2.shared.model.SaltEntry;
import com.uid2.shared.store.RotatingSaltProvider;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class SaltService implements IService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SaltService.class);

    private final AuthMiddleware auth;
    private final WriteLock writeLock;
    private final SaltStoreWriter storageManager;
    private final RotatingSaltProvider saltProvider;
    private final ISaltRotation saltRotation;

    public SaltService(AuthMiddleware auth,
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
                auth.handle(this::handleSaltSnapshots, Role.SECRET_MANAGER));

        router.post("/api/salt/rotate").blockingHandler(auth.handle(ctx -> {
            synchronized (writeLock) {
                this.handleSaltRotate(ctx);
            }
        }, Role.SECRET_MANAGER));
    }

    private void handleSaltSnapshots(RoutingContext ctx) {
        try {
            final JsonArray ja = new JsonArray();
            this.saltProvider.getSnapshots().stream()
                    .forEachOrdered(s -> ja.add(toJson(s)));

            ctx.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(ja.encode());
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            ctx.fail(500, e);
        }
    }

    private void handleSaltRotate(RoutingContext ctx) {
        try {
            final Optional<Double> fraction = RequestUtil.getDouble(ctx, "fraction");
            if (!fraction.isPresent()) return;
            final Duration[] minAges = RequestUtil.getDurations(ctx, "min_ages_in_seconds");
            if (minAges == null) return;

            // force refresh
            this.saltProvider.loadContent();

            final List<RotatingSaltProvider.SaltSnapshot> snapshots = this.saltProvider.getSnapshots();
            final RotatingSaltProvider.SaltSnapshot lastSnapshot = snapshots.get(snapshots.size() - 1);
            final ISaltRotation.Result result = saltRotation.rotateSalts(
                    lastSnapshot, minAges, fraction.get());
            if (!result.hasSnapshot()) {
                ResponseUtil.error(ctx, 200, result.getReason());
                return;
            }

            storageManager.upload(result.getSnapshot());

            ctx.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(toJson(result.getSnapshot()).encode());
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            ctx.fail(500, e);
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