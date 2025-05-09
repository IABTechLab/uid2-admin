package com.uid2.admin.secret;

import com.uid2.shared.model.SaltEntry;
import com.uid2.shared.secret.IKeyGenerator;
import com.uid2.shared.store.salt.RotatingSaltProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.*;
import java.util.*;

import static java.time.temporal.ChronoUnit.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SaltRotationTest {
    @Mock private IKeyGenerator keyGenerator;
    private SaltRotation saltRotation;

    private static final LocalDate targetDate = LocalDate.of(2025, 1, 1);
    private static final Instant targetDateAsInstant = targetDate.atStartOfDay().toInstant(ZoneOffset.UTC);

    private static final Instant oneDayEarlier = targetDateAsInstant.minus(1, DAYS);
    private static final Instant twoDaysEarlier = targetDateAsInstant.minus(2, DAYS);
    private static final Instant threeDaysEarlier = targetDateAsInstant.minus(3, DAYS);
    private static final Instant fourDaysEarlier = targetDateAsInstant.minus(4, DAYS);
    private static final Instant fiveDaysEarlier = targetDateAsInstant.minus(5, DAYS);
    private static final Instant sixDaysEarlier = targetDateAsInstant.minus(6, DAYS);
    private static final Instant tenDaysEarlier = targetDateAsInstant.minus(10, DAYS);
    private static final Instant sixDaysLater = targetDateAsInstant.plus(6, DAYS);
    private static final Instant sevenDaysLater = targetDateAsInstant.plus(7, DAYS);

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);

        saltRotation = new SaltRotation(keyGenerator);
    }

    private static class SnapshotBuilder {
        private final List<SaltEntry> entries = new ArrayList<>();

        private SnapshotBuilder() {}

        public static SnapshotBuilder start() { return new SnapshotBuilder(); }

        public SnapshotBuilder withEntries(int count, Instant lastUpdated) {
            for (int i = 0; i < count; ++i) {
                entries.add(new SaltEntry(entries.size(), "h", lastUpdated.toEpochMilli(), "salt" + entries.size(), null, null, null, null));
            }
            return this;
        }

        public SnapshotBuilder withEntries(SaltEntry... salts) {
            Collections.addAll(this.entries, salts);
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

    @Test
    void rotateSaltsLastSnapshotIsUpToDate() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(1),
                Duration.ofDays(2),
        };
        var lastSnapshot = SnapshotBuilder.start()
                .withEntries(10, targetDateAsInstant)
                .build(targetDateAsInstant, sevenDaysLater);

        var result1 = saltRotation.rotateSalts(lastSnapshot, minAges, 0.2, targetDate);
        assertFalse(result1.hasSnapshot());
        var result2 = saltRotation.rotateSalts(lastSnapshot, minAges, 0.2, targetDate.minusDays(1));
        assertFalse(result2.hasSnapshot());
    }

    @Test
    void rotateSaltsAllSaltsUpToDate() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(1),
                Duration.ofDays(2),
        };

        var lastSnapshot = SnapshotBuilder.start()
                .withEntries(10, targetDateAsInstant)
                .build(oneDayEarlier, sixDaysLater);

        var result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.2, targetDate);
        assertFalse(result.hasSnapshot());
        verify(keyGenerator, times(0)).generateRandomKeyString(anyInt());
    }

    @Test
    void rotateSaltsAllSaltsOld() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(1),
                Duration.ofDays(2),
        };

        var lastSnapshot = SnapshotBuilder.start()
                .withEntries(10, tenDaysEarlier)
                .build(oneDayEarlier, sixDaysLater);

        var result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.2, targetDate);
        assertTrue(result.hasSnapshot());
        assertEquals(2, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), result.getSnapshot().getEffective()));
        assertEquals(8, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), tenDaysEarlier));
        assertEquals(targetDateAsInstant, result.getSnapshot().getEffective());
        assertEquals(sevenDaysLater, result.getSnapshot().getExpires());
        verify(keyGenerator, times(2)).generateRandomKeyString(anyInt());
    }

    @Test
    void rotateSaltsRotateSaltsFromOldestBucketOnly() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(5),
                Duration.ofDays(4),
        };

        var lastSnapshot = SnapshotBuilder.start()
                .withEntries(3, sixDaysEarlier)
                .withEntries(5, fiveDaysEarlier)
                .withEntries(2, fourDaysEarlier)
                .build(targetDateAsInstant.minus(1, DAYS),
                        targetDateAsInstant.plus(6, DAYS));

        var result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.2, targetDate);
        assertTrue(result.hasSnapshot());
        var salts = result.getSnapshot().getAllRotatingSalts();
        assertEquals(2, countEntriesWithLastUpdated(salts, result.getSnapshot().getEffective()));
        assertEquals(1, countEntriesWithLastUpdated(salts, sixDaysEarlier));
        assertEquals(5, countEntriesWithLastUpdated(salts, fiveDaysEarlier));
        assertEquals(2, countEntriesWithLastUpdated(salts, fourDaysEarlier));
        assertEquals(targetDateAsInstant, result.getSnapshot().getEffective());
        assertEquals(sevenDaysLater, result.getSnapshot().getExpires());
        verify(keyGenerator, times(2)).generateRandomKeyString(anyInt());
    }

    @Test
    void rotateSaltsRotateSaltsFromNewerBucketOnly() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(5),
                Duration.ofDays(3),
        };

        var lastSnapshot = SnapshotBuilder.start()
                .withEntries(3, fourDaysEarlier)
                .withEntries(7, threeDaysEarlier)
                .build(oneDayEarlier, sixDaysLater);

        var result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.2, targetDate);
        assertTrue(result.hasSnapshot());
        var salts = result.getSnapshot().getAllRotatingSalts();
        assertEquals(2, countEntriesWithLastUpdated(salts, result.getSnapshot().getEffective()));
        assertEquals(1, countEntriesWithLastUpdated(salts, fourDaysEarlier));
        assertEquals(7, countEntriesWithLastUpdated(salts, threeDaysEarlier));
        assertEquals(targetDateAsInstant, result.getSnapshot().getEffective());
        assertEquals(sevenDaysLater, result.getSnapshot().getExpires());
        verify(keyGenerator, times(2)).generateRandomKeyString(anyInt());
    }

    @Test
    void rotateSaltsRotateSaltsFromMultipleBuckets() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(5),
                Duration.ofDays(4),
        };

        var lastSnapshot = SnapshotBuilder.start()
                .withEntries(3, sixDaysEarlier)
                .withEntries(5, fiveDaysEarlier)
                .withEntries(2, fourDaysEarlier)
                .build(oneDayEarlier, sixDaysLater);

        var result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.45, targetDate);
        assertTrue(result.hasSnapshot());
        var salts = result.getSnapshot().getAllRotatingSalts();
        assertEquals(5, countEntriesWithLastUpdated(salts, result.getSnapshot().getEffective()));
        assertEquals(0, countEntriesWithLastUpdated(salts, sixDaysEarlier));
        assertEquals(3, countEntriesWithLastUpdated(salts, fiveDaysEarlier));
        assertEquals(2, countEntriesWithLastUpdated(salts, fourDaysEarlier));
        assertEquals(targetDateAsInstant, result.getSnapshot().getEffective());
        assertEquals(sevenDaysLater, result.getSnapshot().getExpires());
        verify(keyGenerator, times(5)).generateRandomKeyString(anyInt());
    }

    @Test
    void rotateSaltsRotateSaltsInsufficientOutdatedSalts() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(5),
                Duration.ofDays(3),
        };

        var lastSnapshot = SnapshotBuilder.start()
                .withEntries(1, fiveDaysEarlier)
                .withEntries(2, fourDaysEarlier)
                .withEntries(7, twoDaysEarlier)
                .build(oneDayEarlier, sixDaysLater);

        var result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.45, targetDate);
        assertTrue(result.hasSnapshot());
        var salts = result.getSnapshot().getAllRotatingSalts();
        assertEquals(3, countEntriesWithLastUpdated(salts, result.getSnapshot().getEffective()));
        assertEquals(0, countEntriesWithLastUpdated(salts, fiveDaysEarlier));
        assertEquals(0, countEntriesWithLastUpdated(salts, fourDaysEarlier));
        assertEquals(7, countEntriesWithLastUpdated(salts, twoDaysEarlier));
        assertEquals(targetDateAsInstant, result.getSnapshot().getEffective());
        assertEquals(sevenDaysLater, result.getSnapshot().getExpires());
        verify(keyGenerator, times(3)).generateRandomKeyString(anyInt());
    }

    @ParameterizedTest
    @CsvSource({
            "5, 30", // Soon after rotation, use 30 days post rotation
            "40, 60", // >30 days after rotation use the next increment of 30 days
            "60, 90", // Exactly at multiple of 30 days post rotation, use next increment of 30 days
    })
    void testRefreshFromCalculation(int lastRotationDaysAgo, int refreshFromDaysFromRotation) throws Exception {
        var lastRotation = targetDateAsInstant.minus(lastRotationDaysAgo, DAYS);
        var lastSnapshot = SnapshotBuilder.start()
                .withEntries(new SaltEntry(1, "1", lastRotation.toEpochMilli(), "salt1", 100L, null, null, null))
                .build(oneDayEarlier, sixDaysLater);

        var result = saltRotation.rotateSalts(lastSnapshot, new Duration[]{ Duration.ofDays(1) }, 0.45, targetDate);
        var actual = result.getSnapshot().getAllRotatingSalts()[0];

        var expected = lastRotation.plus(refreshFromDaysFromRotation, DAYS).toEpochMilli();

        assertThat(actual.refreshFrom()).isEqualTo(expected);
    }
}
