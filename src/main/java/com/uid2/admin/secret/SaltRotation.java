package com.uid2.admin.secret;

import com.uid2.shared.model.SaltEntry;
import com.uid2.shared.secret.IKeyGenerator;
import com.uid2.shared.store.RotatingSaltProvider;
import io.vertx.core.json.JsonObject;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

// TODO: Add feature switch for any logic related to refresh_from
public class SaltRotation implements ISaltRotation {
    private static final int PREVIOUS_SALT_EXPIRATION_DAYS = 90;
    private static final String SNAPSHOT_ACTIVATES_IN_SECONDS = "salt_snapshot_activates_in_seconds";
    private static final String SNAPSHOT_EXPIRES_AFTER_SECONDS = "salt_snapshot_expires_after_seconds";

    private final IKeyGenerator keyGenerator;
    private final Duration snapshotActivatesIn;
    private final Duration snapshotExpiresAfter;

    public static Duration getSnapshotActivatesIn(JsonObject config) {
        return Duration.ofSeconds(config.getInteger(SNAPSHOT_ACTIVATES_IN_SECONDS));
    }

    public static Duration getSnapshotExpiresAfter(JsonObject config) {
        return Duration.ofSeconds(config.getInteger(SNAPSHOT_EXPIRES_AFTER_SECONDS));
    }

    public SaltRotation(JsonObject config, IKeyGenerator keyGenerator) {
        this.keyGenerator = keyGenerator;

        snapshotActivatesIn = getSnapshotActivatesIn(config);
        snapshotExpiresAfter = getSnapshotExpiresAfter(config);

        if (snapshotActivatesIn.compareTo(snapshotExpiresAfter) >= 0) {
            throw new IllegalStateException(SNAPSHOT_EXPIRES_AFTER_SECONDS + " must be greater than " + SNAPSHOT_ACTIVATES_IN_SECONDS);
        }
    }

    @Override
    public Result rotateSalts(RotatingSaltProvider.SaltSnapshot lastSnapshot,
                              Duration[] minAges,
                              double fraction,
                              int period) throws Exception {
        Instant now = Instant.now().plus(1, ChronoUnit.DAYS); // TODO: Plus one?
        Instant nextEffective = now.plusSeconds(snapshotActivatesIn.getSeconds());
        Instant nextExpires = nextEffective.plusSeconds(snapshotExpiresAfter.getSeconds());
        if (!nextEffective.isAfter(lastSnapshot.getEffective())) {
            return Result.noSnapshot("cannot create a new salt snapshot with effective timestamp prior to that of an existing snapshot");
        }

        Instant[] thresholds = Arrays.stream(minAges)
                .map(a -> now.minusSeconds(a.getSeconds()))
                .sorted()
                .toArray(Instant[]::new);
        int maxSalts = (int)Math.ceil(lastSnapshot.getAllRotatingSalts().length * fraction);
        List<Integer> entryIndexes = new ArrayList<>();

        // TODO: Candidate salt age histogram - Number of candidate salts can be derived from above
        Instant minLastUpdated = Instant.ofEpochMilli(0);
        for (Instant threshold : thresholds) {
            if (entryIndexes.size() >= maxSalts) break;
            addIndexesToRotate(entryIndexes, lastSnapshot,
                    now.toEpochMilli(), minLastUpdated.toEpochMilli(), threshold.toEpochMilli(),
                    maxSalts - entryIndexes.size());
            minLastUpdated = threshold;
        }

        if (entryIndexes.isEmpty()) {
            return Result.noSnapshot("all salts are below min rotation age");
        }
        return Result.fromSnapshot(createRotatedSnapshot(lastSnapshot, now, nextEffective, nextExpires, entryIndexes, period));
    }

