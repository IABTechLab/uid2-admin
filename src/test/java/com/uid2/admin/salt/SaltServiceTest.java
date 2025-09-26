package com.uid2.admin.salt;

import com.uid2.admin.salt.helper.SaltSnapshotBuilder;
import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.service.SaltService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.auth.Role;
import com.uid2.shared.store.salt.RotatingSaltProvider;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

import static com.uid2.admin.salt.helper.TargetDateUtil.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SaltServiceTest extends ServiceTestBase {
    private final TargetDate utcTomorrow = TargetDate.now().plusDays(1);

    @Mock
    private RotatingSaltProvider saltProvider;
    @Mock
    private SaltRotation saltRotation;

    @Override
    protected IService createService() {
        return new SaltService(auth, writeLock, saltStoreWriter, saltProvider, saltRotation);
    }

    @Test
    void listSaltSnapshotsNoSnapshots(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        get(vertx, testContext, "api/salt/snapshots", response -> {
            assertEquals(200, response.statusCode());
            assertEquals(0, response.bodyAsJsonArray().size());
            testContext.completeNow();
        });
    }

    @Test
    void listSaltSnapshotsWithSnapshots(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.MAINTAINER);

        final SaltSnapshotBuilder[] snapshots = {
                SaltSnapshotBuilder.start().effective(daysLater(1)).expires(daysLater(4)).entries(10, daysLater(1)),
                SaltSnapshotBuilder.start().effective(daysLater(2)).expires(daysLater(5)).entries(10, daysLater(2)),
                SaltSnapshotBuilder.start().effective(daysLater(3)).expires(daysLater(6)).entries(10, daysLater(3)),
        };
        setSnapshots(snapshots);

        get(vertx, testContext, "api/salt/snapshots", response -> {
            assertEquals(200, response.statusCode());
            checkSnapshotsResponse(snapshots, response.bodyAsJsonArray().stream().toArray());
            testContext.completeNow();
        });
    }

    @Test
    void rotateSalts(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.SUPER_USER);

        final SaltSnapshotBuilder[] snapshots = {
                SaltSnapshotBuilder.start().effective(daysLater(1)).expires(daysLater(4)).entries(10, daysLater(1)),
                SaltSnapshotBuilder.start().effective(daysLater(2)).expires(daysLater(5)).entries(10, daysLater(2)),
                SaltSnapshotBuilder.start().effective(daysLater(3)).expires(daysLater(6)).entries(10, daysLater(3)),
        };
        setSnapshots(snapshots);

        final SaltSnapshotBuilder[] addedSnapshots = {
                SaltSnapshotBuilder.start().effective(daysLater(7)).expires(daysLater(8)).entries(10, daysLater(7)),
        };

        var result = SaltRotation.Result.fromSnapshot(addedSnapshots[0].build());
        when(saltRotation.rotateSalts(any(), any(), eq(0.2), eq(utcTomorrow))).thenReturn(result);

        post(vertx, testContext, "api/salt/rotate?fraction=0.2", "", response -> {
            assertEquals(200, response.statusCode());
            checkSnapshotsResponse(addedSnapshots, new Object[]{response.bodyAsJsonObject()});
            verify(saltStoreWriter).upload(any());
            verify(saltStoreWriter, times(1)).archiveSaltLocations();
            testContext.completeNow();
        });
    }

    @Test
    void rotateSaltsNoNewSnapshot(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.SUPER_USER);

        final SaltSnapshotBuilder[] snapshots = {
                SaltSnapshotBuilder.start().effective(daysLater(1)).expires(daysLater(4)).entries(10, daysLater(1)),
                SaltSnapshotBuilder.start().effective(daysLater(2)).expires(daysLater(5)).entries(10, daysLater(2)),
                SaltSnapshotBuilder.start().effective(daysLater(3)).expires(daysLater(6)).entries(10, daysLater(3)),
        };
        setSnapshots(snapshots);

        var result = SaltRotation.Result.noSnapshot("test");
        when(saltRotation.rotateSalts(any(), any(), eq(0.2), eq(utcTomorrow))).thenReturn(result);

        post(vertx, testContext, "api/salt/rotate?fraction=0.2", "", response -> {
            assertEquals(200, response.statusCode());
            JsonObject jo = response.bodyAsJsonObject();
            assertFalse(jo.containsKey("effective"));
            assertFalse(jo.containsKey("expires"));
            verify(saltStoreWriter, times(0)).upload(any());
            testContext.completeNow();
        });
    }

    @Test
    void rotateSaltsWithSpecificTargetDate(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.SUPER_USER);
        final SaltSnapshotBuilder[] snapshots = {
                SaltSnapshotBuilder.start().effective(daysEarlier(5)).expires(daysEarlier(4)).entries(10, daysEarlier(5)),
                SaltSnapshotBuilder.start().effective(daysEarlier(4)).expires(daysEarlier(3)).entries(10, daysEarlier(4)),
                SaltSnapshotBuilder.start().effective(daysEarlier(3)).expires(daysEarlier(2)).entries(10, daysEarlier(3)),
        };
        setSnapshots(snapshots);

        final SaltSnapshotBuilder[] addedSnapshots = {
                SaltSnapshotBuilder.start().effective(targetDate()).expires(daysEarlier(1)).entries(10, targetDate()),
        };

        var result = SaltRotation.Result.fromSnapshot(addedSnapshots[0].build());
        when(saltRotation.rotateSalts(any(), any(), eq(0.2), eq(targetDate()))).thenReturn(result);

        post(vertx, testContext, "api/salt/rotate?fraction=0.2&target_date=2025-01-01", "", response -> {
            assertEquals(200, response.statusCode());
            testContext.completeNow();
        });
    }

    @Test
    void rotateSaltsWithDefaultAgeThresholds(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.SUPER_USER);

        final SaltSnapshotBuilder lastSnapshot = SaltSnapshotBuilder.start().effective(daysEarlier(1)).expires(daysLater(6)).entries(1, daysEarlier(1));
        setSnapshots(lastSnapshot);

        var result = SaltRotation.Result.fromSnapshot(SaltSnapshotBuilder.start().effective(targetDate()).expires(daysEarlier(7)).entries(1, targetDate()).build());

        Duration[] expectedDefaultAgeThresholds = new Duration[]{
                Duration.ofDays(30), Duration.ofDays(60), Duration.ofDays(90), Duration.ofDays(120),
                Duration.ofDays(150), Duration.ofDays(180), Duration.ofDays(210), Duration.ofDays(240),
                Duration.ofDays(270), Duration.ofDays(300), Duration.ofDays(330), Duration.ofDays(360),
                Duration.ofDays(390)
        };

        when(saltRotation.rotateSalts(any(), eq(expectedDefaultAgeThresholds), eq(0.2), eq(utcTomorrow))).thenReturn(result);

        post(vertx, testContext, "api/salt/rotate?fraction=0.2", "", response -> {
            verify(saltRotation).rotateSalts(any(), eq(expectedDefaultAgeThresholds), eq(0.2), eq(utcTomorrow));
            assertEquals(200, response.statusCode());
            testContext.completeNow();
        });
    }

    private void checkSnapshotsResponse(SaltSnapshotBuilder[] expectedSnapshots, Object[] actualSnapshots) {
        assertEquals(expectedSnapshots.length, actualSnapshots.length);
        for (int i = 0; i < expectedSnapshots.length; ++i) {
            RotatingSaltProvider.SaltSnapshot expectedSnapshot = expectedSnapshots[i].build();
            JsonObject actualSnapshot = (JsonObject) actualSnapshots[i];
            assertEquals(expectedSnapshot.getEffective(), Instant.ofEpochMilli(actualSnapshot.getLong("effective")));
            assertEquals(expectedSnapshot.getExpires(), Instant.ofEpochMilli(actualSnapshot.getLong("expires")));
            assertEquals(expectedSnapshot.getAllRotatingSalts().length, actualSnapshot.getInteger("salts_count"));
        }
    }

    private void setSnapshots(SaltSnapshotBuilder... snapshots) {
        when(saltProvider.getSnapshots()).thenReturn(Arrays.stream(snapshots).map(SaltSnapshotBuilder::build).toList());
    }
}
