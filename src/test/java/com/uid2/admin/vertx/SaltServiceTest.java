package com.uid2.admin.vertx;

import com.uid2.admin.secret.ISaltRotation;
import com.uid2.admin.vertx.service.IService;
import com.uid2.admin.vertx.service.SaltService;
import com.uid2.admin.vertx.test.ServiceTestBase;
import com.uid2.shared.auth.Role;
import com.uid2.shared.model.SaltEntry;
import com.uid2.shared.store.RotatingSaltProvider;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.time.Instant;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SaltServiceTest extends ServiceTestBase {
    @Mock RotatingSaltProvider saltProvider;
    @Mock ISaltRotation saltRotation;

    @Override
    protected IService createService() {
        return new SaltService(auth, writeLock, saltStoreWriter, saltProvider, saltRotation);
    }

    private void checkSnapshotsResponse(RotatingSaltProvider.SaltSnapshot[] expectedSnapshots, Object[] actualSnapshots) {
        assertEquals(expectedSnapshots.length, actualSnapshots.length);
        for (int i = 0; i < expectedSnapshots.length; ++i) {
            RotatingSaltProvider.SaltSnapshot expectedSnapshot = expectedSnapshots[i];
            JsonObject actualSnapshot = (JsonObject) actualSnapshots[i];
            assertEquals(expectedSnapshot.getEffective(), Instant.ofEpochMilli(actualSnapshot.getLong("effective")));
            assertEquals(expectedSnapshot.getExpires(), Instant.ofEpochMilli(actualSnapshot.getLong("expires")));
            assertEquals(expectedSnapshot.getAllRotatingSalts().length, actualSnapshot.getInteger("salts_count"));
        }
    }

    private void setSnapshots(RotatingSaltProvider.SaltSnapshot... snapshots) {
        when(saltProvider.getSnapshots()).thenReturn(Arrays.asList(snapshots));
    }

    private RotatingSaltProvider.SaltSnapshot makeSnapshot(Instant effective, Instant expires, int nsalts) {
        SaltEntry[] entries = new SaltEntry[nsalts];
        for (int i = 0; i < entries.length; ++i) {
            entries[i] = new SaltEntry(i, "hashed_id", effective.toEpochMilli(), "salt");
        }
        return new RotatingSaltProvider.SaltSnapshot(effective, expires, entries, "test_first_level_salt");
    }

    @Test
    void listSaltSnapshotsNoSnapshots(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SECRET_MANAGER);

        get(vertx, testContext, "api/salt/snapshots", response -> {
            assertEquals(200, response.statusCode());
            assertEquals(0, response.bodyAsJsonArray().size());
            testContext.completeNow();
        });
    }

    @Test
    void listSaltSnapshotsWithSnapshots(Vertx vertx, VertxTestContext testContext) {
        fakeAuth(Role.SECRET_MANAGER);

        final RotatingSaltProvider.SaltSnapshot[] snapshots = {
                makeSnapshot(Instant.ofEpochMilli(10001), Instant.ofEpochMilli(20001), 10),
                makeSnapshot(Instant.ofEpochMilli(10002), Instant.ofEpochMilli(20002), 10),
                makeSnapshot(Instant.ofEpochMilli(10003), Instant.ofEpochMilli(20003), 10),
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
        fakeAuth(Role.SECRET_MANAGER);

        final RotatingSaltProvider.SaltSnapshot[] snapshots = {
                makeSnapshot(Instant.ofEpochMilli(10001), Instant.ofEpochMilli(20001), 10),
                makeSnapshot(Instant.ofEpochMilli(10002), Instant.ofEpochMilli(20002), 10),
                makeSnapshot(Instant.ofEpochMilli(10003), Instant.ofEpochMilli(20003), 10),
        };
        setSnapshots(snapshots);

        final RotatingSaltProvider.SaltSnapshot[] addedSnapshots = {
                makeSnapshot(Instant.ofEpochMilli(10004), Instant.ofEpochMilli(20004), 10),
        };
        when(saltRotation.rotateSalts(any(), any(), eq(0.2))).thenReturn(ISaltRotation.Result.fromSnapshot(addedSnapshots[0]));

        post(vertx, testContext, "api/salt/rotate?min_ages_in_seconds=50,60,70&fraction=0.2", "", response -> {
            assertEquals(200, response.statusCode());
            checkSnapshotsResponse(addedSnapshots, new Object[]{response.bodyAsJsonObject()});
            verify(saltStoreWriter).upload(any());
            verify(saltStoreWriter, times(1)).archiveSaltLocations();
            testContext.completeNow();
        });
    }

    @Test
    void rotateSaltsNoNewSnapshot(Vertx vertx, VertxTestContext testContext) throws Exception {
        fakeAuth(Role.SECRET_MANAGER);

        final RotatingSaltProvider.SaltSnapshot[] snapshots = {
                makeSnapshot(Instant.ofEpochMilli(10001), Instant.ofEpochMilli(20001), 10),
                makeSnapshot(Instant.ofEpochMilli(10002), Instant.ofEpochMilli(20002), 10),
                makeSnapshot(Instant.ofEpochMilli(10003), Instant.ofEpochMilli(20003), 10),
        };
        setSnapshots(snapshots);

        when(saltRotation.rotateSalts(any(), any(), eq(0.2))).thenReturn(ISaltRotation.Result.noSnapshot("test"));

        post(vertx, testContext, "api/salt/rotate?min_ages_in_seconds=50,60,70&fraction=0.2", "", response -> {
            assertEquals(200, response.statusCode());
            JsonObject jo = response.bodyAsJsonObject();
            assertFalse(jo.containsKey("effective"));
            assertFalse(jo.containsKey("expires"));
            verify(saltStoreWriter, times(0)).upload(any());
            testContext.completeNow();
        });
    }
}
