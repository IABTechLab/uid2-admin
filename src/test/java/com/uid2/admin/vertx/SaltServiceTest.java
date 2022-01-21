// Copyright (c) 2021 The Trade Desk, Inc
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice,
//    this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice,
//    this list of conditions and the following disclaimer in the documentation
//    and/or other materials provided with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
// POSSIBILITY OF SUCH DAMAGE.

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
        return new SaltService(auth, writeLock, storageManager, saltProvider, saltRotation);
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

        get(vertx, "api/salt/snapshots", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
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

        get(vertx, "api/salt/snapshots", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
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

        post(vertx, "api/salt/rotate?min_ages_in_seconds=50,60,70&fraction=0.2", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            checkSnapshotsResponse(addedSnapshots, new Object[]{response.bodyAsJsonObject()});
            try {
                verify(storageManager).uploadSalts(any(), any());
            } catch (Exception ex) {
                fail(ex);
            }
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

        post(vertx, "api/salt/rotate?min_ages_in_seconds=50,60,70&fraction=0.2", "", ar -> {
            assertTrue(ar.succeeded());
            HttpResponse response = ar.result();
            assertEquals(200, response.statusCode());
            JsonObject jo = response.bodyAsJsonObject();
            assertFalse(jo.containsKey("effective"));
            assertFalse(jo.containsKey("expires"));
            try {
                verify(storageManager, times(0)).uploadSalts(any(), any());
            } catch (Exception ex) {
                fail(ex);
            }
            testContext.completeNow();
        });
    }
}
