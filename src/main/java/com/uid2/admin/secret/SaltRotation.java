package com.uid2.admin.secret;

import com.uid2.shared.model.SaltEntry;
import com.uid2.shared.secret.IKeyGenerator;
import com.uid2.shared.store.RotatingSaltProvider;
import io.vertx.core.json.JsonObject;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

public class SaltRotation implements ISaltRotation {
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
}
