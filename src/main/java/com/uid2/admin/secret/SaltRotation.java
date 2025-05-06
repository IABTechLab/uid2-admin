package com.uid2.admin.secret;

import com.uid2.shared.model.SaltEntry;
import com.uid2.shared.secret.IKeyGenerator;
import com.uid2.shared.store.salt.RotatingSaltProvider;
import io.vertx.core.json.JsonObject;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

public class SaltRotation implements ISaltRotation {
    private final IKeyGenerator keyGenerator;
    private static final int DAY_IN_SECONDS = 86400;

    public SaltRotation(IKeyGenerator keyGenerator) {
        this.keyGenerator = keyGenerator;
    }

    @Override
    public Result rotateSalts(RotatingSaltProvider.SaltSnapshot lastSnapshot,
                                                         Duration[] minAges,
                                                         double fraction) throws Exception {

        final Instant nextEffective = Instant.now().truncatedTo(ChronoUnit.DAYS).plusSeconds(DAY_IN_SECONDS);
        final Instant nextExpires = nextEffective.plusSeconds(DAY_IN_SECONDS * 7);
        if (nextEffective.equals(lastSnapshot.getEffective()) || nextEffective.isBefore(lastSnapshot.getEffective())) {
            return Result.noSnapshot("cannot create a new salt snapshot with effective timestamp equal or prior to that of an existing snapshot");
        }

        final Instant[] thresholds = Arrays.stream(minAges)
                .map(a -> nextEffective.minusSeconds(a.getSeconds()))
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
                .filter(i -> isBetween(entries[i].lastUpdated(), minLastUpdated, maxLastUpdated))
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
            nextSnapshot.getAllRotatingSalts()[i] = new SaltEntry(oldSalt.id(), oldSalt.hashedId(), lastUpdated, secret, null, null, null, null);
        }
        return nextSnapshot;
    }
}
