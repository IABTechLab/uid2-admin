package com.uid2.admin.salt;

import com.uid2.admin.AdminConst;
import com.uid2.shared.model.SaltEntry;
import com.uid2.shared.secret.IKeyGenerator;

import com.uid2.shared.store.salt.ISaltProvider.ISaltSnapshot;
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
    private static final long THIRTY_DAYS_IN_MS = Duration.ofDays(30).toMillis();
    private static final double MAX_SALT_PERCENTAGE = 0.8;
    private final boolean ENABLE_V4_RAW_UID;

    private final IKeyGenerator keyGenerator;

    private static final Logger LOGGER = LoggerFactory.getLogger(SaltRotation.class);

    public SaltRotation(IKeyGenerator keyGenerator, JsonObject config) {
        this.keyGenerator = keyGenerator;
        this.ENABLE_V4_RAW_UID = config.getBoolean(AdminConst.ENABLE_V4_RAW_UID, false);
    }

    public Result rotateSalts(
            SaltSnapshot lastSnapshot,
            Duration[] minAges,
            double fraction,
            TargetDate targetDate) throws Exception {
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
        logBucketFormatCount(targetDate, preRotationSalts, postRotationSalts);

        var nextSnapshot = new SaltSnapshot(
                nextEffective,
                nextExpires,
                postRotationSalts,
                lastSnapshot.getFirstLevelSalt());
        return Result.fromSnapshot(nextSnapshot);
    }

    public Result rotateSaltsZero(
            ISaltSnapshot effectiveSnapshot,
            TargetDate targetDate,
            Instant nextEffective
    ) throws Exception {
        var preRotationSalts = effectiveSnapshot.getAllRotatingSalts();
        var nextExpires = nextEffective.plus(7, ChronoUnit.DAYS);

        var postRotationSalts = rotateSalts(preRotationSalts, List.of(), targetDate);

        LOGGER.info("Zero salt rotation complete target_date={}", targetDate);

        var nextSnapshot = new SaltSnapshot(
                nextEffective,
                nextExpires,
                postRotationSalts,
                effectiveSnapshot.getFirstLevelSalt());
        return Result.fromSnapshot(nextSnapshot);
    }

    private static int getNumSaltsToRotate(SaltEntry[] preRotationSalts, double fraction) {
        return (int) Math.ceil(preRotationSalts.length * fraction);
    }

    private Set<SaltEntry> findRefreshableSalts(SaltEntry[] preRotationSalts, TargetDate targetDate) {
        return Arrays.stream(preRotationSalts).filter(s -> isRefreshable(targetDate, s)).collect(Collectors.toSet());
    }

    private boolean isRefreshable(TargetDate targetDate, SaltEntry salt) {
        return Instant.ofEpochMilli(salt.refreshFrom()).truncatedTo(ChronoUnit.DAYS).equals(targetDate.asInstant());
    }

    private SaltEntry[] rotateSalts(SaltEntry[] oldSalts, List<SaltEntry> saltsToRotate, TargetDate targetDate) throws Exception {
        var keyIdGenerator = new KeyIdGenerator(oldSalts);
        var saltIdsToRotate = saltsToRotate.stream().map(SaltEntry::id).collect(Collectors.toSet());

        var updatedSalts = new SaltEntry[oldSalts.length];
        for (int i = 0; i < oldSalts.length; i++) {
            var shouldRotate = saltIdsToRotate.contains(oldSalts[i].id());
            updatedSalts[i] = updateSalt(oldSalts[i], targetDate, shouldRotate, keyIdGenerator);
        }
        return updatedSalts;
    }

    private SaltEntry updateSalt(SaltEntry oldBucket, TargetDate targetDate, boolean shouldRotate, KeyIdGenerator keyIdGenerator) throws Exception {
        var lastUpdated = shouldRotate ? targetDate.asEpochMs() : oldBucket.lastUpdated();
        var refreshFrom = calculateRefreshFrom(oldBucket, targetDate);
        var currentSalt = calculateCurrentSalt(oldBucket, shouldRotate);
        var previousSalt = calculatePreviousSalt(oldBucket, shouldRotate, targetDate);
        var currentKeySalt = calculateCurrentKeySalt(oldBucket, shouldRotate, keyIdGenerator);
        var previousKeySalt = calculatePreviousKeySalt(oldBucket,shouldRotate, targetDate);

        return new SaltEntry(
                oldBucket.id(),
                oldBucket.hashedId(),
                lastUpdated,
                currentSalt,
                refreshFrom,
                previousSalt,
                currentKeySalt,
                previousKeySalt
        );
    }

    private long calculateRefreshFrom(SaltEntry bucket, TargetDate targetDate) {
        long multiplier = targetDate.saltAgeInDays(bucket) / 30 + 1;
        return Instant.ofEpochMilli(bucket.lastUpdated()).truncatedTo(ChronoUnit.DAYS).toEpochMilli() + (multiplier * THIRTY_DAYS_IN_MS);
    }

    private String calculateCurrentSalt(SaltEntry bucket, boolean shouldRotate) throws Exception {
        if (shouldRotate) {
            if (ENABLE_V4_RAW_UID) {
                return null;
            }
            else {
                return this.keyGenerator.generateRandomKeyString(32);
            }
        }
        return bucket.currentSalt();
    }

    private String calculatePreviousSalt(SaltEntry bucket, boolean shouldRotate, TargetDate targetDate) {
        if (shouldRotate) {
            return bucket.currentSalt();
        }
        if (targetDate.saltAgeInDays(bucket) < 90) {
            return bucket.previousSalt();
        }
        return null;
    }

    private SaltEntry.KeyMaterial calculateCurrentKeySalt(SaltEntry bucket, boolean shouldRotate, KeyIdGenerator keyIdGenerator) throws Exception {
        if (shouldRotate) {
            if (ENABLE_V4_RAW_UID) {
                return new SaltEntry.KeyMaterial(
                        keyIdGenerator.getNextKeyId(),
                        this.keyGenerator.generateRandomKeyString(32),
                        this.keyGenerator.generateRandomKeyString(32)
                );
            } else {
                return null;
            }
        }
        return bucket.currentKeySalt();
    }

    private SaltEntry.KeyMaterial calculatePreviousKeySalt(SaltEntry bucket, boolean shouldRotate, TargetDate targetDate) {
        if (shouldRotate) {
            return bucket.currentKeySalt();
        }
        if (targetDate.saltAgeInDays(bucket) < 90) {
            return bucket.previousKeySalt();
        }
        return null;
    }

    private List<SaltEntry> pickSaltsToRotate(
            Set<SaltEntry> refreshableSalts,
            TargetDate targetDate,
            Duration[] minAges,
            int numSaltsToRotate) {
        var maxSaltsPerAge = (int) (numSaltsToRotate * MAX_SALT_PERCENTAGE);

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
                    Math.min(maxIndexes, maxSaltsPerAge),
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
            long maxLastUpdated) {
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


    /** Logging to monitor migration of buckets from salts (old format - v2/v3) to encryption keys (new format - v4) **/
    private void logBucketFormatCount(TargetDate targetDate, SaltEntry[] preRotationBuckets, SaltEntry[] postRotationBuckets) {
        int migratedKeyBuckets = 0, totalKeys = 0, totalSalts = 0, totalPreviousKeys = 0, totalPreviousSalts = 0;

        for (int i = 0; i < preRotationBuckets.length && i < postRotationBuckets.length; i++) {
            var oldBucket = preRotationBuckets[i];
            var updatedBucket = postRotationBuckets[i];

            if (updatedBucket.currentKey() != null) totalKeys++;
            if (updatedBucket.currentSalt() != null) totalSalts++;
            if (updatedBucket.previousKey() != null) totalPreviousKeys++;
            if (updatedBucket.previousSalt() != null) totalPreviousSalts++;
            if (updatedBucket.currentKey() != null && oldBucket.currentSalt() != null) migratedKeyBuckets++;
        }

        LOGGER.info("UID bucket format: target_date={} migrated_key_bucket_count={} total_key_bucket_count={} total_salt_bucket_count={} " +
                        "total_previous_key_count={} total_previous_salt_count={}",
                targetDate, migratedKeyBuckets, totalKeys, totalSalts, totalPreviousKeys, totalPreviousSalts);
    }

    @Getter
    public static final class Result {
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
