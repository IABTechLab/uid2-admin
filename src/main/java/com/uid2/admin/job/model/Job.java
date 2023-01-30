package com.uid2.admin.job.model;

import java.time.Instant;

public abstract class Job {
    private Instant enqueueTime;
    private Instant executionTime;

    public Instant getEnqueueTime() {
        return enqueueTime;
    }

    public void setEnqueueTime(Instant enqueueTime) {
        this.enqueueTime = enqueueTime;
    }

    public Instant getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(Instant executionTime) {
        this.executionTime = executionTime;
    }

    abstract public String getId();
    abstract public void execute() throws Exception;
}
