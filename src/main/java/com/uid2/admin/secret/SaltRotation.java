package com.uid2.admin.secret;

import com.uid2.shared.model.SaltEntry;
import com.uid2.shared.secret.IKeyGenerator;
import com.uid2.shared.store.salt.RotatingSaltProvider;

import com.uid2.shared.store.salt.RotatingSaltProvider.SaltSnapshot;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

public class SaltRotation implements ISaltRotation {
    private final IKeyGenerator keyGenerator;

    public SaltRotation(IKeyGenerator keyGenerator) {
        this.keyGenerator = keyGenerator;
    }

    @Override
    public Result rotateSalts(RotatingSaltProvider.SaltSnapshot lastSnapshot,
                                                        Duration[] minAges,
                                                        double fraction,
                                                        LocalDate targetDate) throws Exception {

        final Instant nextEffective = targetDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        final Instant nextExpires = nextEffective.plus(7, ChronoUnit.DAYS);
        if (nextEffective.equals(lastSnapshot.getEffective()) || nextEffective.isBefore(lastSnapshot.getEffective())) {
            return Result.noSnapshot("cannot create a new salt snapshot with effective timestamp equal or prior to that of an existing snapshot");
        }

        List<Integer> saltIndexesToRotate = pickSaltIndexesToRotate(lastSnapshot, nextEffective, minAges, fraction);
        if (saltIndexesToRotate.isEmpty()) {
            return Result.noSnapshot("all salts are below min rotation age");
        }

        var updatedSalts = updateSalts(lastSnapshot.getAllRotatingSalts(), saltIndexesToRotate, nextEffective.toEpochMilli());

        SaltSnapshot nextSnapshot = new SaltSnapshot(
                nextEffective,
                nextExpires,
                updatedSalts,
                lastSnapshot.getFirstLevelSalt());
        return Result.fromSnapshot(nextSnapshot);
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

        return new SaltEntry(
                oldSalt.id(),
                oldSalt.hashedId(),
                lastUpdated,
                currentSalt,
                null,
                null,
                null,
                null
        );
    }

    private List<Integer> pickSaltIndexesToRotate(
            SaltSnapshot lastSnapshot,
            Instant nextEffective,
            Duration[] minAges,
            double fraction) {
        final Instant[] thresholds = Arrays.stream(minAges)
                .map(age -> nextEffective.minusSeconds(age.getSeconds()))
                .sorted()
                .toArray(Instant[]::new);
        final int maxSalts = (int) Math.ceil(lastSnapshot.getAllRotatingSalts().length * fraction);
        final List<Integer> indexesToRotate = new ArrayList<>();

        Instant minLastUpdated = Instant.ofEpochMilli(0);
        for (Instant threshold : thresholds) {
            if (indexesToRotate.size() >= maxSalts) break;
            addIndexesToRotate(
                    indexesToRotate,
                    lastSnapshot,
                    minLastUpdated.toEpochMilli(),
                    threshold.toEpochMilli(),
                    maxSalts - indexesToRotate.size()
            );
            minLastUpdated = threshold;
        }
        return indexesToRotate;
    }

    private void addIndexesToRotate(List<Integer> entryIndexes,
                                    SaltSnapshot lastSnapshot,
                                    long minLastUpdated,
                                    long maxLastUpdated,
                                    int maxIndexes) {
        final SaltEntry[] entries = lastSnapshot.getAllRotatingSalts();
        final List<Integer> candidateIndexes = IntStream.range(0, entries.length)
                .filter(i -> isBetween(entries[i].lastUpdated(), minLastUpdated, maxLastUpdated))
                .boxed()
                .collect(toList());
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

}
