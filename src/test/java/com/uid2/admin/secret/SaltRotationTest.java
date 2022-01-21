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

package com.uid2.admin.secret;

import com.uid2.shared.model.SaltEntry;
import com.uid2.shared.store.RotatingSaltProvider;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SaltRotationTest {
    private static final int ACTIVATES_IN_SECONDS = 3600;
    private static final int EXPIRES_IN_SECONDS = 7200;

    private AutoCloseable mocks;
    @Mock private IKeyGenerator keyGenerator;
    private SaltRotation saltRotation;

    @BeforeEach
    void setup() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);

        JsonObject config = new JsonObject();
        config.put("salt_snapshot_activates_in_seconds", ACTIVATES_IN_SECONDS);
        config.put("salt_snapshot_expires_after_seconds", EXPIRES_IN_SECONDS);

        saltRotation = new SaltRotation(config, keyGenerator);
    }

    private static class SnapshotBuilder
    {
        private final List<SaltEntry> entries = new ArrayList<>();

        private SnapshotBuilder() {}

        public static SnapshotBuilder start() { return new SnapshotBuilder(); }

        public SnapshotBuilder withEntries(int count, Instant lastUpdated) {
            for (int i = 0; i < count; ++i) {
                entries.add(new SaltEntry(entries.size(), "h", lastUpdated.toEpochMilli(), "salt" + entries.size()));
            }
            return this;
        }

        public RotatingSaltProvider.SaltSnapshot build(Instant effective, Instant expires) {
            return new RotatingSaltProvider.SaltSnapshot(
                    effective, expires, entries.stream().toArray(SaltEntry[]::new), "test_first_level_salt");
        }
    }

    private int countEntriesWithLastUpdated(SaltEntry[] entries, Instant lastUpdated) {
        return (int)Arrays.stream(entries).filter(e -> e.getLastUpdated() == lastUpdated.toEpochMilli()).count();
    }

    private static void assertEqualsClose(Instant expected, Instant actual, int withinSeconds) {
        assertTrue(expected.minusSeconds(withinSeconds).isBefore(actual));
        assertTrue(expected.plusSeconds(withinSeconds).isAfter(actual));
    }

    @Test
    void rotateSaltsLastSnapshotIsUpToDate() throws Exception {
        final Duration[] minAges = {
                Duration.ofSeconds(100),
                Duration.ofSeconds(200),
        };

        final RotatingSaltProvider.SaltSnapshot lastSnapshot = SnapshotBuilder.start()
                .withEntries(10, Instant.ofEpochSecond(10001))
                .build(Instant.now().plusSeconds(ACTIVATES_IN_SECONDS + 10),
                        Instant.now().plusSeconds(ACTIVATES_IN_SECONDS + EXPIRES_IN_SECONDS + 10));

        final ISaltRotation.Result result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.2);
        assertFalse(result.hasSnapshot());
        verify(keyGenerator, times(0)).generateRandomKeyString(anyInt());
    }

    @Test
    void rotateSaltsAllSaltsUpToDate() throws Exception {
        final Duration[] minAges = {
                Duration.ofSeconds(100),
                Duration.ofSeconds(200),
        };

        final RotatingSaltProvider.SaltSnapshot lastSnapshot = SnapshotBuilder.start()
                .withEntries(10, Instant.now())
                .build(Instant.now(), Instant.now());

        final ISaltRotation.Result result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.2);
        assertFalse(result.hasSnapshot());
        verify(keyGenerator, times(0)).generateRandomKeyString(anyInt());
    }

    @Test
    void rotateSaltsAllSaltsOld() throws Exception {
        final Duration[] minAges = {
                Duration.ofSeconds(100),
                Duration.ofSeconds(200),
        };

        final Instant lastUpdated1 = Instant.now().minusSeconds(500);
        final RotatingSaltProvider.SaltSnapshot lastSnapshot = SnapshotBuilder.start()
                .withEntries(10, lastUpdated1)
                .build(Instant.now(), Instant.now());

        final ISaltRotation.Result result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.2);
        assertTrue(result.hasSnapshot());
        assertEquals(2, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), result.getSnapshot().getEffective()));
        assertEquals(8, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), lastUpdated1));
        assertEqualsClose(Instant.now().plusSeconds(ACTIVATES_IN_SECONDS), result.getSnapshot().getEffective(), 10);
        assertEqualsClose(Instant.now().plusSeconds(ACTIVATES_IN_SECONDS+EXPIRES_IN_SECONDS), result.getSnapshot().getExpires(), 10);
        verify(keyGenerator, times(2)).generateRandomKeyString(anyInt());
    }

    @Test
    void rotateSaltsRotateSaltsFromOldestBucketOnly() throws Exception {
        final Duration[] minAges = {
                Duration.ofSeconds(100),
                Duration.ofSeconds(200),
        };

        final Instant lastUpdated1 = Instant.now().minusSeconds(500);
        final Instant lastUpdated2 = Instant.now().minusSeconds(150);
        final Instant lastUpdated3 = Instant.now().minusSeconds(50);
        final RotatingSaltProvider.SaltSnapshot lastSnapshot = SnapshotBuilder.start()
                .withEntries(3, lastUpdated1)
                .withEntries(5, lastUpdated2)
                .withEntries(2, lastUpdated3)
                .build(Instant.now(), Instant.now());

        final ISaltRotation.Result result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.2);
        assertTrue(result.hasSnapshot());
        assertEquals(2, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), result.getSnapshot().getEffective()));
        assertEquals(1, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), lastUpdated1));
        assertEquals(5, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), lastUpdated2));
        assertEquals(2, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), lastUpdated3));
        assertEqualsClose(Instant.now().plusSeconds(ACTIVATES_IN_SECONDS), result.getSnapshot().getEffective(), 10);
        assertEqualsClose(Instant.now().plusSeconds(ACTIVATES_IN_SECONDS+EXPIRES_IN_SECONDS), result.getSnapshot().getExpires(), 10);
        verify(keyGenerator, times(2)).generateRandomKeyString(anyInt());
    }

    @Test
    void rotateSaltsRotateSaltsFromNewerBucketOnly() throws Exception {
        final Duration[] minAges = {
                Duration.ofSeconds(100),
                Duration.ofSeconds(200),
        };

        final Instant lastUpdated1 = Instant.now().minusSeconds(150);
        final Instant lastUpdated2 = Instant.now().minusSeconds(50);
        final RotatingSaltProvider.SaltSnapshot lastSnapshot = SnapshotBuilder.start()
                .withEntries(3, lastUpdated1)
                .withEntries(7, lastUpdated2)
                .build(Instant.now(), Instant.now());

        final ISaltRotation.Result result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.2);
        assertTrue(result.hasSnapshot());
        assertEquals(2, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), result.getSnapshot().getEffective()));
        assertEquals(1, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), lastUpdated1));
        assertEquals(7, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), lastUpdated2));
        assertEqualsClose(Instant.now().plusSeconds(ACTIVATES_IN_SECONDS), result.getSnapshot().getEffective(), 10);
        assertEqualsClose(Instant.now().plusSeconds(ACTIVATES_IN_SECONDS+EXPIRES_IN_SECONDS), result.getSnapshot().getExpires(), 10);
        verify(keyGenerator, times(2)).generateRandomKeyString(anyInt());
    }

    @Test
    void rotateSaltsRotateSaltsFromBothBuckets() throws Exception {
        final Duration[] minAges = {
                Duration.ofSeconds(100),
                Duration.ofSeconds(200),
        };

        final Instant lastUpdated1 = Instant.now().minusSeconds(500);
        final Instant lastUpdated2 = Instant.now().minusSeconds(150);
        final Instant lastUpdated3 = Instant.now().minusSeconds(50);
        final RotatingSaltProvider.SaltSnapshot lastSnapshot = SnapshotBuilder.start()
                .withEntries(3, lastUpdated1)
                .withEntries(5, lastUpdated2)
                .withEntries(2, lastUpdated3)
                .build(Instant.now(), Instant.now());

        final ISaltRotation.Result result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.45);
        assertTrue(result.hasSnapshot());
        assertEquals(5, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), result.getSnapshot().getEffective()));
        assertEquals(0, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), lastUpdated1));
        assertEquals(3, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), lastUpdated2));
        assertEquals(2, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), lastUpdated3));
        assertEqualsClose(Instant.now().plusSeconds(ACTIVATES_IN_SECONDS), result.getSnapshot().getEffective(), 10);
        assertEqualsClose(Instant.now().plusSeconds(ACTIVATES_IN_SECONDS+EXPIRES_IN_SECONDS), result.getSnapshot().getExpires(), 10);
        verify(keyGenerator, times(5)).generateRandomKeyString(anyInt());
    }

    @Test
    void rotateSaltsRotateSaltsInsufficientOutdatedSalts() throws Exception {
        final Duration[] minAges = {
                Duration.ofSeconds(100),
                Duration.ofSeconds(200),
        };

        final Instant lastUpdated1 = Instant.now().minusSeconds(500);
        final Instant lastUpdated2 = Instant.now().minusSeconds(150);
        final Instant lastUpdated3 = Instant.now().minusSeconds(50);
        final RotatingSaltProvider.SaltSnapshot lastSnapshot = SnapshotBuilder.start()
                .withEntries(1, lastUpdated1)
                .withEntries(2, lastUpdated2)
                .withEntries(7, lastUpdated3)
                .build(Instant.now(), Instant.now());

        final ISaltRotation.Result result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.45);
        assertTrue(result.hasSnapshot());
        assertEquals(3, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), result.getSnapshot().getEffective()));
        assertEquals(0, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), lastUpdated1));
        assertEquals(0, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), lastUpdated2));
        assertEquals(7, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), lastUpdated3));
        assertEqualsClose(Instant.now().plusSeconds(ACTIVATES_IN_SECONDS), result.getSnapshot().getEffective(), 10);
        assertEqualsClose(Instant.now().plusSeconds(ACTIVATES_IN_SECONDS+EXPIRES_IN_SECONDS), result.getSnapshot().getExpires(), 10);
        verify(keyGenerator, times(3)).generateRandomKeyString(anyInt());
    }
}
