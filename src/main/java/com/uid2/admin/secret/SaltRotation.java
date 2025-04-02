package com.uid2.admin.secret;

import com.uid2.admin.vertx.service.SaltService;
import com.uid2.shared.model.SaltEntry;
import com.uid2.shared.secret.IKeyGenerator;
import com.uid2.shared.store.RotatingSaltProvider;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

public class SaltRotation implements ISaltRotation {
    private static final Logger LOGGER = LoggerFactory.getLogger(SaltRotation.class);
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
                              double fraction) throws Exception {
        final Instant now = Instant.now();
        final Instant nextEffective = now.plusSeconds(snapshotActivatesIn.getSeconds());
        final Instant nextExpires = nextEffective.plusSeconds(snapshotExpiresAfter.getSeconds());
        if (!nextEffective.isAfter(lastSnapshot.getEffective())) {
            return Result.noSnapshot("cannot create a new salt snapshot with effective timestamp prior to that of an existing snapshot");
        }

        final Instant[] thresholds = Arrays.stream(minAges)
                .map(a -> now.minusSeconds(a.getSeconds()))
                .sorted()
                .toArray(Instant[]::new);
        final int maxSalts = (int)Math.ceil(lastSnapshot.getAllRotatingSalts().length * fraction);
        final List<Integer> entryIndexes = new ArrayList<>();

        Instant minLastUpdated = Instant.ofEpochMilli(0);
        for (Instant threshold : thresholds) {
            if (entryIndexes.size() >= maxSalts) break;
            addIndexesToRotate(entryIndexes, lastSnapshot,
                    minLastUpdated.toEpochMilli(), threshold.toEpochMilli(),
                    maxSalts - entryIndexes.size());
            minLastUpdated = threshold;
        }

        if (entryIndexes.isEmpty()) return Result.noSnapshot("all salts are below min rotation age");

        return Result.fromSnapshot(createRotatedSnapshot(lastSnapshot, nextEffective, nextExpires, entryIndexes));
    }

    @Override
    public Result rotateSaltsSimulation(RotatingSaltProvider.SaltSnapshot lastSnapshot,
                                        Duration[] minAges,
                                        double fraction,
                                        int period,
                                        int numEpochs) throws Exception {
        final Instant now = SaltService.NOW.plus(Duration.ofDays(numEpochs + 1));
        final Instant nextEffective = now.plusSeconds(snapshotActivatesIn.getSeconds());
        final Instant nextExpires = nextEffective.plusSeconds(snapshotExpiresAfter.getSeconds());
        if (!nextEffective.isAfter(lastSnapshot.getEffective())) {
            return Result.noSnapshot("cannot create a new salt snapshot with effective timestamp prior to that of an existing snapshot");
        }

        final Instant[] thresholds = Arrays.stream(minAges)
                .map(a -> now.minusSeconds(a.getSeconds()))
                .sorted()
                .toArray(Instant[]::new);
        final int maxSalts = (int)Math.ceil(lastSnapshot.getAllRotatingSalts().length * fraction);
        final List<Integer> entryIndexes = new ArrayList<>();

        Instant minLastUpdated = Instant.ofEpochMilli(0);
        for (Instant threshold : thresholds) {
            if (entryIndexes.size() >= maxSalts) break;
            addIndexesToRotateForward(entryIndexes, lastSnapshot,
                    now.toEpochMilli(), minLastUpdated.toEpochMilli(), threshold.toEpochMilli(),
                    maxSalts - entryIndexes.size());
            minLastUpdated = threshold;
        }

        if (entryIndexes.isEmpty()) return Result.noSnapshot("all salts are below min rotation age");

        return Result.fromSnapshot(createRotatedSnapshotSimulation(lastSnapshot, now, nextEffective, nextExpires, entryIndexes, period, numEpochs));
    }

    private void addIndexesToRotate(List<Integer> entryIndexes,
                                    RotatingSaltProvider.SaltSnapshot lastSnapshot,
                                    long minLastUpdated,
                                    long maxLastUpdated,
                                    int maxIndexes) {
        final SaltEntry[] entries = lastSnapshot.getAllRotatingSalts();
        final List<Integer> candidateIndexes = IntStream.range(0, entries.length)
                .filter(i -> isBetween(entries[i].getLastUpdated(), minLastUpdated, maxLastUpdated))
                .boxed().collect(toList());
        if (candidateIndexes.size() <= maxIndexes) {
            entryIndexes.addAll(candidateIndexes);
            return;
        }
        Collections.shuffle(candidateIndexes);
        candidateIndexes.stream().limit(maxIndexes).forEachOrdered(i -> entryIndexes.add(i));
    }

    private void addIndexesToRotateForward(List<Integer> entryIndexes,
                                           RotatingSaltProvider.SaltSnapshot lastSnapshot,
                                           long now,
                                           long minLastUpdated,
                                           long maxLastUpdated,
                                           int maxIndexes) {
        final SaltEntry[] entries = lastSnapshot.getAllRotatingSalts();
        final List<Integer> candidateIndexes = IntStream.range(0, entries.length)
                .filter(i -> entries[i].getRefreshFrom() <= now)
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
                                                                    Instant nextEffective,
                                                                    Instant nextExpires,
                                                                    List<Integer> entryIndexes) throws Exception {
        final long lastUpdated = nextEffective.toEpochMilli();
        final RotatingSaltProvider.SaltSnapshot nextSnapshot = new RotatingSaltProvider.SaltSnapshot(
                nextEffective, nextExpires,
                Arrays.copyOf(lastSnapshot.getAllRotatingSalts(), lastSnapshot.getAllRotatingSalts().length),
                lastSnapshot.getFirstLevelSalt());
        for (Integer i : entryIndexes) {
            final SaltEntry oldSalt = nextSnapshot.getAllRotatingSalts()[i];
            final String secret = this.keyGenerator.generateRandomKeyString(32);
            nextSnapshot.getAllRotatingSalts()[i] = new SaltEntry(oldSalt.getId(), oldSalt.getHashedId(), lastUpdated, secret);
        }
        return nextSnapshot;
    }

    private RotatingSaltProvider.SaltSnapshot createRotatedSnapshotSimulation(RotatingSaltProvider.SaltSnapshot lastSnapshot,
                                                                              Instant now,
                                                                              Instant nextEffective,
                                                                              Instant nextExpires,
                                                                              List<Integer> entryIndexes,
                                                                              int period,
                                                                              int numEpochs) throws Exception {
        final long lastUpdated = nextEffective.toEpochMilli();
        final RotatingSaltProvider.SaltSnapshot nextSnapshot = new RotatingSaltProvider.SaltSnapshot(
                nextEffective, nextExpires,
                Arrays.copyOf(lastSnapshot.getAllRotatingSalts(), lastSnapshot.getAllRotatingSalts().length),
                lastSnapshot.getFirstLevelSalt());

        Map<Long, Integer> unrotatedAgeCounts = new TreeMap<>();
        Map<Long, Integer> rotatedAgeCounts = new TreeMap<>();
        long totalAge = 0;

        Set<Integer> entryIndexesSet = new HashSet<>(entryIndexes);
        for (int i = 0; i < nextSnapshot.getAllRotatingSalts().length; i++) {
            SaltEntry salt = nextSnapshot.getAllRotatingSalts()[i];
            long age = Duration.between(Instant.ofEpochMilli(salt.getLastUpdated()), now).toDays();

            if (entryIndexesSet.contains(i)) {
                rotatedAgeCounts.put(age, rotatedAgeCounts.getOrDefault(age, 0) + 1);
                totalAge += age;

                final String secret = this.keyGenerator.generateRandomKeyString(32);
                nextSnapshot.getAllRotatingSalts()[i] = new SaltEntry(
                        salt.getId(),
                        salt.getHashedId(),
                        lastUpdated,
                        secret,
                        Instant.ofEpochMilli(salt.getRefreshFrom()).plus(Duration.ofDays(period)).toEpochMilli()
                );
            } else {
                unrotatedAgeCounts.put(age, unrotatedAgeCounts.getOrDefault(age, 0) + 1);
                if (salt.getRefreshFrom() > now.toEpochMilli()) continue;
                salt.setRefreshFrom(Instant.ofEpochMilli(salt.getRefreshFrom()).plus(Duration.ofDays(period)).toEpochMilli());
            }
        }

        LOGGER.info(
                "Epoch: {} | Rotated salts: {} | Average lifetime: {}",
                numEpochs,
                entryIndexes.size(),
                (double) totalAge / entryIndexes.size()
        );
//        for (Map.Entry<Long, Integer> entry : unrotatedAgeCounts.entrySet()) {
//            LOGGER.info("Unrotated age: {} | Count: {}", entry.getKey(), entry.getValue());
//        }
        for (Map.Entry<Long, Integer> entry : rotatedAgeCounts.entrySet()) {
            LOGGER.info("Rotated age: {} | Count: {}", entry.getKey(), entry.getValue());
        }

        return nextSnapshot;
    }
}
