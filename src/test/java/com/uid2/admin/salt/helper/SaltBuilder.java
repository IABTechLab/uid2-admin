package com.uid2.admin.salt.helper;

import com.uid2.admin.salt.TargetDate;
import com.uid2.shared.model.SaltEntry;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public class SaltBuilder {
    private static final AtomicInteger LAST_AUTO_ID = new AtomicInteger(0);

    private int id = LAST_AUTO_ID.incrementAndGet();
    private Instant lastUpdated = Instant.now();
    private Instant refreshFrom = Instant.now();
    private String currentSalt = null;
    private String previousSalt = null;

    private SaltBuilder() {
    }

    public static SaltBuilder start() {
        return new SaltBuilder();
    }

    public SaltBuilder id(int id) {
        this.id = id;
        return this;
    }

    public SaltBuilder lastUpdated(TargetDate lastUpdated) {
        this.lastUpdated = lastUpdated.asInstant();
        return this;
    }

    public SaltBuilder refreshFrom(TargetDate refreshFrom) {
        this.refreshFrom = refreshFrom.asInstant();
        return this;
    }

    public SaltBuilder currentSalt(String currentSalt) {
        this.currentSalt = currentSalt;
        return this;
    }

    public SaltBuilder previousSalt(String previousSalt) {
        this.previousSalt = previousSalt;
        return this;
    }

    public SaltEntry build() {
        return new SaltEntry(
                id,
                Integer.toString(id),
                lastUpdated.toEpochMilli(),
                currentSalt == null ? "salt " + id : currentSalt,
                refreshFrom.toEpochMilli(),
                previousSalt,
                null,
                null
        );
    }
}
