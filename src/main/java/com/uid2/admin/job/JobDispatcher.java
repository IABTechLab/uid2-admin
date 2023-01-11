package com.uid2.admin.job;

import com.uid2.admin.job.model.Job;
import com.uid2.admin.job.model.JobInfo;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class JobDispatcher {

    private static class Loader {
        public static final JobDispatcher INSTANCE = new JobDispatcher();
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(JobDispatcher.class);

    private final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(1);
    private final ExecutorService JOB_EXECUTOR = Executors.newSingleThreadExecutor();
    private final Queue<Job> JOB_QUEUE = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean STARTED = new AtomicBoolean(false);
    private final Object JOB_LOCK = new Object();
    private Job currentJob;

    private JobDispatcher() {}

    public static JobDispatcher getInstance() {
        return Loader.INSTANCE;
    }

    public void start(int interval, int maxRetries) {
        if (STARTED.compareAndSet(false, true)) {
            LOGGER.info("Starting job dispatcher (Interval: {}ms | Max retries: {})", interval, maxRetries);
            SCHEDULER.scheduleAtFixedRate(() -> run(maxRetries), 0, interval, TimeUnit.MILLISECONDS);
        } else {
            LOGGER.warn("Already started job dispatcher");
        }
    }

    public void stop() {
        LOGGER.info("Stopping job dispatcher");
        synchronized (JOB_LOCK) {
            JOB_QUEUE.clear();
        }
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
            if (currentJob != null) {
                jobInfos.add(new JobInfo(currentJob.getId(), true));
            }
            jobInfos.addAll(JOB_QUEUE.stream()
                    .map(job -> new JobInfo(job.getId(), false))
                    .collect(Collectors.toList()));
        }
        return jobInfos;
    }

    private void run(int maxRetries) {
        LOGGER.debug("Checking for jobs");
        if (JOB_QUEUE.isEmpty()) {
            LOGGER.debug("No jobs to run");
            return;
        }
        if (currentJob != null) {
            LOGGER.debug("Job already running: {}", currentJob.getId());
            return;
        }

        String currentJobId;
        synchronized (JOB_LOCK) {
            currentJob = JOB_QUEUE.poll();
            assert currentJob != null;
            currentJobId = currentJob.getId();
            LOGGER.info("Executing job: {} ({} jobs remaining in queue)", currentJobId, JOB_QUEUE.size());
        }

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

            synchronized (JOB_LOCK) {
                currentJob = null;
            }
        });
    }

}
