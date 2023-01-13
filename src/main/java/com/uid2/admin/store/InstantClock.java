package com.uid2.admin.store;

import java.time.Instant;

public class InstantClock implements Clock {
    @Override
    public Long getEpochSecond() {
        return Instant.now().getEpochSecond();
    }

    @Override
    public Long getEpochMillis() {
        return Instant.now().toEpochMilli();
    }
}
