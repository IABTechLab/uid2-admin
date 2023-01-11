package com.uid2.job.custom;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

public class JobDispatcher {

    private final static Logger LOGGER = LoggerFactory.getLogger(JobDispatcher.class);

    private static JobDispatcher INSTANCE;

    private final int dispatchInterval;
    private final int maxRetries;

    private final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(1);
    private final ExecutorService JOB_EXECUTOR = Executors.newSingleThreadExecutor();
    private final Queue<Job> JOB_QUEUE = new LinkedList<>();
    private Job currentJob;

    private JobDispatcher(int dispatchInterval, int maxRetries) {
        this.dispatchInterval = dispatchInterval;
        this.maxRetries = maxRetries;
        start();
    }

    synchronized public static JobDispatcher getInstance(int dispatchInterval, int maxRetries) {
        if (INSTANCE == null) {
            INSTANCE = new JobDispatcher(dispatchInterval, maxRetries);
        }
        return INSTANCE;
    }

    public List<Job> getJobQueue() {
        return Collections.unmodifiableList(new ArrayList<>(JOB_QUEUE));
    }

    synchronized public void enqueue(Job job) {
        String id = job.getId();
        if ((currentJob == null || !currentJob.getId().equals(id))
                && !isJobQueued(id)) {
            LOGGER.info("Queueing new job: {}", id);
            JOB_QUEUE.add(job);
        } else {
            LOGGER.warn("Already queued job: {}", id);
        }
    }

    private void start() {
        LOGGER.info("Starting job dispatcher (Dispatch interval: {}ms | Max retries: {})",
                dispatchInterval, maxRetries);
        SCHEDULER.scheduleAtFixedRate(this::runJobs, 0, dispatchInterval, TimeUnit.MILLISECONDS);
    }

    private void runJobs() {
        LOGGER.debug("Checking for jobs");
        if (JOB_QUEUE.isEmpty()) {
            LOGGER.debug("No jobs to run");
            return;
        }
        if (currentJob != null) {
            LOGGER.debug("Job already running: {}", currentJob.getId());
            return;
        }

        currentJob = JOB_QUEUE.poll();
        String currentJobId = currentJob.getId();
        LOGGER.info("Executing job: {} ({} jobs remaining in queue)", currentJobId, JOB_QUEUE.size());
        JOB_EXECUTOR.execute(() -> {
            for (int retryCount = 1; retryCount <= maxRetries; retryCount++) {
                try {
                    currentJob.execute();
                    break;
                } catch (Throwable t) {
                    if (retryCount < maxRetries) {
                        LOGGER.error(
                                String.format("Found error, retrying job: %s (%d/%d attempts)",
                                        currentJob.getId(), retryCount, maxRetries), t);
                    } else {
                        LOGGER.error(String.format("Found error, but reached max retries for job: %s", currentJobId), t);
                    }
                }
            }
            currentJob = null;
        });
    }

    private boolean isJobQueued(String id) {
        return JOB_QUEUE.stream().anyMatch(job -> job.getId().equals(id));
    }

}
