package com.uid2.admin.store;

import java.time.Instant;

public interface Clock {
    Long getEpochSecond();
    Long getEpochMillis();
    Instant now();
}
