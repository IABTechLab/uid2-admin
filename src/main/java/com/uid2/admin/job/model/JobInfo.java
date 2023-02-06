package com.uid2.admin.job.model;

import java.time.Instant;
import java.util.Objects;

public class JobInfo {
    private final String id;
    private final boolean executing;
    private final Instant addedToQueueAt;
    private final Instant startedExecutingAt;

    public JobInfo(Job job, boolean executing) {
        this.id = job.getId();
        this.executing = executing;
        this.addedToQueueAt = job.getAddedToQueueAt();
        this.startedExecutingAt = job.getStartedExecutingAt();
    }

    public String getId() {
        return id;
    }

    public boolean isExecuting() {
        return executing;
    }

    public Instant getAddedToQueueAt() {
        return addedToQueueAt;
    }

    public Instant getStartedExecutingAt() {
        return startedExecutingAt;
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
                && Objects.equals(addedToQueueAt, jobInfo.addedToQueueAt)
                && Objects.equals(startedExecutingAt, jobInfo.startedExecutingAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, executing, addedToQueueAt, startedExecutingAt);
    }
}
