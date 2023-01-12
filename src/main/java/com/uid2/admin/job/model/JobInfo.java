package com.uid2.admin.job.model;

import java.time.Instant;

public class JobInfo {

    private final String id;
    private final boolean executing;
    private final Instant executionTime;

    public JobInfo(String id, boolean executing, Instant executionTime) {
        this.id = id;
        this.executing = executing;
        this.executionTime = executionTime;
    }

    public JobInfo(String id, boolean executing) {
        this(id, executing, Instant.now());
    }

    public String getId() {
        return id;
    }

    public boolean isExecuting() {
        return executing;
    }

    public Instant getExecutionTime() {
        return executionTime;
    }

}
