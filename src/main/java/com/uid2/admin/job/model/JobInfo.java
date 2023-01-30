package com.uid2.admin.job.model;

import java.time.Instant;
import java.util.Objects;

public class JobInfo {
    private final String id;
    private final boolean executing;
    private final Instant enqueueTime;
    private final Instant executionTime;

    public JobInfo(Job job, boolean executing) {
        this.id = job.getId();
        this.executing = executing;
        this.enqueueTime = job.getEnqueueTime();
        this.executionTime = job.getExecutionTime();
    }

    public String getId() {
        return id;
    }

    public boolean isExecuting() {
        return executing;
    }

    public Instant getEnqueueTime() {
        return enqueueTime;
    }

    public Instant getExecutionTime() {
        return executionTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        JobInfo jobInfo = (JobInfo) o;
        return executing == jobInfo.executing
                && id.equals(jobInfo.id)
                && Objects.equals(enqueueTime, jobInfo.enqueueTime)
                && Objects.equals(executionTime, jobInfo.executionTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, executing, enqueueTime, executionTime);
    }
}
