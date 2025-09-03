package com.uid2.admin.salt.helper;

import com.uid2.admin.salt.TargetDate;
import com.uid2.shared.model.SaltEntry;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public final class SaltBuilder {
    private static final AtomicInteger LAST_AUTO_ID = new AtomicInteger(0);

    private int id = LAST_AUTO_ID.incrementAndGet();
    private Instant lastUpdated = Instant.now();
    private Instant refreshFrom = Instant.now();
    private String currentSalt = null;
    private String previousSalt = null;
    private SaltEntry.KeyMaterial currentKeySalt = null;
    private SaltEntry.KeyMaterial previousKeySalt = null;

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

    public SaltBuilder lastUpdated(Instant lastUpdated) {
        this.lastUpdated = lastUpdated;
        return this;
    }

    public SaltBuilder refreshFrom(TargetDate refreshFrom) {
        this.refreshFrom = refreshFrom.asInstant();
        return this;
    }

    public SaltBuilder refreshFrom(Instant refreshFrom) {
        this.refreshFrom = refreshFrom;
        return this;
    }

    public SaltBuilder currentSalt() {
        this.currentSalt =  "salt " + id;
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

    public SaltBuilder currentKeySalt(int keyId) {
        return this.currentKeySalt(keyId, "currentKeyKey" + id, "currentKeySalt" + id);
    }

    public SaltBuilder previousKeySalt(int keyId) {
        return this.previousKeySalt(keyId, "previousKeyKey" + id, "previousKeySalt" + id);
    }

    public SaltBuilder currentKeySalt(int keyId, String key, String salt) {
        this.currentKeySalt = new SaltEntry.KeyMaterial(keyId, key, salt);
        return this;
    }

    public SaltBuilder previousKeySalt(int keyId, String key, String salt) {
        this.previousKeySalt = new SaltEntry.KeyMaterial(keyId, key, salt);
        return this;
    }

    public SaltEntry build() {
        return new SaltEntry(
                id,
                Integer.toString(id),
                lastUpdated.toEpochMilli(),
                currentSalt,
                refreshFrom.toEpochMilli(),
                previousSalt,
                currentKeySalt,
                previousKeySalt
        );
    }
}