    private void addIndexesToRotate(List<Integer> entryIndexes,
                                    RotatingSaltProvider.SaltSnapshot lastSnapshot,
                                    long now,
                                    long minLastUpdated,
                                    long maxLastUpdated,
                                    int maxIndexes) {
        SaltEntry[] entries = lastSnapshot.getAllRotatingSalts();
        List<Integer> candidateIndexes = IntStream.range(0, entries.length)
                .filter(i -> entries[i].getRefreshFrom() <= now) // TODO: Check whether refresh is exactly today, round down refreshFrom and now to nearest day
                .filter(i -> isBetween(entries[i].getLastUpdated(), minLastUpdated, maxLastUpdated))
                .boxed().collect(toList());

        if (candidateIndexes.size() <= maxIndexes) {
            entryIndexes.addAll(candidateIndexes);
            return;
        }
        Collections.shuffle(candidateIndexes);
        candidateIndexes.stream().limit(maxIndexes).forEachOrdered(entryIndexes::add);
    }

    private static boolean isBetween(long t, long minInclusive, long maxExclusive) {
        return minInclusive <= t && t < maxExclusive;
    }

    private RotatingSaltProvider.SaltSnapshot createRotatedSnapshot(RotatingSaltProvider.SaltSnapshot lastSnapshot,
                                                                    Instant now,
                                                                    Instant nextEffective,
                                                                    Instant nextExpires,
                                                                    List<Integer> entryIndexes,
                                                                    int period) throws Exception {
        long lastUpdated = nextEffective.toEpochMilli();
        RotatingSaltProvider.SaltSnapshot nextSnapshot = new RotatingSaltProvider.SaltSnapshot(
                nextEffective, nextExpires,
                Arrays.copyOf(lastSnapshot.getAllRotatingSalts(), lastSnapshot.getAllRotatingSalts().length),
                lastSnapshot.getFirstLevelSalt());

        /* TODO: Add metrics
         * Rotated salt age histogram
         * rotatedAgeCounts
         * Number of rotated salts can be derived from above
         * All salt age histogram
         * rotatedAgeCounts + unrotatedAgeCounts
         */

        Map<Long, Integer> unrotatedAgeCounts = new TreeMap<>();
        Map<Long, Integer> rotatedAgeCounts = new TreeMap<>();

        Set<Integer> entryIndexesSet = new HashSet<>(entryIndexes);
        for (int i = 0; i < nextSnapshot.getAllRotatingSalts().length; i++) {
            SaltEntry salt = nextSnapshot.getAllRotatingSalts()[i];
            long age = Duration.between(Instant.ofEpochMilli(salt.getLastUpdated()), now).toDays();

            if (entryIndexesSet.contains(i)) {
                rotatedAgeCounts.put(age, rotatedAgeCounts.getOrDefault(age, 0) + 1);

                String secret = this.keyGenerator.generateRandomKeyString(32);
                nextSnapshot.getAllRotatingSalts()[i] = new SaltEntry(
                        salt.getId(),
                        salt.getHashedId(),
                        lastUpdated,
                        secret,
                        salt.getSalt(),
                        Instant.ofEpochMilli(salt.getRefreshFrom()).plus(Duration.ofDays(period)).toEpochMilli()
                );
            } else {
                unrotatedAgeCounts.put(age, unrotatedAgeCounts.getOrDefault(age, 0) + 1);

                // TODO: Check whether refresh is exactly today, round down refreshFrom and now to nearest day in case cronjob ran a bit later than midnight
                if (salt.getRefreshFrom() > now.toEpochMilli()) {
                    continue;
                }

                if (Duration.between(Instant.ofEpochMilli(salt.getLastUpdated()), now).toDays() > PREVIOUS_SALT_EXPIRATION_DAYS) {
                    salt.setPreviousSalt(null);
                }
                // TODO: Make this add period repeatedly until refresh > now in case of missed rotations
                salt.setRefreshFrom(Instant.ofEpochMilli(salt.getRefreshFrom()).plus(Duration.ofDays(period)).toEpochMilli());
            }
        }

        return nextSnapshot;
    }
}
