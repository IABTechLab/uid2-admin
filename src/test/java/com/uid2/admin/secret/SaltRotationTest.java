package com.uid2.admin.secret;

import com.uid2.shared.model.SaltEntry;
import com.uid2.shared.secret.IKeyGenerator;
import com.uid2.shared.store.salt.RotatingSaltProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SaltRotationTest {
    private AutoCloseable mocks;

    @Mock private IKeyGenerator keyGenerator;
    private SaltRotation saltRotation;

    private final LocalDateTime now = LocalDate.now(Clock.systemUTC()).atStartOfDay().plusDays(1);

    @BeforeEach
    void setup() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);

        saltRotation = new SaltRotation(keyGenerator);
    }

    private static class SnapshotBuilder
    {
        private final List<SaltEntry> entries = new ArrayList<>();

        private SnapshotBuilder() {}

        public static SnapshotBuilder start() { return new SnapshotBuilder(); }

        public SnapshotBuilder withEntries(int count, Instant lastUpdated) {
            for (int i = 0; i < count; ++i) {
                entries.add(new SaltEntry(entries.size(), "h", lastUpdated.toEpochMilli(), "salt" + entries.size(), null, null, null, null));
            }
            return this;
        }

        public RotatingSaltProvider.SaltSnapshot build(Instant effective, Instant expires) {
            return new RotatingSaltProvider.SaltSnapshot(
                    effective, expires, entries.stream().toArray(SaltEntry[]::new), "test_first_level_salt");
        }
    }

    private int countEntriesWithLastUpdated(SaltEntry[] entries, Instant lastUpdated) {
        return (int)Arrays.stream(entries).filter(e -> e.lastUpdated() == lastUpdated.toEpochMilli()).count();
    }

    private static void assertEqualsClose(Instant expected, Instant actual, int withinSeconds) {
        assertTrue(expected.minusSeconds(withinSeconds).isBefore(actual));
        assertTrue(expected.plusSeconds(withinSeconds).isAfter(actual));
    }

    @Test
    void rotateSaltsLastSnapshotIsUpToDate() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(1),
                Duration.ofDays(2),
        };
        final RotatingSaltProvider.SaltSnapshot lastSnapshot = SnapshotBuilder.start()
                .withEntries(10, Instant.ofEpochSecond(10001))
                .build(now.minusDays(1).toInstant(ZoneOffset.UTC),
                        now.plusDays(7).toInstant(ZoneOffset.UTC));

        final ISaltRotation.Result firstRotation = saltRotation.rotateSalts(lastSnapshot, minAges, 0.2);
        assertTrue(firstRotation.hasSnapshot());
        final ISaltRotation.Result secondRotation = saltRotation.rotateSalts(firstRotation.getSnapshot(), minAges, 0.2);
        assertFalse(secondRotation.hasSnapshot());
    }

    @Test
    void rotateSaltsAllSaltsUpToDate() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(1),
                Duration.ofDays(2),
        };

        final RotatingSaltProvider.SaltSnapshot lastSnapshot = SnapshotBuilder.start()
                .withEntries(10, now.toInstant(ZoneOffset.UTC))
                .build(now.minusDays(1).toInstant(ZoneOffset.UTC),
                        now.plusDays(8).toInstant(ZoneOffset.UTC));

        final ISaltRotation.Result result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.2);
        assertFalse(result.hasSnapshot());
        verify(keyGenerator, times(0)).generateRandomKeyString(anyInt());
    }

    @Test
    void rotateSaltsAllSaltsOld() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(1),
                Duration.ofDays(2),
        };

        final RotatingSaltProvider.SaltSnapshot lastSnapshot = SnapshotBuilder.start()
                .withEntries(10, now.minusDays(2).toInstant(ZoneOffset.UTC))
                .build(Instant.now(), Instant.now());

        final ISaltRotation.Result result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.2);
        assertTrue(result.hasSnapshot());
        assertEquals(2, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), result.getSnapshot().getEffective()));
        assertEquals(8, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), now.minusDays(2).toInstant(ZoneOffset.UTC)));
        assertEquals(now.toInstant(ZoneOffset.UTC), result.getSnapshot().getEffective());
        assertEquals(now.plusDays(7).toInstant(ZoneOffset.UTC), result.getSnapshot().getExpires());
        verify(keyGenerator, times(2)).generateRandomKeyString(anyInt());
    }

    @Test
    void rotateSaltsRotateSaltsFromOldestBucketOnly() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(5),
                Duration.ofDays(4),
        };

        final Instant lastUpdated1 = now.minusDays(6).toInstant(ZoneOffset.UTC);
        final Instant lastUpdated2 = now.minusDays(4).toInstant(ZoneOffset.UTC);
        final Instant lastUpdated3 = now.minusDays(3).toInstant(ZoneOffset.UTC);
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
        assertEquals(now.toInstant(ZoneOffset.UTC), result.getSnapshot().getEffective());
        assertEquals(now.plusDays(7).toInstant(ZoneOffset.UTC), result.getSnapshot().getExpires());
        verify(keyGenerator, times(2)).generateRandomKeyString(anyInt());
    }

    @Test
    void rotateSaltsRotateSaltsFromNewerBucketOnly() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(5),
                Duration.ofDays(3),
        };

        final Instant lastUpdated1 = now.minusDays(4).toInstant(ZoneOffset.UTC);
        final Instant lastUpdated2 = now.minusDays(3).toInstant(ZoneOffset.UTC);
        final RotatingSaltProvider.SaltSnapshot lastSnapshot = SnapshotBuilder.start()
                .withEntries(3, lastUpdated1)
                .withEntries(7, lastUpdated2)
                .build(Instant.now(), Instant.now());

        final ISaltRotation.Result result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.2);
        assertTrue(result.hasSnapshot());
        assertEquals(2, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), result.getSnapshot().getEffective()));
        assertEquals(1, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), lastUpdated1));
        assertEquals(7, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), lastUpdated2));
        assertEquals(now.toInstant(ZoneOffset.UTC), result.getSnapshot().getEffective());
        assertEquals(now.plusDays(7).toInstant(ZoneOffset.UTC), result.getSnapshot().getExpires());
        verify(keyGenerator, times(2)).generateRandomKeyString(anyInt());
    }

    @Test
    void rotateSaltsRotateSaltsFromMultipleBuckets() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(5),
                Duration.ofDays(4),
        };

        final Instant lastUpdated1 = now.minusDays(6).toInstant(ZoneOffset.UTC);
        final Instant lastUpdated2 = now.minusDays(5).toInstant(ZoneOffset.UTC);
        final Instant lastUpdated3 = now.minusDays(4).toInstant(ZoneOffset.UTC);
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
        assertEquals(now.toInstant(ZoneOffset.UTC), result.getSnapshot().getEffective());
        assertEquals(now.plusDays(7).toInstant(ZoneOffset.UTC), result.getSnapshot().getExpires());
        verify(keyGenerator, times(5)).generateRandomKeyString(anyInt());
    }

    @Test
    void rotateSaltsRotateSaltsInsufficientOutdatedSalts() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(5),
                Duration.ofDays(3),
        };

        final Instant lastUpdated1 = now.minusDays(6).toInstant(ZoneOffset.UTC);
        final Instant lastUpdated2 = now.minusDays(4).toInstant(ZoneOffset.UTC);
        final Instant lastUpdated3 = now.minusDays(2).toInstant(ZoneOffset.UTC);
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
        assertEquals(now.toInstant(ZoneOffset.UTC), result.getSnapshot().getEffective());
        assertEquals(now.plusDays(7).toInstant(ZoneOffset.UTC), result.getSnapshot().getExpires());
        verify(keyGenerator, times(3)).generateRandomKeyString(anyInt());
    }
}
