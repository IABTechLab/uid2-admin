package com.uid2.admin.job;

import com.uid2.admin.job.model.Job;
import com.uid2.admin.job.model.JobInfo;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class JobDispatcher {

    private static class Loader {
        public static final JobDispatcher INSTANCE = new JobDispatcher();
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(JobDispatcher.class);

    private final ExecutorService JOB_EXECUTOR = Executors.newSingleThreadExecutor();
    private final Queue<Job> JOB_QUEUE = new ConcurrentLinkedQueue<>();
    private final Object JOB_LOCK = new Object();
    private boolean started = false;
    private ScheduledExecutorService scheduler;
    private Job currentJob;

    private JobDispatcher() {}

    public static JobDispatcher getInstance() {
        return Loader.INSTANCE;
    }

    public void start(int interval, int maxRetries) {
        synchronized (JOB_LOCK) {
            if (!started) {
                LOGGER.info("Starting job dispatcher (Interval: {}ms | Max retries: {})", interval, maxRetries);
                scheduler = Executors.newScheduledThreadPool(1);
                scheduler.scheduleAtFixedRate(() -> run(maxRetries), 0, interval, TimeUnit.MILLISECONDS);
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
            JOB_QUEUE.clear();
            if (scheduler != null) {
                scheduler.shutdown();
            }
        }
    }

    public void clear() {
        LOGGER.info("Clearing job dispatcher");
        JOB_QUEUE.clear();
    }

    public void enqueue(Job job) {
        String id = job.getId();

        synchronized (JOB_LOCK) {
            if ((currentJob == null || !currentJob.getId().equals(id))
                    && JOB_QUEUE.stream().noneMatch(queuedJob -> queuedJob.getId().equals(id))) {
                LOGGER.info("Queueing new job: {}", id);
                JOB_QUEUE.add(job);
            } else {
                LOGGER.warn("Already queued job: {}", id);
            }
        }
    }

    public List<JobInfo> getJobQueueInfo() {
        List<JobInfo> jobInfos = new ArrayList<>();
        synchronized (JOB_LOCK) {
            if (isExecutingJob()) {
                jobInfos.add(new JobInfo(currentJob.getId(), true));
            }
            jobInfos.addAll(JOB_QUEUE.stream()
                    .map(job -> new JobInfo(job.getId(), false))
                    .collect(Collectors.toList()));
        }
        return jobInfos;
    }

    public Job getExecutingJob() {
        return currentJob;
    }

    public boolean isExecutingJob() {
        return currentJob != null;
    }

    public boolean isStarted() {
        return started;
    }

    private void run(int maxRetries) {
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
            LOGGER.info("Executing job: {} ({} jobs remaining in queue)", currentJobId, JOB_QUEUE.size());
        }

        JOB_EXECUTOR.execute(() -> {
            for (int retryCount = 1; retryCount <= maxRetries; retryCount++) {
                try {
                    currentJob.execute();
                    LOGGER.info("Job successfully executed: {}", currentJobId);
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

}
