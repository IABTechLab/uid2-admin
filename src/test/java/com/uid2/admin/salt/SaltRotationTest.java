package com.uid2.admin.salt;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.uid2.admin.AdminConst;
import com.uid2.admin.salt.helper.SaltBuilder;
import com.uid2.admin.salt.helper.SaltSnapshotBuilder;
import com.uid2.shared.model.SaltEntry;
import com.uid2.shared.secret.IKeyGenerator;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

import static com.uid2.admin.salt.helper.TargetDateUtil.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

public class SaltRotationTest {
    @Mock
    private IKeyGenerator keyGenerator;
    private SaltRotation saltRotation;

    private ListAppender<ILoggingEvent> appender;
    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);

        appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(SaltRotation.class)).addAppender(appender);

        JsonObject config = new JsonObject();
        saltRotation = new SaltRotation(config, keyGenerator);
    }

    @AfterEach
    void tearDown() throws Exception {
        appender.stop();
        mocks.close();
    }

    @Test
    void rotateSaltsLastSnapshotIsUpToDate() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(1),
                Duration.ofDays(2),
        };
        var lastSnapshot = SaltSnapshotBuilder.start()
                .entries(10, targetDate())
                .effective(targetDate())
                .expires(daysLater(7))
                .build();

        var result1 = saltRotation.rotateSalts(lastSnapshot, minAges, 0.2, targetDate());
        assertFalse(result1.hasSnapshot());
        var result2 = saltRotation.rotateSalts(lastSnapshot, minAges, 0.2, targetDate().minusDays(1));
        assertFalse(result2.hasSnapshot());
    }

    @Test
    void rotateSaltsAllSaltsUpToDate() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(1),
                Duration.ofDays(2),
        };

        var lastSnapshot = SaltSnapshotBuilder.start()
                .entries(10, targetDate())
                .build();

        var result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.2, targetDate());
        assertFalse(result.hasSnapshot());
        verify(keyGenerator, times(0)).generateRandomKeyString(anyInt());
    }

    @Test
    void rotateSaltsAllSaltsOld() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(1),
                Duration.ofDays(2),
        };

        var lastSnapshot = SaltSnapshotBuilder.start()
                .entries(10, daysEarlier(10))
                .build();

        var result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.2, targetDate());
        assertTrue(result.hasSnapshot());
        assertEquals(2, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), result.getSnapshot().getEffective()));
        assertEquals(8, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), daysEarlier(10)));
        assertEquals(targetDate().asInstant(), result.getSnapshot().getEffective());
        assertEquals(daysLater(7).asInstant(), result.getSnapshot().getExpires());
        verify(keyGenerator, times(2)).generateRandomKeyString(anyInt());
    }

    @Test
    void rotateSaltsRotateSaltsFromOldestBucketOnly() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(5),
                Duration.ofDays(4),
        };

        var lastSnapshot = SaltSnapshotBuilder.start()
                .entries(3, daysEarlier(6))
                .entries(5, daysEarlier(5))
                .entries(2, daysEarlier(4))
                .build();

        var result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.2, targetDate());
        assertTrue(result.hasSnapshot());
        var salts = result.getSnapshot().getAllRotatingSalts();
        assertEquals(2, countEntriesWithLastUpdated(salts, result.getSnapshot().getEffective()));
        assertEquals(1, countEntriesWithLastUpdated(salts, daysEarlier(6)));
        assertEquals(5, countEntriesWithLastUpdated(salts, daysEarlier(5)));
        assertEquals(2, countEntriesWithLastUpdated(salts, daysEarlier(4)));
        assertEquals(targetDate().asInstant(), result.getSnapshot().getEffective());
        assertEquals(daysLater(7).asInstant(), result.getSnapshot().getExpires());
        verify(keyGenerator, times(2)).generateRandomKeyString(anyInt());
    }

    @Test
    void rotateSaltsRotateSaltsFromNewerBucketOnly() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(5),
                Duration.ofDays(3),
        };

        var lastSnapshot = SaltSnapshotBuilder.start()
                .entries(3, daysEarlier(4))
                .entries(7, daysEarlier(3))
                .build();

        var result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.2, targetDate());
        assertTrue(result.hasSnapshot());
        var salts = result.getSnapshot().getAllRotatingSalts();
        assertEquals(2, countEntriesWithLastUpdated(salts, result.getSnapshot().getEffective()));
        assertEquals(1, countEntriesWithLastUpdated(salts, daysEarlier(4)));
        assertEquals(7, countEntriesWithLastUpdated(salts, daysEarlier(3)));
        assertEquals(targetDate().asInstant(), result.getSnapshot().getEffective());
        assertEquals(daysLater(7).asInstant(), result.getSnapshot().getExpires());
        verify(keyGenerator, times(2)).generateRandomKeyString(anyInt());
    }

    @Test
    void rotateSaltsRotateSaltsFromMultipleBuckets() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(5),
                Duration.ofDays(4),
        };

        var lastSnapshot = SaltSnapshotBuilder.start()
                .entries(3, daysEarlier(6))
                .entries(5, daysEarlier(5))
                .entries(2, daysEarlier(4))
                .build();

        var result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.45, targetDate());
        assertTrue(result.hasSnapshot());
        var salts = result.getSnapshot().getAllRotatingSalts();
        assertEquals(5, countEntriesWithLastUpdated(salts, result.getSnapshot().getEffective()));
        assertEquals(0, countEntriesWithLastUpdated(salts, daysEarlier(6)));
        assertEquals(3, countEntriesWithLastUpdated(salts, daysEarlier(5)));
        assertEquals(2, countEntriesWithLastUpdated(salts, daysEarlier(4)));
        assertEquals(targetDate().asInstant(), result.getSnapshot().getEffective());
        assertEquals(daysLater(7).asInstant(), result.getSnapshot().getExpires());
        verify(keyGenerator, times(5)).generateRandomKeyString(anyInt());
    }

    @Test
    void rotateSaltsRotateSaltsInsufficientOutdatedSalts() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(5),
                Duration.ofDays(3),
        };

        var lastSnapshot = SaltSnapshotBuilder.start()
                .entries(1, daysEarlier(5))
                .entries(2, daysEarlier(4))
                .entries(7, daysEarlier(2))
                .build();

        var result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.45, targetDate());
        assertTrue(result.hasSnapshot());
        var salts = result.getSnapshot().getAllRotatingSalts();
        assertEquals(3, countEntriesWithLastUpdated(salts, result.getSnapshot().getEffective()));
        assertEquals(0, countEntriesWithLastUpdated(salts, daysEarlier(5)));
        assertEquals(0, countEntriesWithLastUpdated(salts, daysEarlier(4)));
        assertEquals(7, countEntriesWithLastUpdated(salts, daysEarlier(2)));
        assertEquals(targetDate().asInstant(), result.getSnapshot().getEffective());
        assertEquals(daysLater(7).asInstant(), result.getSnapshot().getExpires());
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
        SaltBuilder saltBuilder = SaltBuilder.start().lastUpdated(lastRotation);
        var lastSnapshot = SaltSnapshotBuilder.start()
                .entries(saltBuilder)
                .build();

        var result = saltRotation.rotateSalts(lastSnapshot, new Duration[]{Duration.ofDays(1)}, 0.45, targetDate());
        var actual = result.getSnapshot().getAllRotatingSalts()[0];

        var expected = lastRotation.plusDays(refreshFromDaysFromRotation).asEpochMs();

        assertThat(actual.refreshFrom()).isEqualTo(expected);
    }

    @Test
    void rotateSaltsPopulatePreviousSaltsOnRotation() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(90),
                Duration.ofDays(60),
                Duration.ofDays(30)
        };

        var lessThan90Days = daysEarlier(60);
        var exactly90Days = daysEarlier(90);
        var over90Days = daysEarlier(120);
        var lastSnapshot = SaltSnapshotBuilder.start()
                .entries(
                        SaltBuilder.start().lastUpdated(lessThan90Days).currentSalt("salt1"),
                        SaltBuilder.start().lastUpdated(exactly90Days).currentSalt("salt2"),
                        SaltBuilder.start().lastUpdated(over90Days).currentSalt("salt3")
                )
                .build();

        var result = saltRotation.rotateSalts(lastSnapshot, minAges, 1, targetDate());
        assertTrue(result.hasSnapshot());

        var salts = result.getSnapshot().getAllRotatingSalts();
        assertEquals("salt1", salts[0].previousSalt());
        assertEquals("salt2", salts[1].previousSalt());
        assertEquals("salt3", salts[2].previousSalt());
    }

    @Test
    void rotateSaltsPreservePreviousSaltsLessThan90DaysOld() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(60),
        };

        var notValidForRotation1 = daysEarlier(40);
        var notValidForRotation2 = daysEarlier(50);
        var validForRotation = daysEarlier(70);
        var lastSnapshot = SaltSnapshotBuilder.start()
                .entries(
                        SaltBuilder.start().lastUpdated(notValidForRotation1).currentSalt("salt1").previousSalt("previousSalt1"),
                        SaltBuilder.start().lastUpdated(notValidForRotation2).currentSalt("salt2")
                )
                .entries(1, validForRotation)
                .build();

        var result = saltRotation.rotateSalts(lastSnapshot, minAges, 1, targetDate());
        assertTrue(result.hasSnapshot());

        var salts = result.getSnapshot().getAllRotatingSalts();
        assertEquals("previousSalt1", salts[0].previousSalt());
        assertNull(salts[1].previousSalt());
    }

    @Test
    void rotateSaltsRemovePreviousSaltsOver90DaysOld() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(100),
        };

        var exactly90Days = daysEarlier(90);
        var over90Days = daysEarlier(100);
        var validForRotation = daysEarlier(120);
        var lastSnapshot = SaltSnapshotBuilder.start()
                .entries(
                        SaltBuilder.start().lastUpdated(exactly90Days).previousSalt("90DaysOld"),
                        SaltBuilder.start().lastUpdated(over90Days).previousSalt("over90DaysOld")
                )
                .entries(1, validForRotation)
                .build();

        var result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.5, targetDate());
        assertTrue(result.hasSnapshot());

        var salts = result.getSnapshot().getAllRotatingSalts();
        assertNull(salts[0].previousSalt());
        assertNull(salts[1].previousSalt());
    }


    @Test
    void rotateSaltsRotateWhenRefreshFromIsTargetDate() throws Exception {
        JsonObject config = new JsonObject();
        config.put(AdminConst.ENABLE_SALT_ROTATION_REFRESH_FROM, Boolean.TRUE);
        saltRotation = new SaltRotation(config, keyGenerator);

        final Duration[] minAges = {
                Duration.ofDays(90),
                Duration.ofDays(60),
        };

        var validForRotation1 = daysEarlier(120);
        var validForRotation2 = daysEarlier(70);
        var notValidForRotation = daysEarlier(30);
        var refreshNow = targetDate();
        var refreshLater = daysLater(20);

        var lastSnapshot = SaltSnapshotBuilder.start()
                .entries(
                        SaltBuilder.start().lastUpdated(validForRotation1).refreshFrom(refreshNow),
                        SaltBuilder.start().lastUpdated(notValidForRotation).refreshFrom(refreshNow),
                        SaltBuilder.start().lastUpdated(validForRotation2).refreshFrom(refreshLater)
                )
                .build();

        var result = saltRotation.rotateSalts(lastSnapshot, minAges, 1, targetDate());
        assertTrue(result.hasSnapshot());

        var salts = result.getSnapshot().getAllRotatingSalts();

        assertEquals(targetDate().asEpochMs(), salts[0].lastUpdated());
        assertEquals(daysLater(30).asEpochMs(), salts[0].refreshFrom());

        assertEquals(notValidForRotation.asEpochMs(), salts[1].lastUpdated());
        assertEquals(daysLater(30).asEpochMs(), salts[1].refreshFrom());

        assertEquals(validForRotation2.asEpochMs(), salts[2].lastUpdated());
        assertEquals(refreshLater.asEpochMs(), salts[2].refreshFrom());
    }

    @Test
    void logsSaltAgesOnRotation() throws Exception {
        JsonObject config = new JsonObject();
        config.put(AdminConst.ENABLE_SALT_ROTATION_REFRESH_FROM, Boolean.TRUE);
        saltRotation = new SaltRotation(config, keyGenerator);

        var lastSnapshot = SaltSnapshotBuilder.start()
                .entries(
                        // 5 salts total, 3 refreshable, 2 rotated given 40% fraction
                        SaltBuilder.start().lastUpdated(daysEarlier(65)).refreshFrom(targetDate()), // Refreshable, old enough, rotated
                        SaltBuilder.start().lastUpdated(daysEarlier(5)).refreshFrom(targetDate()), // Refreshable, too new
                        SaltBuilder.start().lastUpdated(daysEarlier(50)).refreshFrom(daysLater(1)), // Not refreshable, old enough
                        SaltBuilder.start().lastUpdated(daysEarlier(65)).refreshFrom(targetDate()), // Refreshable, old enough, rotated
                        SaltBuilder.start().lastUpdated(daysEarlier(10)).refreshFrom(daysLater(10)) // Not refreshable, too new
                )
                .build();

        var expected = Set.of(
                "[INFO] Salt rotation complete, target_date=2025-01-01 salts_rotated=2 total_salts=5",
                // Post-rotation ages, we want to look at current state
                "[INFO] salt_count_type=total-salts target_date=2025-01-01 age=0 salt_count=2", // The two rotated salts, used to be 65 and 50 days old
                "[INFO] salt_count_type=total-salts target_date=2025-01-01 age=5 salt_count=1",
                "[INFO] salt_count_type=total-salts target_date=2025-01-01 age=10 salt_count=1",
                "[INFO] salt_count_type=total-salts target_date=2025-01-01 age=50 salt_count=1",

                // Pre-rotation ages, we want to see at which ages salts become refreshable, post rotation some will be 0
                "[INFO] salt_count_type=refreshable-salts target_date=2025-01-01 age=5 salt_count=1",
                "[INFO] salt_count_type=refreshable-salts target_date=2025-01-01 age=65 salt_count=2",

                // Pre-rotation ages, post rotation they will all have age 0
                "[INFO] salt_count_type=rotated-salts target_date=2025-01-01 age=65 salt_count=2"
        );

        var minAges = new Duration[]{Duration.ofDays(30), Duration.ofDays(60)};
        saltRotation.rotateSalts(lastSnapshot, minAges, 0.4, targetDate());

        var actual = appender.list.stream().map(Object::toString).collect(Collectors.toSet());
        assertThat(actual).isEqualTo(expected);
    }

    private int countEntriesWithLastUpdated(SaltEntry[] entries, TargetDate lastUpdated) {
        return countEntriesWithLastUpdated(entries, lastUpdated.asInstant());
    }

    private int countEntriesWithLastUpdated(SaltEntry[] entries, Instant lastUpdated) {
        return (int) Arrays.stream(entries).filter(e -> e.lastUpdated() == lastUpdated.toEpochMilli()).count();
    }

}
