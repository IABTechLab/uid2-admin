package com.uid2.admin.secret;

import com.uid2.admin.AdminConst;
import com.uid2.shared.model.SaltEntry;
import com.uid2.shared.secret.IKeyGenerator;

import com.uid2.shared.store.salt.RotatingSaltProvider.SaltSnapshot;
import io.vertx.core.json.JsonObject;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class SaltRotation {
    private final static long THIRTY_DAYS_IN_MS = Duration.ofDays(30).toMillis();
    private final static long DAY_IN_MS = Duration.ofDays(1).toMillis();

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
            LocalDate targetDate
    ) throws Exception {
        var preRotationSalts = lastSnapshot.getAllRotatingSalts();
        var nextEffective = targetDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        var nextExpires = nextEffective.plus(7, ChronoUnit.DAYS);
        if (nextEffective.equals(lastSnapshot.getEffective()) || nextEffective.isBefore(lastSnapshot.getEffective())) {
            return Result.noSnapshot("cannot create a new salt snapshot with effective timestamp equal or prior to that of an existing snapshot");
        }

        var rotatableSaltIndexes = findRotatableSaltIndexes(preRotationSalts, nextEffective.toEpochMilli());
        var saltIndexesToRotate = pickSaltIndexesToRotate(
                nextEffective,
                minAges,
                fraction,
                preRotationSalts,
                rotatableSaltIndexes
        );

        if (saltIndexesToRotate.isEmpty()) {
            return Result.noSnapshot("all rotatable salts are below min rotation age");
        }

        var postRotationSalts = updateSalts(preRotationSalts, saltIndexesToRotate, nextEffective.toEpochMilli());

        var rotatableSalts = onlySaltsAtIndexes(preRotationSalts, rotatableSaltIndexes);
        logSaltAgeCounts("rotatable-salts", targetDate, nextEffective, rotatableSalts);

        var rotatedSalts = onlySaltsAtIndexes(preRotationSalts, saltIndexesToRotate);
        logSaltAgeCounts("rotated-salts", targetDate, nextEffective, rotatedSalts);

        logSaltAgeCounts("total-salts", targetDate, nextEffective, postRotationSalts);

        var nextSnapshot = new SaltSnapshot(
                nextEffective,
                nextExpires,
                postRotationSalts,
                lastSnapshot.getFirstLevelSalt());
        return Result.fromSnapshot(nextSnapshot);
    }

    private List<Integer> findRotatableSaltIndexes(SaltEntry[] preRotationSalts, long nextEffective) {
        var rotatableSalts = new ArrayList<Integer>();
        for (int i = 0; i < preRotationSalts.length; i++) {
            if (isRotatable(nextEffective, preRotationSalts[i])) {
                rotatableSalts.add(i);
            }
        }
        return rotatableSalts;
    }

    private SaltEntry[] updateSalts(SaltEntry[] oldSalts, List<Integer> saltIndexesToRotate, long nextEffective) throws Exception {
        var updatedSalts = new SaltEntry[oldSalts.length];

        for (int i = 0; i < oldSalts.length; i++) {
            var shouldRotate = saltIndexesToRotate.contains(i);
            updatedSalts[i] = updateSalt(oldSalts[i], shouldRotate, nextEffective);
        }
        return updatedSalts;
    }

    private SaltEntry updateSalt(SaltEntry oldSalt, boolean shouldRotate, long nextEffective) throws Exception {
        var currentSalt = shouldRotate ? this.keyGenerator.generateRandomKeyString(32) : oldSalt.currentSalt();
        var lastUpdated = shouldRotate ? nextEffective : oldSalt.lastUpdated();
        var refreshFrom = calculateRefreshFrom(oldSalt.lastUpdated(), nextEffective);
        var previousSalt = calculatePreviousSalt(oldSalt, shouldRotate, nextEffective);

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

    private long calculateRefreshFrom(long lastUpdated, long nextEffective) {
        long age = nextEffective - lastUpdated;
        long multiplier = age / THIRTY_DAYS_IN_MS + 1;
        return lastUpdated + (multiplier * THIRTY_DAYS_IN_MS);
    }

    private String calculatePreviousSalt(SaltEntry salt, boolean shouldRotate, long nextEffective) throws Exception {
        if (shouldRotate) {
            return salt.currentSalt();
        }
        long age = nextEffective - salt.lastUpdated();
        if (age / DAY_IN_MS < 90) {
            return salt.previousSalt();
        }
        return null;
    }

    private List<Integer> pickSaltIndexesToRotate(
            Instant nextEffective,
            Duration[] minAges,
            double fraction,
            SaltEntry[] preRotationSalts,
            List<Integer> rotatableSaltIndexes
    ) {
        var thresholds = Arrays.stream(minAges)
                .map(age -> nextEffective.minusSeconds(age.getSeconds()))
                .sorted()
                .toArray(Instant[]::new);
        var maxSalts = (int) Math.ceil(preRotationSalts.length * fraction);
        var indexesToRotate = new ArrayList<Integer>();

        var minLastUpdated = Instant.ofEpochMilli(0);
        for (var maxLastUpdated : thresholds) {
            if (indexesToRotate.size() >= maxSalts) break;

            var maxIndexes = maxSalts - indexesToRotate.size();
            var saltsToRotate = selectIndexesToRotate(
                    preRotationSalts,
                    minLastUpdated.toEpochMilli(),
                    maxLastUpdated.toEpochMilli(),
                    maxIndexes,
                    rotatableSaltIndexes
            );
            indexesToRotate.addAll(saltsToRotate);
            minLastUpdated = maxLastUpdated;
        }
        return indexesToRotate;
    }

    private List<Integer> selectIndexesToRotate(
            SaltEntry[] salts,
            long minLastUpdated,
            long maxLastUpdated,
            int maxIndexes,
            List<Integer> rotatableSaltIndexes
    ) {
        var candidateIndexes = indexesForRotation(salts, minLastUpdated, maxLastUpdated, rotatableSaltIndexes);

        if (candidateIndexes.size() <= maxIndexes) {
            return candidateIndexes;
        }
        Collections.shuffle(candidateIndexes);
        return candidateIndexes.subList(0, Math.min(maxIndexes, candidateIndexes.size()));
    }

    private List<Integer> indexesForRotation(
            SaltEntry[] salts,
            long minLastUpdated,
            long maxLastUpdated,
            List<Integer> rotatableSaltIndexes
    ) {
        var candidateIndexes = new ArrayList<Integer>();
        for (int i = 0; i < salts.length; i++) {
            var salt = salts[i];
            var lastUpdated = salt.lastUpdated();
            var isInTimeWindow = minLastUpdated <= lastUpdated && lastUpdated < maxLastUpdated;
            var isRotatable = rotatableSaltIndexes.contains(i);

            if (isInTimeWindow && isRotatable) {
                candidateIndexes.add(i);
            }
        }
        return candidateIndexes;
    }

    private boolean isRotatable(long nextEffective, SaltEntry salt) {
        if (this.isRefreshFromEnabled) {
            if (salt.refreshFrom() == null) { // TODO: remove once refreshFrom is no longer optional
                return true;
            }
            return salt.refreshFrom() == nextEffective;
        }

        return true;
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

    private void logSaltAgeCounts(String logEvent, LocalDate targetDate, Instant nextEffective, SaltEntry[] salts) {
        var formattedDate = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(targetDate);

        var ages = new HashMap<Long, Long>(); // salt age to count
        for (var salt : salts) {
            long age = (nextEffective.toEpochMilli() - salt.lastUpdated()) / DAY_IN_MS;
            ages.put(age, ages.getOrDefault(age, 0L) + 1);
        }

        for (var entry : ages.entrySet()) {
            LOGGER.info("{} target-date={} age={} salts={}", logEvent, formattedDate, entry.getKey(), entry.getValue());
        }
    }

    private static SaltEntry[] onlySaltsAtIndexes(SaltEntry[] salts, List<Integer> saltIndexes) {
        SaltEntry[] selected = new SaltEntry[saltIndexes.size()];
        for (int i = 0; i < saltIndexes.size(); i++) {
            selected[i] = salts[saltIndexes.get(i)];
        }
        return selected;
    }
}
