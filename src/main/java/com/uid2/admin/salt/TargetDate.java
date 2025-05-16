package com.uid2.admin.salt;

import com.uid2.shared.model.SaltEntry;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public class TargetDate {
    private final static long DAY_IN_MS = Duration.ofDays(1).toMillis();

    private final LocalDate date;
    private final long epochMs;
    private final Instant instant;
    private final String formatted;

    public TargetDate(LocalDate date) {
        this.instant = date.atStartOfDay().toInstant(ZoneOffset.UTC);
        this.date = date;
        this.epochMs = instant.toEpochMilli();
        this.formatted = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    public static TargetDate now() {
        return new TargetDate(LocalDate.now(Clock.systemUTC()));
    }

    public static TargetDate of(int year, int month, int day) {
        return new TargetDate(LocalDate.of(year, month, day));
    }

    public long asEpochMs() {
        return epochMs;
    }

    public Instant asInstant() {
        return instant;
    }

    // relative to this date
    public long saltAgeInDays(SaltEntry salt) {
        return (this.asEpochMs() - salt.lastUpdated()) / DAY_IN_MS;
    }

    public TargetDate plusDays(int days) {
        return new TargetDate(date.plusDays(days));
    }

    public TargetDate minusDays(int days) {
        return new TargetDate(date.minusDays(days));
    }

    @Override
    public String toString() {
        return formatted;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        TargetDate that = (TargetDate) o;
        return epochMs == that.epochMs;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(epochMs);
    }
}
