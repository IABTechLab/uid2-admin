package com.uid2.admin.secret;

import com.uid2.shared.model.SaltEntry;
import com.uid2.shared.secret.IKeyGenerator;
import com.uid2.shared.store.salt.RotatingSaltProvider;
import io.vertx.core.json.JsonObject;
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


    void setup(boolean enableRefreshFrom) {
        MockitoAnnotations.openMocks(this);

        JsonObject config = new JsonObject();
        config.put("enable_salt_rotation_refresh_from", enableRefreshFrom);

        saltRotation = new SaltRotation(config, keyGenerator);
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
        setup(false);
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
        setup(false);
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
        setup(false);
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
        setup(false);
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
        setup(false);
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
        setup(false);
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
        setup(false);
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
        setup(false);
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
    void rotateSaltsRotateSaltsOnRefreshFromDate() throws Exception {
        setup(true);
        final Duration[] minAges = {
                Duration.ofDays(90),
                Duration.ofDays(60),
        };

        var validForRotation1 = daysEarlier(120).toEpochMilli();
        var validForRotation2 = daysEarlier(70).toEpochMilli();
        var notValidForRotation = daysEarlier(30).toEpochMilli();
        var refreshNow = targetDateAsInstant.toEpochMilli();
        var refreshLater = daysLater(20).toEpochMilli();

        var lastSnapshot = SnapshotBuilder.start()
                .withEntries(
                    new SaltEntry(1, "1", validForRotation1, "salt", refreshNow, null, null, null),
                    new SaltEntry(2, "2", notValidForRotation, "salt", refreshNow, null, null, null),
                    new SaltEntry(3, "3", validForRotation2, "salt", refreshLater, null, null, null)
                )
                .build(daysEarlier(1), daysLater(6));

        var result = saltRotation.rotateSalts(lastSnapshot, minAges, 1, targetDate);
        assertTrue(result.hasSnapshot());

        var salts = result.getSnapshot().getAllRotatingSalts();

        assertEquals(targetDateAsInstant.toEpochMilli(), salts[0].lastUpdated());
        assertEquals(daysLater(30).toEpochMilli(), salts[0].refreshFrom());

        assertEquals(notValidForRotation, salts[1].lastUpdated());
        assertEquals(daysLater(30).toEpochMilli(), salts[1].refreshFrom());

        assertEquals(validForRotation2, salts[2].lastUpdated());
        assertEquals(refreshLater, salts[2].refreshFrom());
    }
}
