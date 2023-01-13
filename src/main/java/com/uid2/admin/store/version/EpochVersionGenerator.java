package com.uid2.admin.store.version;

import com.uid2.admin.store.Clock;

public class EpochVersionGenerator implements VersionGenerator {
    private final Clock clock;

    public EpochVersionGenerator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Long getVersion() {
        return clock.getEpochMillis();
    }
}
