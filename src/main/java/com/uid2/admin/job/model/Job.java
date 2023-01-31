package com.uid2.admin.job.model;

import java.time.Instant;

public abstract class Job {
    private Instant addedToQueueAt;
    private Instant startedExecutingAt;

    public Instant getAddedToQueueAt() {
        return addedToQueueAt;
    }

    public void setAddedToQueueAt(Instant addedToQueueAt) {
        this.addedToQueueAt = addedToQueueAt;
    }

    public Instant getStartedExecutingAt() {
        return startedExecutingAt;
    }

    public void setStartedExecutingAt(Instant startedExecutingAt) {
        this.startedExecutingAt = startedExecutingAt;
    }

    abstract public String getId();
    abstract public void execute() throws Exception;
}
