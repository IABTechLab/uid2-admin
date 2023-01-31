package com.uid2.admin.job;

import com.uid2.admin.job.model.Job;
import com.uid2.admin.job.model.JobInfo;
import com.uid2.admin.store.Clock;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Metrics;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class JobDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobDispatcher.class);

    private final ExecutorService jobExecutor = Executors.newSingleThreadExecutor();
    private final Queue<Job> jobQueue = new ConcurrentLinkedQueue<>();
    private final Object jobLock = new Object();

    private final String id;
    private final int intervalMs;
    private final int maxRetries;
    private final Clock clock;

    private boolean started = false;
    private Job currentJob = null;
    private ScheduledExecutorService scheduler;

    public JobDispatcher(
            String id,
            int intervalMs,
            int maxRetries,
            Clock clock) {
        this.id = id;
        this.intervalMs = intervalMs;
        this.maxRetries = maxRetries;
        this.clock = clock;

        Gauge.builder("uid2.job_dispatcher.execution_duration_ms", this::getExecutionTime)
                .tag("job_dispatcher", id)
                .description("gauge for " + id + " execution time")
                .register(Metrics.globalRegistry);
    }

    public void start() {
        synchronized (jobLock) {
            if (!started) {
                LOGGER.info("Starting job dispatcher (Interval: {}ms | Max retries: {})", intervalMs, maxRetries);
                scheduler = Executors.newScheduledThreadPool(1);
                scheduler.scheduleAtFixedRate(this::executeNextJob, 0, intervalMs, TimeUnit.MILLISECONDS);
                started = true;
            } else {
                LOGGER.warn("Already started job dispatcher");
            }
        }
    }

    public void shutdown() {
        LOGGER.info("Shutting down job dispatcher");
        synchronized (jobLock) {
            started = false;
            currentJob = null;
            jobQueue.clear();

            if (scheduler != null) {
                scheduler.shutdown();
                scheduler = null;
            }
        }
    }

    public void clear() {
        LOGGER.info("Clearing job dispatcher queue");
        jobQueue.clear();
    }

    public void enqueue(Job job) {
        String id = job.getId();

        synchronized (jobLock) {
            if ((currentJob == null || !currentJob.getId().equals(id))
                    && jobQueue.stream().noneMatch(queuedJob -> queuedJob.getId().equals(id))) {
                LOGGER.info("Queueing new job: {}", id);
                jobQueue.add(job);
                job.setAddedToQueueAt(clock.now());
            } else {
                LOGGER.warn("Already queued job: {}", id);
            }
        }
    }

    public void executeNextJob() {
        String currentJobId;

        synchronized (jobLock) {
            LOGGER.debug("Checking for jobs");
            if (jobQueue.isEmpty()) {
                LOGGER.debug("No jobs to run");
                return;
            }
            if (currentJob != null) {
                LOGGER.debug("Job already running: {}", currentJob.getId());
                return;
            }

            currentJob = jobQueue.poll();
            assert currentJob != null;
            currentJobId = currentJob.getId();
            currentJob.setStartedExecutingAt(clock.now());
            LOGGER.info("Executing job: {} ({} jobs remaining in queue)", currentJobId, jobQueue.size());
        }

        jobExecutor.execute(() -> {
            for (int retryCount = 1; retryCount <= maxRetries; retryCount++) {
                try {
                    long before = System.currentTimeMillis();
                    currentJob.execute();
                    long after = System.currentTimeMillis();
                    long durationInSeconds = (after - before)/1000;
                    LOGGER.info("Job successfully executed: {} in {} seconds", currentJobId, durationInSeconds);
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

            synchronized (jobLock) {
                currentJob = null;
            }
        });
    }

    public List<JobInfo> getJobQueueInfo() {
        List<JobInfo> jobInfos = new ArrayList<>();

        synchronized (jobLock) {
            if (isExecutingJob()) {
                jobInfos.add(new JobInfo(currentJob, true));
            }
            jobInfos.addAll(jobQueue.stream()
                    .map(job -> new JobInfo(job, false))
                    .collect(Collectors.toList()));
        }
        return jobInfos;
    }

    public JobInfo getExecutingJobInfo() {
        synchronized (jobLock) {
            return currentJob == null ? null : new JobInfo(currentJob, true);
        }
    }

    public String getId() {
        return id;
    }

    public boolean isExecutingJob() {
        return currentJob != null;
    }

    public boolean isStarted() {
        return started;
    }

    private long getExecutionTime() {
        synchronized (jobLock) {
            return currentJob == null ? 0 : ChronoUnit.MILLIS.between(currentJob.getStartedExecutingAt(), Instant.now());
        }
    }
}
