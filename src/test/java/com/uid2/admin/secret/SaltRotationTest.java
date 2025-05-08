package com.uid2.admin.secret;

import com.uid2.shared.model.SaltEntry;
import com.uid2.shared.secret.IKeyGenerator;
import com.uid2.shared.store.salt.RotatingSaltProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SaltRotationTest {
    @Mock private IKeyGenerator keyGenerator;
    private SaltRotation saltRotation;

    private final LocalDate targetDate = LocalDate.of(2025, 1, 1);
    private final Instant targetDateAsInstant = targetDate.atStartOfDay().toInstant(ZoneOffset.UTC);

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);

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
                    effective, expires, entries.toArray(SaltEntry[]::new), "test_first_level_salt");
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
                .withEntries(10, targetDateAsInstant)
                .build(targetDateAsInstant,
                        targetDateAsInstant.plus(7, ChronoUnit.DAYS));

        final ISaltRotation.Result result1 = saltRotation.rotateSalts(lastSnapshot, minAges, 0.2, targetDate);
        assertFalse(result1.hasSnapshot());
        final ISaltRotation.Result result2 = saltRotation.rotateSalts(lastSnapshot, minAges, 0.2, targetDate.minusDays(1));
        assertFalse(result2.hasSnapshot());
    }

    @Test
    void rotateSaltsAllSaltsUpToDate() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(1),
                Duration.ofDays(2),
        };

        final RotatingSaltProvider.SaltSnapshot lastSnapshot = SnapshotBuilder.start()
                .withEntries(10, targetDateAsInstant)
                .build(targetDateAsInstant.minus(1, ChronoUnit.DAYS),
                        targetDateAsInstant.plus(6, ChronoUnit.DAYS));

        final ISaltRotation.Result result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.2, targetDate);
        assertFalse(result.hasSnapshot());
        verify(keyGenerator, times(0)).generateRandomKeyString(anyInt());
    }

    @Test
    void rotateSaltsAllSaltsOld() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(1),
                Duration.ofDays(2),
        };

        final Instant entryLastUpdated = targetDateAsInstant.minus(10, ChronoUnit.DAYS);
        final RotatingSaltProvider.SaltSnapshot lastSnapshot = SnapshotBuilder.start()
                .withEntries(10, entryLastUpdated)
                .build(targetDateAsInstant.minus(1, ChronoUnit.DAYS),
                        targetDateAsInstant.plus(6, ChronoUnit.DAYS));

        final ISaltRotation.Result result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.2, targetDate);
        assertTrue(result.hasSnapshot());
        assertEquals(2, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), result.getSnapshot().getEffective()));
        assertEquals(8, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), entryLastUpdated));
        assertEquals(targetDateAsInstant, result.getSnapshot().getEffective());
        assertEquals(targetDateAsInstant.plus(7, ChronoUnit.DAYS), result.getSnapshot().getExpires());
        verify(keyGenerator, times(2)).generateRandomKeyString(anyInt());
    }

    @Test
    void rotateSaltsRotateSaltsFromOldestBucketOnly() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(5),
                Duration.ofDays(4),
        };

        final Instant lastUpdated1 = targetDateAsInstant.minus(6, ChronoUnit.DAYS);
        final Instant lastUpdated2 = targetDateAsInstant.minus(5, ChronoUnit.DAYS);
        final Instant lastUpdated3 = targetDateAsInstant.minus(4, ChronoUnit.DAYS);
        final RotatingSaltProvider.SaltSnapshot lastSnapshot = SnapshotBuilder.start()
                .withEntries(3, lastUpdated1)
                .withEntries(5, lastUpdated2)
                .withEntries(2, lastUpdated3)
                .build(targetDateAsInstant.minus(1, ChronoUnit.DAYS),
                        targetDateAsInstant.plus(6, ChronoUnit.DAYS));

        final ISaltRotation.Result result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.2, targetDate);
        assertTrue(result.hasSnapshot());
        assertEquals(2, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), result.getSnapshot().getEffective()));
        assertEquals(1, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), lastUpdated1));
        assertEquals(5, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), lastUpdated2));
        assertEquals(2, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), lastUpdated3));
        assertEquals(targetDateAsInstant, result.getSnapshot().getEffective());
        assertEquals(targetDateAsInstant.plus(7, ChronoUnit.DAYS), result.getSnapshot().getExpires());
        verify(keyGenerator, times(2)).generateRandomKeyString(anyInt());
    }

    @Test
    void rotateSaltsRotateSaltsFromNewerBucketOnly() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(5),
                Duration.ofDays(3),
        };

        final Instant lastUpdated1 = targetDateAsInstant.minus(4, ChronoUnit.DAYS);
        final Instant lastUpdated2 = targetDateAsInstant.minus(3, ChronoUnit.DAYS);
        final RotatingSaltProvider.SaltSnapshot lastSnapshot = SnapshotBuilder.start()
                .withEntries(3, lastUpdated1)
                .withEntries(7, lastUpdated2)
                .build(targetDateAsInstant.minus(1, ChronoUnit.DAYS),
                        targetDateAsInstant.plus(6, ChronoUnit.DAYS));

        final ISaltRotation.Result result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.2, targetDate);
        assertTrue(result.hasSnapshot());
        assertEquals(2, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), result.getSnapshot().getEffective()));
        assertEquals(1, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), lastUpdated1));
        assertEquals(7, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), lastUpdated2));
        assertEquals(targetDateAsInstant, result.getSnapshot().getEffective());
        assertEquals(targetDateAsInstant.plus(7, ChronoUnit.DAYS), result.getSnapshot().getExpires());
        verify(keyGenerator, times(2)).generateRandomKeyString(anyInt());
    }

    @Test
    void rotateSaltsRotateSaltsFromMultipleBuckets() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(5),
                Duration.ofDays(4),
        };

        final Instant lastUpdated1 = targetDateAsInstant.minus(6, ChronoUnit.DAYS);
        final Instant lastUpdated2 = targetDateAsInstant.minus(5, ChronoUnit.DAYS);
        final Instant lastUpdated3 = targetDateAsInstant.minus(4, ChronoUnit.DAYS);
        final RotatingSaltProvider.SaltSnapshot lastSnapshot = SnapshotBuilder.start()
                .withEntries(3, lastUpdated1)
                .withEntries(5, lastUpdated2)
                .withEntries(2, lastUpdated3)
                .build(targetDateAsInstant.minus(1, ChronoUnit.DAYS),
                        targetDateAsInstant.plus(6, ChronoUnit.DAYS));

        final ISaltRotation.Result result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.45, targetDate);
        assertTrue(result.hasSnapshot());
        assertEquals(5, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), result.getSnapshot().getEffective()));
        assertEquals(0, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), lastUpdated1));
        assertEquals(3, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), lastUpdated2));
        assertEquals(2, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), lastUpdated3));
        assertEquals(targetDateAsInstant, result.getSnapshot().getEffective());
        assertEquals(targetDateAsInstant.plus(7, ChronoUnit.DAYS), result.getSnapshot().getExpires());
        verify(keyGenerator, times(5)).generateRandomKeyString(anyInt());
    }

    @Test
    void rotateSaltsRotateSaltsInsufficientOutdatedSalts() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(5),
                Duration.ofDays(3),
        };

        final Instant lastUpdated1 = targetDateAsInstant.minus(5, ChronoUnit.DAYS);
        final Instant lastUpdated2 = targetDateAsInstant.minus(4, ChronoUnit.DAYS);
        final Instant lastUpdated3 = targetDateAsInstant.minus(2, ChronoUnit.DAYS);
        final RotatingSaltProvider.SaltSnapshot lastSnapshot = SnapshotBuilder.start()
                .withEntries(1, lastUpdated1)
                .withEntries(2, lastUpdated2)
                .withEntries(7, lastUpdated3)
                .build(targetDateAsInstant.minus(1, ChronoUnit.DAYS),
                        targetDateAsInstant.plus(6, ChronoUnit.DAYS));

        final ISaltRotation.Result result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.45, targetDate);
        assertTrue(result.hasSnapshot());
        assertEquals(3, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), result.getSnapshot().getEffective()));
        assertEquals(0, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), lastUpdated1));
        assertEquals(0, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), lastUpdated2));
        assertEquals(7, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), lastUpdated3));
        assertEquals(targetDateAsInstant, result.getSnapshot().getEffective());
        assertEquals(targetDateAsInstant.plus(7, ChronoUnit.DAYS), result.getSnapshot().getExpires());
        verify(keyGenerator, times(3)).generateRandomKeyString(anyInt());
    }
}
