package com.uid2.job.model;

import java.time.Instant;

public class JobInfo {

    private final String id;
    private final Instant executionTime;

    public JobInfo(String id) {
        this.id = id;
        this.executionTime = Instant.now();
    }

    public String getId() {
        return id;
    }

    public Instant getExecutionTime() {
        return executionTime;
    }

}
