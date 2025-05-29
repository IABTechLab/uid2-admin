package com.uid2.admin.salt;

import com.uid2.admin.AdminConst;
import com.uid2.shared.model.SaltEntry;
import com.uid2.shared.secret.IKeyGenerator;

import com.uid2.shared.store.salt.RotatingSaltProvider.SaltSnapshot;
import io.vertx.core.json.JsonObject;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public class SaltRotation {
    private final static long THIRTY_DAYS_IN_MS = Duration.ofDays(30).toMillis();

    private final IKeyGenerator keyGenerator;
    private final boolean isRefreshFromEnabled;
    private static final Logger LOGGER = LoggerFactory.getLogger(SaltRotation.class);

    public SaltRotation(JsonObject config, IKeyGenerator keyGenerator) {
        this.keyGenerator = keyGenerator;
        this.isRefreshFromEnabled = config.getBoolean(AdminConst.ENABLE_SALT_ROTATION_REFRESH_FROM, false);
    }

    public Result rotateSalts(
            SaltSnapshot lastSnapshot,
            Duration[] minAges,
            double fraction,
            TargetDate targetDate
    ) throws Exception {
        var preRotationSalts = lastSnapshot.getAllRotatingSalts();
        var nextEffective = targetDate.asInstant();
        var nextExpires = nextEffective.plus(7, ChronoUnit.DAYS);
        if (nextEffective.equals(lastSnapshot.getEffective()) || nextEffective.isBefore(lastSnapshot.getEffective())) {
            return Result.noSnapshot("cannot create a new salt snapshot with effective timestamp equal or prior to that of an existing snapshot");
        }

        // Salts that can be rotated based on their refreshFrom being at target date
        var refreshableSalts = findRefreshableSalts(preRotationSalts, targetDate);

        var saltsToRotate = pickSaltsToRotate(
                refreshableSalts,
                targetDate,
                minAges,
                getNumSaltsToRotate(preRotationSalts, fraction)
        );

        if (saltsToRotate.isEmpty()) {
            return Result.noSnapshot("all refreshable salts are below min rotation age");
        }

        var postRotationSalts = rotateSalts(preRotationSalts, saltsToRotate, targetDate);

        LOGGER.info("Salt rotation complete target_date={}", targetDate);
        logSaltAges("refreshable-salts", targetDate, refreshableSalts);
        logSaltAges("rotated-salts", targetDate, saltsToRotate);
        logSaltAges("total-salts", targetDate, Arrays.asList(postRotationSalts));

        var nextSnapshot = new SaltSnapshot(
                nextEffective,
                nextExpires,
                postRotationSalts,
                lastSnapshot.getFirstLevelSalt());
        return Result.fromSnapshot(nextSnapshot);
    }

    private static int getNumSaltsToRotate(SaltEntry[] preRotationSalts, double fraction) {
        return (int) Math.ceil(preRotationSalts.length * fraction);
    }

    private Set<SaltEntry> findRefreshableSalts(SaltEntry[] preRotationSalts, TargetDate targetDate) {
        return Arrays.stream(preRotationSalts).filter(s -> isRefreshable(targetDate, s)).collect(Collectors.toSet());
    }

    private boolean isRefreshable(TargetDate targetDate, SaltEntry salt) {
        if (this.isRefreshFromEnabled) {
            return salt.refreshFrom().equals(targetDate.asEpochMs());
        }

        return true;
    }

    private SaltEntry[] rotateSalts(SaltEntry[] oldSalts, List<SaltEntry> saltsToRotate, TargetDate targetDate) throws Exception {
        var saltIdsToRotate = saltsToRotate.stream().map(SaltEntry::id).collect(Collectors.toSet());

        var updatedSalts = new SaltEntry[oldSalts.length];
        for (int i = 0; i < oldSalts.length; i++) {
            var shouldRotate = saltIdsToRotate.contains(oldSalts[i].id());
            updatedSalts[i] = updateSalt(oldSalts[i], targetDate, shouldRotate);
        }
        return updatedSalts;
    }

    private SaltEntry updateSalt(SaltEntry oldSalt, TargetDate targetDate, boolean shouldRotate) throws Exception {
        var currentSalt = shouldRotate ? this.keyGenerator.generateRandomKeyString(32) : oldSalt.currentSalt();
        var lastUpdated = shouldRotate ? targetDate.asEpochMs() : oldSalt.lastUpdated();
        var refreshFrom = calculateRefreshFrom(oldSalt, targetDate);
        var previousSalt = calculatePreviousSalt(oldSalt, shouldRotate, targetDate);

        return new SaltEntry(
                oldSalt.id(),
                oldSalt.hashedId(),
                lastUpdated,
                currentSalt,
                refreshFrom,
                previousSalt,
                null,
                null
        );
    }

    private long calculateRefreshFrom(SaltEntry salt, TargetDate targetDate) {
        long multiplier = targetDate.saltAgeInDays(salt) / 30 + 1;
        return salt.lastUpdated() + (multiplier * THIRTY_DAYS_IN_MS);
    }

    private String calculatePreviousSalt(SaltEntry salt, boolean shouldRotate, TargetDate targetDate) {
        if (shouldRotate) {
            return salt.currentSalt();
        }
        if (targetDate.saltAgeInDays(salt) < 90) {
            return salt.previousSalt();
        }
        return null;
    }

    private List<SaltEntry> pickSaltsToRotate(
            Set<SaltEntry> refreshableSalts,
            TargetDate targetDate,
            Duration[] minAges,
            int numSaltsToRotate
    ) {
        var thresholds = Arrays.stream(minAges)
                .map(minAge -> targetDate.asInstant().minusSeconds(minAge.getSeconds()))
                .sorted()
                .toArray(Instant[]::new);
        var indexesToRotate = new ArrayList<SaltEntry>();

        var minLastUpdated = Instant.ofEpochMilli(0);
        for (var maxLastUpdated : thresholds) {
            if (indexesToRotate.size() >= numSaltsToRotate) break;

            var maxIndexes = numSaltsToRotate - indexesToRotate.size();
            var saltsToRotate = pickSaltsToRotateInTimeWindow(
                    refreshableSalts,
                    maxIndexes,
                    minLastUpdated.toEpochMilli(),
                    maxLastUpdated.toEpochMilli()
            );
            indexesToRotate.addAll(saltsToRotate);
            minLastUpdated = maxLastUpdated;
        }
        return indexesToRotate;
    }

    private List<SaltEntry> pickSaltsToRotateInTimeWindow(
            Set<SaltEntry> refreshableSalts,
            int maxIndexes,
            long minLastUpdated,
            long maxLastUpdated
    ) {
        ArrayList<SaltEntry> candidateSalts = refreshableSalts.stream()
                .filter(salt -> minLastUpdated <= salt.lastUpdated() && salt.lastUpdated() < maxLastUpdated)
                .collect(Collectors.toCollection(ArrayList::new));

        if (candidateSalts.size() <= maxIndexes) {
            return candidateSalts;
        }

        Collections.shuffle(candidateSalts);

        return candidateSalts.stream().limit(maxIndexes).collect(Collectors.toList());
    }

    private void logSaltAges(String saltCountType, TargetDate targetDate, Collection<SaltEntry> salts) {
        var ages = new HashMap<Long, Long>(); // salt age to count
        for (var salt : salts) {
            long ageInDays = targetDate.saltAgeInDays(salt);
            ages.put(ageInDays, ages.getOrDefault(ageInDays, 0L) + 1);
        }

        for (var entry : ages.entrySet()) {
            LOGGER.info("salt_count_type={} target_date={} age={} salt_count={}",
                    saltCountType,
                    targetDate,
                    entry.getKey(),
                    entry.getValue()
            );
        }
    }

    @Getter
    public static class Result {
        private final SaltSnapshot snapshot; // can be null if new snapshot is not needed
        private final String reason; // why you are not getting a new snapshot

        private Result(SaltSnapshot snapshot, String reason) {
            this.snapshot = snapshot;
            this.reason = reason;
        }

        public boolean hasSnapshot() {
            return snapshot != null;
        }

        public static Result fromSnapshot(SaltSnapshot snapshot) {
            return new Result(snapshot, null);
        }

        public static Result noSnapshot(String reason) {
            return new Result(null, reason);
        }
    }
}
