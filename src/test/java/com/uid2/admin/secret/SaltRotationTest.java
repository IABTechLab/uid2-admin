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

    private final LocalDate targetDate = LocalDate.of(2025, 1, 1);
    private final Instant targetDateAsInstant = targetDate.atStartOfDay().toInstant(ZoneOffset.UTC);

    private Instant daysEarlier(int days) {
        return targetDateAsInstant.minus(days, DAYS);
    }

    private Instant daysLater(int days) {
        return targetDateAsInstant.plus(days, DAYS);
    }

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
                .build(targetDateAsInstant, daysLater(7));

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
                .build(daysEarlier(1), daysLater(6));

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
                .withEntries(10, daysEarlier(10))
                .build(daysEarlier(1), daysLater(6));

        var result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.2, targetDate);
        assertTrue(result.hasSnapshot());
        assertEquals(2, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), result.getSnapshot().getEffective()));
        assertEquals(8, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), daysEarlier(10)));
        assertEquals(targetDateAsInstant, result.getSnapshot().getEffective());
        assertEquals(daysLater(7), result.getSnapshot().getExpires());
        verify(keyGenerator, times(2)).generateRandomKeyString(anyInt());
    }

    @Test
    void rotateSaltsRotateSaltsFromOldestBucketOnly() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(5),
                Duration.ofDays(4),
        };

        var lastSnapshot = SnapshotBuilder.start()
                .withEntries(3, daysEarlier(6))
                .withEntries(5, daysEarlier(5))
                .withEntries(2, daysEarlier(4))
                .build(daysEarlier(1), daysLater(6));

        var result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.2, targetDate);
        assertTrue(result.hasSnapshot());
        var salts = result.getSnapshot().getAllRotatingSalts();
        assertEquals(2, countEntriesWithLastUpdated(salts, result.getSnapshot().getEffective()));
        assertEquals(1, countEntriesWithLastUpdated(salts, daysEarlier(6)));
        assertEquals(5, countEntriesWithLastUpdated(salts, daysEarlier(5)));
        assertEquals(2, countEntriesWithLastUpdated(salts, daysEarlier(4)));
        assertEquals(targetDateAsInstant, result.getSnapshot().getEffective());
        assertEquals(daysLater(7), result.getSnapshot().getExpires());
        verify(keyGenerator, times(2)).generateRandomKeyString(anyInt());
    }

    @Test
    void rotateSaltsRotateSaltsFromNewerBucketOnly() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(5),
                Duration.ofDays(3),
        };

        var lastSnapshot = SnapshotBuilder.start()
                .withEntries(3, daysEarlier(4))
                .withEntries(7, daysEarlier(3))
                .build(daysEarlier(1), daysLater(6));

        var result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.2, targetDate);
        assertTrue(result.hasSnapshot());
        var salts = result.getSnapshot().getAllRotatingSalts();
        assertEquals(2, countEntriesWithLastUpdated(salts, result.getSnapshot().getEffective()));
        assertEquals(1, countEntriesWithLastUpdated(salts, daysEarlier(4)));
        assertEquals(7, countEntriesWithLastUpdated(salts, daysEarlier(3)));
        assertEquals(targetDateAsInstant, result.getSnapshot().getEffective());
        assertEquals(daysLater(7), result.getSnapshot().getExpires());
        verify(keyGenerator, times(2)).generateRandomKeyString(anyInt());
    }

    @Test
    void rotateSaltsRotateSaltsFromMultipleBuckets() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(5),
                Duration.ofDays(4),
        };

        var lastSnapshot = SnapshotBuilder.start()
                .withEntries(3, daysEarlier(6))
                .withEntries(5, daysEarlier(5))
                .withEntries(2, daysEarlier(4))
                .build(daysEarlier(1), daysLater(6));

        var result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.45, targetDate);
        assertTrue(result.hasSnapshot());
        var salts = result.getSnapshot().getAllRotatingSalts();
        assertEquals(5, countEntriesWithLastUpdated(salts, result.getSnapshot().getEffective()));
        assertEquals(0, countEntriesWithLastUpdated(salts, daysEarlier(6)));
        assertEquals(3, countEntriesWithLastUpdated(salts, daysEarlier(5)));
        assertEquals(2, countEntriesWithLastUpdated(salts, daysEarlier(4)));
        assertEquals(targetDateAsInstant, result.getSnapshot().getEffective());
        assertEquals(daysLater(7), result.getSnapshot().getExpires());
        verify(keyGenerator, times(5)).generateRandomKeyString(anyInt());
    }

    @Test
    void rotateSaltsRotateSaltsInsufficientOutdatedSalts() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(5),
                Duration.ofDays(3),
        };

        var lastSnapshot = SnapshotBuilder.start()
                .withEntries(1, daysEarlier(5))
                .withEntries(2, daysEarlier(4))
                .withEntries(7, daysEarlier(2))
                .build(daysEarlier(1), daysLater(6));

        var result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.45, targetDate);
        assertTrue(result.hasSnapshot());
        var salts = result.getSnapshot().getAllRotatingSalts();
        assertEquals(3, countEntriesWithLastUpdated(salts, result.getSnapshot().getEffective()));
        assertEquals(0, countEntriesWithLastUpdated(salts, daysEarlier(5)));
        assertEquals(0, countEntriesWithLastUpdated(salts, daysEarlier(4)));
        assertEquals(7, countEntriesWithLastUpdated(salts, daysEarlier(2)));
        assertEquals(targetDateAsInstant, result.getSnapshot().getEffective());
        assertEquals(daysLater(7), result.getSnapshot().getExpires());
        verify(keyGenerator, times(3)).generateRandomKeyString(anyInt());
    }

    @ParameterizedTest
    @CsvSource({
            "5, 30", // Soon after rotation, use 30 days post rotation
            "40, 60", // >30 days after rotation use the next increment of 30 days
            "60, 90", // Exactly at multiple of 30 days post rotation, use next increment of 30 days
    })
    void testRefreshFromCalculation(int lastRotationDaysAgo, int refreshFromDaysFromRotation) throws Exception {
        var lastRotation = daysEarlier(lastRotationDaysAgo);
        var lastSnapshot = SnapshotBuilder.start()
                .withEntries(new SaltEntry(1, "1", lastRotation.toEpochMilli(), "salt1", 100L, null, null, null))
                .build(daysEarlier(1), daysLater(6));

        var result = saltRotation.rotateSalts(lastSnapshot, new Duration[]{ Duration.ofDays(1) }, 0.45, targetDate);
        var actual = result.getSnapshot().getAllRotatingSalts()[0];

        var expected = lastRotation.plus(refreshFromDaysFromRotation, DAYS).toEpochMilli();

        assertThat(actual.refreshFrom()).isEqualTo(expected);
    }

    @Test
    void rotateSaltsPopulatePreviousSalt() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(5),
        };

        final long validForRotation = daysEarlier(8).toEpochMilli();
        final Instant notValidForRotation = daysEarlier(2);
        final RotatingSaltProvider.SaltSnapshot lastSnapshot = SnapshotBuilder.start()
                .withEntries(
                    new SaltEntry(1, "1", validForRotation, "salt1", null, null, null, null),
                    new SaltEntry(1, "1", validForRotation, "salt2", null, null, null, null),
                    new SaltEntry(1, "1", validForRotation, "salt3", null, null, null, null)
                )
                .withEntries(7, notValidForRotation)
                .build(daysEarlier(1), daysLater(6));

        final SaltRotation.Result result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.3, targetDate);
        assertTrue(result.hasSnapshot());
        assertEquals("salt1", result.getSnapshot().getAllRotatingSalts()[0].previousSalt());
        assertEquals("salt2", result.getSnapshot().getAllRotatingSalts()[1].previousSalt());
        assertEquals("salt3", result.getSnapshot().getAllRotatingSalts()[2].previousSalt());
        assertEquals(7, Arrays.stream(result.getSnapshot().getAllRotatingSalts()).filter(s -> s.previousSalt() == null).count());

        verify(keyGenerator, times(3)).generateRandomKeyString(anyInt());
    }

    @Test
    void rotateSaltsRemovePreviousSaltIfOver90DaysOld() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(90),
                Duration.ofDays(60),
                Duration.ofDays(40),
        };

        final long lessThan90Days = daysEarlier(50).toEpochMilli();
        final long is90DaysOld = daysEarlier(90).toEpochMilli();
        final long over90Days = daysEarlier(100).toEpochMilli();
        final RotatingSaltProvider.SaltSnapshot lastSnapshot = SnapshotBuilder.start()
                .withEntries(
                    new SaltEntry(1, "1", lessThan90Days, "currentSalt", null, "lessThan90DaysOld", null, null),
                    new SaltEntry(2, "2", lessThan90Days, "currentSalt", null, "lessThan90DaysOld", null, null),
                    new SaltEntry(3, "3", is90DaysOld, "currentSalt", null, "90DaysOld", null, null),
                    new SaltEntry(4, "4", is90DaysOld, "currentSalt", null, "90DaysOld", null, null),
                    new SaltEntry(5, "5", over90Days, "currentSalt", null, null, null, null),
                    new SaltEntry(6, "6", over90Days, "currentSalt", null, null, null, null)
                )
                .build(daysEarlier(1), daysLater(6));

        final SaltRotation.Result result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.5, targetDate);
        assertTrue(result.hasSnapshot());
        assertEquals(2, Arrays.stream(result.getSnapshot().getAllRotatingSalts()).filter(s -> "lessThan90DaysOld".equals(s.previousSalt())).count());
        assertEquals(1, Arrays.stream(result.getSnapshot().getAllRotatingSalts()).filter(s -> s.previousSalt() == null).count());
        assertEquals(3, Arrays.stream(result.getSnapshot().getAllRotatingSalts()).filter(s -> "currentSalt".equals(s.previousSalt())).count());

        verify(keyGenerator, times(3)).generateRandomKeyString(anyInt());
    }

}
