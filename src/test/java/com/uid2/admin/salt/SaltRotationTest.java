package com.uid2.admin.salt;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.uid2.admin.AdminConst;
import com.uid2.admin.salt.helper.SaltBuilder;
import com.uid2.admin.salt.helper.SaltSnapshotBuilder;
import com.uid2.shared.model.SaltEntry;
import com.uid2.shared.secret.IKeyGenerator;
import com.uid2.shared.secret.SecureKeyGenerator;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.uid2.admin.salt.helper.TargetDateUtil.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

class SaltRotationTest {
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

        saltRotation = new SaltRotation(keyGenerator, new JsonObject());
    }

    @AfterEach
    void teardown() throws Exception {
        appender.stop();
        mocks.close();
    }

    @Test
    void testRotateSaltsLastSnapshotIsUpToDate() throws Exception {
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
    void testRotateSaltsAllSaltsUpToDate() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(1),
                Duration.ofDays(2),
        };

        var lastSnapshot = SaltSnapshotBuilder.start()
                .entries(10, targetDate(), targetDate())
                .build();

        var result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.2, targetDate());
        assertFalse(result.hasSnapshot());
        verify(keyGenerator, times(0)).generateRandomKeyString(anyInt());
    }

    @Test
    void testRotateSaltsAllSaltsOld() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(1),
                Duration.ofDays(2),
        };

        var lastSnapshot = SaltSnapshotBuilder.start()
                .entries(10, daysEarlier(10), targetDate())
                .build();

        var result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.2, targetDate());
        assertTrue(result.hasSnapshot());
        assertEquals(1, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), result.getSnapshot().getEffective()));
        assertEquals(9, countEntriesWithLastUpdated(result.getSnapshot().getAllRotatingSalts(), daysEarlier(10)));
        assertEquals(targetDate().asInstant(), result.getSnapshot().getEffective());
        assertEquals(daysLater(7).asInstant(), result.getSnapshot().getExpires());
        verify(keyGenerator, times(1)).generateRandomKeyString(anyInt());
    }

    @Test
    void testRotateSaltsRotateSaltsFromOldestBucketOnly() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(5),
                Duration.ofDays(4),
        };

        var lastSnapshot = SaltSnapshotBuilder.start()
                .entries(3, daysEarlier(6), targetDate())
                .entries(5, daysEarlier(5), targetDate())
                .entries(2, daysEarlier(4), targetDate())
                .build();

        var result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.2, targetDate());
        assertTrue(result.hasSnapshot());
        var salts = result.getSnapshot().getAllRotatingSalts();
        assertEquals(2, countEntriesWithLastUpdated(salts, result.getSnapshot().getEffective()));
        assertEquals(2, countEntriesWithLastUpdated(salts, daysEarlier(6)));
        assertEquals(4, countEntriesWithLastUpdated(salts, daysEarlier(5)));
        assertEquals(2, countEntriesWithLastUpdated(salts, daysEarlier(4)));
        assertEquals(targetDate().asInstant(), result.getSnapshot().getEffective());
        assertEquals(daysLater(7).asInstant(), result.getSnapshot().getExpires());
        verify(keyGenerator, times(2)).generateRandomKeyString(anyInt());
    }

    @Test
    void testRotateSaltsRotateSaltsFromNewerBucketOnly() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(5),
                Duration.ofDays(3),
        };

        var lastSnapshot = SaltSnapshotBuilder.start()
                .entries(3, daysEarlier(4), targetDate())
                .entries(7, daysEarlier(3), targetDate())
                .build();

        var result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.2, targetDate());
        assertTrue(result.hasSnapshot());
        var salts = result.getSnapshot().getAllRotatingSalts();
        assertEquals(1, countEntriesWithLastUpdated(salts, result.getSnapshot().getEffective()));
        assertEquals(2, countEntriesWithLastUpdated(salts, daysEarlier(4)));
        assertEquals(7, countEntriesWithLastUpdated(salts, daysEarlier(3)));
        assertEquals(targetDate().asInstant(), result.getSnapshot().getEffective());
        assertEquals(daysLater(7).asInstant(), result.getSnapshot().getExpires());
        verify(keyGenerator, times(1)).generateRandomKeyString(anyInt());
    }

    @Test
    void testRotateSaltsRotateSaltsFromMultipleBuckets() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(5),
                Duration.ofDays(4),
        };

        var lastSnapshot = SaltSnapshotBuilder.start()
                .entries(3, daysEarlier(6), targetDate())
                .entries(5, daysEarlier(5), targetDate())
                .entries(2, daysEarlier(4), targetDate())
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
    void testRotateSaltsRotateSaltsInsufficientOutdatedSalts() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(5),
                Duration.ofDays(3),
        };

        var lastSnapshot = SaltSnapshotBuilder.start()
                .entries(1, daysEarlier(5), targetDate())
                .entries(2, daysEarlier(4), targetDate())
                .entries(7, daysEarlier(2), targetDate())
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
            "5, 0, 30", // Soon after rotation, use 30 days post rotation
            "5, 100, 30", // Soon after rotation, use 30 days post rotation with some offset
            "40, 0, 60", // >30 days after rotation use the next increment of 30 days
            "40, 100, 60", // >30 days after rotation use the next increment of 30 days with some offset
            "60, 0, 90", // Exactly at multiple of 30 days post rotation, use next increment of 30 days
            "60, 100, 90" // Exactly at multiple of 30 days post rotation, use next increment of 30 days with some offset
    })
    void testRefreshFromCalculation(int lastRotationDaysAgo, int lastRotationMsOffset, int refreshFromDaysFromRotation) throws Exception {
        var lastRotation = daysEarlier(lastRotationDaysAgo);
        SaltBuilder saltBuilder = SaltBuilder.start().lastUpdated(lastRotation.asInstant().plusMillis(lastRotationMsOffset)).refreshFrom(targetDate()).currentSalt();
        var lastSnapshot = SaltSnapshotBuilder.start()
                .entries(saltBuilder, saltBuilder, saltBuilder, saltBuilder)
                .build();

        var result = saltRotation.rotateSalts(lastSnapshot, new Duration[]{Duration.ofDays(1)}, 0.45, targetDate());
        var actual = result.getSnapshot().getAllRotatingSalts()[0];

        var expected = lastRotation.plusDays(refreshFromDaysFromRotation).asEpochMs();

        assertThat(actual.refreshFrom()).isEqualTo(expected);
    }

    @Test
    void testRotateSaltsPopulatePreviousSaltsOnRotation() throws Exception {
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
                        SaltBuilder.start().lastUpdated(lessThan90Days).refreshFrom(targetDate()).currentSalt("salt1"),
                        SaltBuilder.start().lastUpdated(exactly90Days).refreshFrom(targetDate()).currentSalt("salt2"),
                        SaltBuilder.start().lastUpdated(over90Days).refreshFrom(targetDate()).currentSalt("salt3")
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
    void testRotateSaltsPreservePreviousSaltsLessThan90DaysOld() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(60),
        };

        var notValidForRotation1 = daysEarlier(40);
        var notValidForRotation2 = daysEarlier(50);
        var validForRotation = daysEarlier(70);
        var lastSnapshot = SaltSnapshotBuilder.start()
                .entries(
                        SaltBuilder.start().lastUpdated(notValidForRotation1).refreshFrom(targetDate()).currentSalt("salt1").previousSalt("previousSalt1"),
                        SaltBuilder.start().lastUpdated(notValidForRotation2).refreshFrom(targetDate()).currentSalt("salt2")
                )
                .entries(1, validForRotation, targetDate())
                .build();

        var result = saltRotation.rotateSalts(lastSnapshot, minAges, 1, targetDate());
        assertTrue(result.hasSnapshot());

        var salts = result.getSnapshot().getAllRotatingSalts();
        assertEquals("previousSalt1", salts[0].previousSalt());
        assertNull(salts[1].previousSalt());
    }

    @Test
    void testRotateSaltsRemovePreviousSaltsOver90DaysOld() throws Exception {
        final Duration[] minAges = {
                Duration.ofDays(100),
        };

        var exactly90Days = daysEarlier(90);
        var over90Days = daysEarlier(100);
        var validForRotation = daysEarlier(120);
        var lastSnapshot = SaltSnapshotBuilder.start()
                .entries(
                        SaltBuilder.start().lastUpdated(exactly90Days).refreshFrom(targetDate()).currentSalt().previousSalt("90DaysOld"),
                        SaltBuilder.start().lastUpdated(over90Days).refreshFrom(targetDate()).currentSalt().previousSalt("over90DaysOld")
                )
                .entries(1, validForRotation, targetDate())
                .build();

        var result = saltRotation.rotateSalts(lastSnapshot, minAges, 0.5, targetDate());
        assertTrue(result.hasSnapshot());

        var salts = result.getSnapshot().getAllRotatingSalts();
        assertNull(salts[0].previousSalt());
        assertNull(salts[1].previousSalt());
    }

    @Test
    void testRotateSaltsRotateWhenRefreshFromIsTargetDate() throws Exception {
        saltRotation = new SaltRotation(keyGenerator,  new JsonObject());

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
                        SaltBuilder.start().lastUpdated(validForRotation1).refreshFrom(refreshNow).currentSalt(),
                        SaltBuilder.start().lastUpdated(notValidForRotation).refreshFrom(refreshNow).currentSalt(),
                        SaltBuilder.start().lastUpdated(validForRotation2).refreshFrom(refreshLater).currentSalt()
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
    void testLogFewSaltAgesOnRotation() throws Exception {
        saltRotation = new SaltRotation(keyGenerator,  new JsonObject());

        // 7 salts total, 5 refreshable, 3 will rotate (6 * 0.4 rounded up), up to 2 will rotate per age (3 * 0.8)
        var lastSnapshot = SaltSnapshotBuilder.start()
                .entries(
                        SaltBuilder.start().lastUpdated(daysEarlier(65)).refreshFrom(targetDate()).currentSalt(), // Refreshable, old enough
                        SaltBuilder.start().lastUpdated(daysEarlier(5)).refreshFrom(targetDate()).currentSalt(), // Refreshable, too new
                        SaltBuilder.start().lastUpdated(daysEarlier(33)).refreshFrom(targetDate()).currentSalt(), // Refreshable, old enough
                        SaltBuilder.start().lastUpdated(daysEarlier(50)).refreshFrom(daysLater(1)).currentSalt(), // Not refreshable, old enough
                        SaltBuilder.start().lastUpdated(daysEarlier(65)).refreshFrom(targetDate()).currentSalt(), // Refreshable, old enough
                        SaltBuilder.start().lastUpdated(daysEarlier(65)).refreshFrom(targetDate()).currentSalt(), // Refreshable, old enough
                        SaltBuilder.start().lastUpdated(daysEarlier(10)).refreshFrom(daysLater(10)).currentSalt() // Not refreshable, too new
                )
                .build();

        var expected = Set.of(
                "[INFO] Salt rotation complete target_date=2025-01-01",
                // Post-rotation ages, we want to look at current state
                "[INFO] salt_count_type=total-salts target_date=2025-01-01 age=0 salt_count=3",
                "[INFO] salt_count_type=total-salts target_date=2025-01-01 age=5 salt_count=1",
                "[INFO] salt_count_type=total-salts target_date=2025-01-01 age=10 salt_count=1",
                "[INFO] salt_count_type=total-salts target_date=2025-01-01 age=50 salt_count=1",
                "[INFO] salt_count_type=total-salts target_date=2025-01-01 age=65 salt_count=1",

                // Pre-rotation ages, we want to see at which ages salts become refreshable, post rotation some will be 0
                "[INFO] salt_count_type=refreshable-salts target_date=2025-01-01 age=5 salt_count=1",
                "[INFO] salt_count_type=refreshable-salts target_date=2025-01-01 age=33 salt_count=1",
                "[INFO] salt_count_type=refreshable-salts target_date=2025-01-01 age=65 salt_count=3",

                // Pre-rotation ages, post rotation they will all have age 0
                "[INFO] salt_count_type=rotated-salts target_date=2025-01-01 age=33 salt_count=1",
                "[INFO] salt_count_type=rotated-salts target_date=2025-01-01 age=65 salt_count=2"
        );

        var minAges = new Duration[]{Duration.ofDays(30), Duration.ofDays(60)};
        saltRotation.rotateSalts(lastSnapshot, minAges, 0.4, targetDate());

        var actual = appender.list.stream().map(Object::toString).filter(s -> s.contains("salt_count_type") || s.contains("Salt rotation complete")).collect(Collectors.toSet());
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void testLogManySaltAgesOnRotation() throws Exception {
        saltRotation = new SaltRotation(keyGenerator,  new JsonObject());

        // 50 salts total, 16 refreshable, 10 will rotate (18 * 0.2 rounded up), up to 8 will rotate per age (10 * 0.8)
        var lastSnapshot = SaltSnapshotBuilder.start()
                .entries(10, daysEarlier(5), targetDate()) // Refreshable, too new
                .entries(10, daysEarlier(10), daysLater(10)) // Not refreshable, too new
                .entries(10, daysEarlier(33), targetDate()) // Refreshable, old enough
                .entries(10, daysEarlier(50), daysLater(1)) // Not refreshable, old enough
                .entries(10, daysEarlier(65), targetDate()) // Refreshable, old enough
                .build();

        var expected = Set.of(
                "[INFO] Salt rotation complete target_date=2025-01-01",
                // Post-rotation ages, we want to look at current state
                "[INFO] salt_count_type=total-salts target_date=2025-01-01 age=0 salt_count=10",
                "[INFO] salt_count_type=total-salts target_date=2025-01-01 age=5 salt_count=10",
                "[INFO] salt_count_type=total-salts target_date=2025-01-01 age=10 salt_count=10",
                "[INFO] salt_count_type=total-salts target_date=2025-01-01 age=33 salt_count=8",
                "[INFO] salt_count_type=total-salts target_date=2025-01-01 age=50 salt_count=10",
                "[INFO] salt_count_type=total-salts target_date=2025-01-01 age=65 salt_count=2",

                // Pre-rotation ages, we want to see at which ages salts become refreshable, post rotation some will be 0
                "[INFO] salt_count_type=refreshable-salts target_date=2025-01-01 age=5 salt_count=10",
                "[INFO] salt_count_type=refreshable-salts target_date=2025-01-01 age=33 salt_count=10",
                "[INFO] salt_count_type=refreshable-salts target_date=2025-01-01 age=65 salt_count=10",

                // Pre-rotation ages, post rotation they will all have age 0
                "[INFO] salt_count_type=rotated-salts target_date=2025-01-01 age=33 salt_count=2",
                "[INFO] salt_count_type=rotated-salts target_date=2025-01-01 age=65 salt_count=8"
        );

        var minAges = new Duration[]{Duration.ofDays(30), Duration.ofDays(60)};
        saltRotation.rotateSalts(lastSnapshot, minAges, 0.2, targetDate());

        var actual = appender.list.stream().map(Object::toString).filter(s -> s.contains("salt_count_type") || s.contains("Salt rotation complete")).collect(Collectors.toSet());
        assertThat(actual).isEqualTo(expected);
    }

    private int countEntriesWithLastUpdated(SaltEntry[] entries, TargetDate lastUpdated) {
        return countEntriesWithLastUpdated(entries, lastUpdated.asInstant());
    }

    private int countEntriesWithLastUpdated(SaltEntry[] entries, Instant lastUpdated) {
        return (int) Arrays.stream(entries).filter(e -> e.lastUpdated() == lastUpdated.toEpochMilli()).count();
    }

    @Test
    void testRotateSaltsZeroDoesntRotateSaltsButUpdatesRefreshFrom() throws Exception {
        var lastSnapshot = SaltSnapshotBuilder.start()
                .entries(
                        SaltBuilder.start().lastUpdated(targetDate().minusDays(75)).refreshFrom(targetDate().minusDays(45)).id(1).currentSalt(),
                        SaltBuilder.start().lastUpdated(targetDate().minusDays(60)).refreshFrom(targetDate()).id(2).currentSalt(),
                        SaltBuilder.start().lastUpdated(targetDate().minusDays(30)).refreshFrom(targetDate()).id(3).currentSalt(),
                        SaltBuilder.start().lastUpdated(targetDate().minusDays(20)).refreshFrom(targetDate().plusDays(10)).id(4).currentSalt()
                )
                .effective(daysEarlier(1))
                .expires(daysLater(6))
                .build();

        var expected = List.of(
                        SaltBuilder.start().lastUpdated(targetDate().minusDays(75)).refreshFrom(targetDate().plusDays(15)).id(1).currentSalt().build(),
                        SaltBuilder.start().lastUpdated(targetDate().minusDays(60)).refreshFrom(targetDate().plusDays(30)).id(2).currentSalt().build(),
                        SaltBuilder.start().lastUpdated(targetDate().minusDays(30)).refreshFrom(targetDate().plusDays(30)).id(3).currentSalt().build(),
                        SaltBuilder.start().lastUpdated(targetDate().minusDays(20)).refreshFrom(targetDate().plusDays(10)).id(4).currentSalt().build()
                ).toArray();

        var result = saltRotation.rotateSaltsZero(lastSnapshot, targetDate(), targetDate().asInstant());
        assertThat(result.hasSnapshot()).isTrue();

        // None are rotated, refreshFrom is updated where it is now or in the past - same as regular rotation
        assertThat(result.getSnapshot().getAllRotatingSalts()).isEqualTo(expected);

        // Effective now
        assertThat(result.getSnapshot().getEffective()).isEqualTo(targetDate().asInstant());

        // Expires in a week
        assertThat(result.getSnapshot().getExpires()).isEqualTo(daysLater(7).asInstant());
    }

    @Test
    void testRotateSaltsZeroWorksWhenThereIsFutureSaltFile() throws Exception {
        // In regular salt rotations if there is a salt

        var lastSnapshot = SaltSnapshotBuilder.start()
                .entries(SaltBuilder.start().lastUpdated(targetDate().minusDays(75)).currentSalt())
                .effective(daysLater(10))
                .build();

        var result = saltRotation.rotateSaltsZero(lastSnapshot, targetDate(), targetDate().asInstant());

        assertThat(result.hasSnapshot()).isTrue();
        assertThat(result.getSnapshot().getEffective()).isEqualTo(targetDate().asInstant());
        assertThat(result.getSnapshot().getExpires()).isEqualTo(daysLater(7).asInstant());
    }

    @Test
    void testRotateFromSaltToKey() throws Exception {
        saltRotation = new SaltRotation(keyGenerator, JsonObject.of(AdminConst.ENABLE_V4_RAW_UID, true));
        when(keyGenerator.generateRandomKeyString(anyInt())).thenReturn("random-key-string");

        final Duration[] minAges = {
                Duration.ofDays(30),
        };

        var willRefresh = targetDate();
        var willNotRefresh = targetDate().plusDays(30);
        var lastSnapshot = SaltSnapshotBuilder.start()
                .entries(SaltBuilder.start().lastUpdated(targetDate().minusDays(60)).refreshFrom(willRefresh).currentSalt("salt1"),
                        SaltBuilder.start().lastUpdated(targetDate().minusDays(60)).refreshFrom(willNotRefresh).currentSalt())
                .build();

        var result = saltRotation.rotateSalts(lastSnapshot, minAges, 1, targetDate());

        assertThat(result.hasSnapshot()).isTrue();

        var salt = result.getSnapshot().getAllRotatingSalts()[0];

        assertThat(salt.currentSalt()).isNull();
        assertThat(salt.previousSalt()).isEqualTo("salt1");
        assertThat(salt.currentKey()).isNotNull();
        assertThat(salt.currentKey().id()).isEqualTo(0);
        assertThat(salt.currentKey().key()).isNotNull();
        assertThat(salt.currentKey().salt()).isNotNull();
        assertThat(salt.previousKey()).isNull();
    }

    @Test
    void testKeyRotationInitialKeyIdPopulation() throws Exception {
        saltRotation = new SaltRotation(keyGenerator, JsonObject.of(AdminConst.ENABLE_V4_RAW_UID, true));

        final Duration[] minAges = {
                Duration.ofDays(30),
        };

        var willRefresh = targetDate();
        var willNotRefresh = targetDate().plusDays(30);
        var lastSnapshot = SaltSnapshotBuilder.start()
                .entries(SaltBuilder.start().lastUpdated(targetDate().minusDays(60)).refreshFrom(willRefresh).currentSalt(),
                        SaltBuilder.start().lastUpdated(targetDate().minusDays(60)).refreshFrom(willRefresh).currentSalt(),
                        SaltBuilder.start().lastUpdated(targetDate().minusDays(60)).refreshFrom(willRefresh).currentSalt(),
                        SaltBuilder.start().lastUpdated(targetDate().minusDays(60)).refreshFrom(willNotRefresh).currentSalt())
                .build();

        var result = saltRotation.rotateSalts(lastSnapshot, minAges, 1, targetDate());
        var salts = result.getSnapshot().getAllRotatingSalts();

        assertEquals(0, salts[0].currentKey().id());
        assertEquals(1, salts[1].currentKey().id());
        assertEquals(2, salts[2].currentKey().id());
        assertNull(salts[3].currentKey());
    }

    @Test
    void testKeyRotationKeyIdIncrementation() throws Exception {
        SecureKeyGenerator keyGenerator = new SecureKeyGenerator();
        saltRotation = new SaltRotation(keyGenerator, JsonObject.of(AdminConst.ENABLE_V4_RAW_UID, true));

        final Duration[] minAges = {
                Duration.ofDays(30),
        };

        var willRefresh = targetDate();
        var willNotRefresh = targetDate().plusDays(30);
        var lastSnapshot = SaltSnapshotBuilder.start()
                .entries(SaltBuilder.start().lastUpdated(targetDate().minusDays(60)).refreshFrom(willNotRefresh).currentKey(0),
                        SaltBuilder.start().lastUpdated(targetDate().minusDays(60)).refreshFrom(willNotRefresh).currentKey(1),
                        SaltBuilder.start().lastUpdated(targetDate().minusDays(60)).refreshFrom(willRefresh).currentSalt(),
                        SaltBuilder.start().lastUpdated(targetDate().minusDays(60)).refreshFrom(willRefresh).currentSalt())
                .build();

        var result = saltRotation.rotateSalts(lastSnapshot, minAges, 1, targetDate());
        var salts = result.getSnapshot().getAllRotatingSalts();

        assertEquals(0, salts[0].currentKey().id());
        assertEquals(1, salts[1].currentKey().id());
        assertEquals(2, salts[2].currentKey().id());
        assertEquals(3, salts[3].currentKey().id());
    }

    private static Stream<Arguments> getKeyIdArguments() {
        return Stream.of(
                Arguments.of(new int[]{16777212, 16777213, 16777214}, new int[]{16777215, 0, 1}),
                Arguments.of(new int[]{16777213, 16777214, 16777215}, new int[]{0, 1, 2}),
                Arguments.of(new int[]{16777215, 0, 1}, new int[]{2, 3, 4})
        );
    }

    @ParameterizedTest
    @MethodSource("getKeyIdArguments")
    void testKeyRotationKeyIdWrap(int[] existingIds, int[] nextIds) throws Exception {
        saltRotation = new SaltRotation(keyGenerator, JsonObject.of(AdminConst.ENABLE_V4_RAW_UID, true));

        final Duration[] minAges = {
                Duration.ofDays(30),
        };

        var willRefresh = targetDate();
        var willNotRefresh = targetDate().plusDays(30);
        var lastSnapshot = SaltSnapshotBuilder.start()
                .entries(SaltBuilder.start().lastUpdated(targetDate().minusDays(60)).refreshFrom(willNotRefresh).currentKey(existingIds[0]),
                        SaltBuilder.start().lastUpdated(targetDate().minusDays(60)).refreshFrom(willNotRefresh).currentKey(existingIds[1]),
                        SaltBuilder.start().lastUpdated(targetDate().minusDays(60)).refreshFrom(willNotRefresh).currentKey(existingIds[2]),
                        SaltBuilder.start().lastUpdated(targetDate().minusDays(60)).refreshFrom(willRefresh).currentSalt(),
                        SaltBuilder.start().lastUpdated(targetDate().minusDays(60)).refreshFrom(willRefresh).currentSalt(),
                        SaltBuilder.start().lastUpdated(targetDate().minusDays(60)).refreshFrom(willRefresh).currentSalt())
                .build();

        var result = saltRotation.rotateSalts(lastSnapshot, minAges, 1, targetDate());
        var salts = result.getSnapshot().getAllRotatingSalts();

        assertEquals(existingIds[0], salts[0].currentKey().id());
        assertEquals(existingIds[1], salts[1].currentKey().id());
        assertEquals(existingIds[2], salts[2].currentKey().id());
        assertEquals(nextIds[0], salts[3].currentKey().id());
        assertEquals(nextIds[1], salts[4].currentKey().id());
        assertEquals(nextIds[2], salts[5].currentKey().id());
    }

    @Test
    void testKeyRotationKeyIdWrapManyBuckets() throws Exception {
        saltRotation = new SaltRotation(keyGenerator, JsonObject.of(AdminConst.ENABLE_V4_RAW_UID, true));

        final Duration[] minAges = {
                Duration.ofDays(390),
                Duration.ofDays(360),
                Duration.ofDays(330)
        };

        var willRefresh = targetDate();
        var willNotRefresh = targetDate().plusDays(30);
        var lastSnapshot = SaltSnapshotBuilder.start()
                .entries(SaltBuilder.start().lastUpdated(targetDate().minusDays(390)).refreshFrom(willRefresh).currentKey(16777200),
                        SaltBuilder.start().lastUpdated(targetDate().minusDays(390)).refreshFrom(willRefresh).currentKey(16777209),
                        SaltBuilder.start().lastUpdated(targetDate().minusDays(390)).refreshFrom(willNotRefresh).currentKey(16777210),
                        SaltBuilder.start().lastUpdated(targetDate().minusDays(330)).refreshFrom(willNotRefresh).currentKey(16777212),
                        SaltBuilder.start().lastUpdated(targetDate().minusDays(330)).refreshFrom(willNotRefresh).currentKey(1),
                        SaltBuilder.start().lastUpdated(targetDate().minusDays(330)).refreshFrom(willNotRefresh).currentKey(4),
                        SaltBuilder.start().lastUpdated(targetDate().minusDays(300)).refreshFrom(willNotRefresh).currentKey(6),
                        SaltBuilder.start().lastUpdated(targetDate().minusDays(300)).refreshFrom(willNotRefresh).currentKey(7),
                        SaltBuilder.start().lastUpdated(targetDate().minusDays(300)).refreshFrom(willNotRefresh).currentKey(8))
                .build();

        var result = saltRotation.rotateSalts(lastSnapshot, minAges, 1, targetDate());
        var salts = result.getSnapshot().getAllRotatingSalts();

        var expectedKeyIds = new int[]{9, 10, 16777210, 16777212, 1, 4, 6, 7, 8};
        assertArrayEquals(expectedKeyIds, Arrays.stream(salts).mapToInt(s -> s.currentKey().id()).toArray());
    }

    @Test
    void testKeyRotationPopulatePreviousKey() throws Exception {
        saltRotation = new SaltRotation(keyGenerator, JsonObject.of(AdminConst.ENABLE_V4_RAW_UID, true));

        final Duration[] minAges = {
                Duration.ofDays(30),
        };

        var willRefresh = targetDate();
        var willNotRefresh = targetDate().plusDays(30);
        var lastSnapshot = SaltSnapshotBuilder.start()
                .entries(SaltBuilder.start().lastUpdated(targetDate().minusDays(60)).refreshFrom(willRefresh).currentKey(0, "keyKey", "keySalt"),
                        SaltBuilder.start().lastUpdated(targetDate().minusDays(60)).refreshFrom(willNotRefresh).currentKey(1),
                        SaltBuilder.start().lastUpdated(targetDate().minusDays(60)).refreshFrom(willNotRefresh).currentKey(2))
                .build();

        var result = saltRotation.rotateSalts(lastSnapshot, minAges, 1, targetDate());
        var salts = result.getSnapshot().getAllRotatingSalts();

        assertEquals(3, salts[0].currentKey().id());
        assertEquals(1, salts[1].currentKey().id());
        assertEquals(2, salts[2].currentKey().id());
        verify(keyGenerator, times(2)).generateRandomKeyString(anyInt());

        assertEquals(0, salts[0].previousKey().id());
        assertEquals("keyKey", salts[0].previousKey().key());
        assertEquals("keySalt", salts[0].previousKey().salt());

        assertNull(salts[1].previousKey());
        assertNull(salts[2].previousKey());
    }

    @ParameterizedTest
    @CsvSource({
            "89, true",
            "90, false",
            "91, false"
    })
    void testKeyRotationPreviousKeyVisibleFor90Days(int lastUpdatedDaysAgo, boolean hasPreviousKey) throws Exception {
        saltRotation = new SaltRotation(keyGenerator, JsonObject.of(AdminConst.ENABLE_V4_RAW_UID, true));

        final Duration[] minAges = {
                Duration.ofDays(30),
        };

        var willRefresh = targetDate();
        var willNotRefresh = targetDate().plusDays(30);
        var lastSnapshot = SaltSnapshotBuilder.start()
                .entries(SaltBuilder.start().lastUpdated(targetDate().minusDays(lastUpdatedDaysAgo)).refreshFrom(willNotRefresh).currentKey(2).previousKey(0),
                        SaltBuilder.start().lastUpdated(targetDate().minusDays(lastUpdatedDaysAgo)).refreshFrom(willRefresh).currentKey(1)) // for sake of getting snapshot
                .build();

        var result = saltRotation.rotateSalts(lastSnapshot, minAges, 1, targetDate());
        var salts = result.getSnapshot().getAllRotatingSalts();

        assertEquals(hasPreviousKey, salts[0].previousKey() != null);
    }

    @Test
    void testKeyRotationSaltToKeyRotation() throws Exception {
        saltRotation = new SaltRotation(keyGenerator, JsonObject.of(AdminConst.ENABLE_V4_RAW_UID, false));
        when(keyGenerator.generateRandomKeyString(anyInt())).thenReturn("random-key-string");

        final Duration[] minAges = {
                Duration.ofDays(30),
        };

        var willRefresh = targetDate();
        var willNotRefresh = targetDate().plusDays(30);
        var lastSnapshot = SaltSnapshotBuilder.start()
                .entries(SaltBuilder.start().lastUpdated(targetDate().minusDays(60)).refreshFrom(willRefresh).currentKey(0, "keyKey1", "keySalt1"),
                        SaltBuilder.start().lastUpdated(targetDate().minusDays(60)).refreshFrom(willNotRefresh).currentSalt())
                .build();

        var result = saltRotation.rotateSalts(lastSnapshot, minAges, 1, targetDate());
        var salts = result.getSnapshot().getAllRotatingSalts();

        assertThat(salts[0].currentKey()).isNull();
        assertThat(salts[0].previousKey()).isEqualTo(new SaltEntry.KeyMaterial(0, "keyKey1", "keySalt1"));
        assertThat(salts[0].currentSalt()).isNotNull();
        assertThat(salts[0].previousSalt()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "true, 1, 3, 1",
            "false, 0, 1, 3"
    })
    void testKeyRotationLogBucketFormat(boolean v4Enabled, int expectedNewKeyBuckets, int expectedTotalKeyBuckets, int expectedTotalSaltBuckets) throws Exception {
        saltRotation = new SaltRotation(keyGenerator, JsonObject.of(AdminConst.ENABLE_V4_RAW_UID, v4Enabled));
        when(keyGenerator.generateRandomKeyString(anyInt())).thenReturn("random-key-string");

        final Duration[] minAges = {
                Duration.ofDays(30)
        };

        var willRefresh = targetDate();
        var willNotRefresh = targetDate().plusDays(30);
        var lastSnapshot = SaltSnapshotBuilder.start()
                .entries(SaltBuilder.start().lastUpdated(targetDate().minusDays(60)).refreshFrom(willRefresh).currentSalt(),
                        SaltBuilder.start().lastUpdated(targetDate().minusDays(60)).refreshFrom(willNotRefresh).currentSalt(),
                        SaltBuilder.start().lastUpdated(targetDate().minusDays(60)).refreshFrom(willRefresh).currentKey(1),
                        SaltBuilder.start().lastUpdated(targetDate().minusDays(60)).refreshFrom(willNotRefresh).currentKey(2))
                .build();

        saltRotation.rotateSalts(lastSnapshot, minAges, 1, targetDate());

        var expected = Set.of(
                String.format("[INFO] UID bucket format: target_date=2025-01-01 migrated_key_bucket_count=%d total_key_bucket_count=%d total_salt_bucket_count=%d " +
                                "total_previous_key_count=%d total_previous_salt_count=%d",
                        expectedNewKeyBuckets, expectedTotalKeyBuckets, expectedTotalSaltBuckets, 1, 1)
        );
        var actual = appender.list.stream().map(Object::toString).filter(s -> s.contains("UID bucket format")).collect(Collectors.toSet());
        assertThat(actual).isEqualTo(expected);
    }
}
