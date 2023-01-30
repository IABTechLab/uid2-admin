package com.uid2.admin.job;

import com.uid2.admin.job.model.Job;
import com.uid2.admin.job.model.JobInfo;
import com.uid2.admin.store.Clock;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class JobDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobDispatcher.class);

    private final ExecutorService JOB_EXECUTOR = Executors.newSingleThreadExecutor();
    private final Queue<Job> JOB_QUEUE = new ConcurrentLinkedQueue<>();
    private final Object JOB_LOCK = new Object();

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
    }

    public void start() {
        synchronized (JOB_LOCK) {
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
        synchronized (JOB_LOCK) {
            started = false;
            currentJob = null;
            JOB_QUEUE.clear();

            if (scheduler != null) {
                scheduler.shutdown();
                scheduler = null;
            }
        }
    }

    public void clear() {
        LOGGER.info("Clearing job dispatcher queue");
        JOB_QUEUE.clear();
    }

    public void enqueue(Job job) {
        String id = job.getId();

        synchronized (JOB_LOCK) {
            if ((currentJob == null || !currentJob.getId().equals(id))
                    && JOB_QUEUE.stream().noneMatch(queuedJob -> queuedJob.getId().equals(id))) {
                LOGGER.info("Queueing new job: {}", id);
                JOB_QUEUE.add(job);
                job.setEnqueueTime(clock.now());
            } else {
                LOGGER.warn("Already queued job: {}", id);
            }
        }
    }

    public void executeNextJob() {
        String currentJobId;

        synchronized (JOB_LOCK) {
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
            assert currentJob != null;
            currentJobId = currentJob.getId();
            currentJob.setExecutionTime(clock.now());
            LOGGER.info("Executing job: {} ({} jobs remaining in queue)", currentJobId, JOB_QUEUE.size());
        }

        JOB_EXECUTOR.execute(() -> {
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

            synchronized (JOB_LOCK) {
                currentJob = null;
            }
        });
    }

    public List<JobInfo> getJobQueueInfo() {
        List<JobInfo> jobInfos = new ArrayList<>();

        synchronized (JOB_LOCK) {
            if (isExecutingJob()) {
                jobInfos.add(new JobInfo(currentJob, true));
            }
            jobInfos.addAll(JOB_QUEUE.stream()
                    .map(job -> new JobInfo(job, false))
                    .collect(Collectors.toList()));
        }
        return jobInfos;
    }

    public JobInfo getExecutingJobInfo() {
        synchronized (JOB_LOCK) {
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
}
