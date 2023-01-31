package com.uid2.admin.job;

import com.uid2.admin.job.model.Job;
import com.uid2.admin.job.model.JobInfo;
import com.uid2.admin.store.Clock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JobDispatcherTest {
    private class TestJob extends Job {
        @Override
        public String getId() {
            return "id";
        }

        @Override
        public void execute() {
            executionCount++;
        }
    }

    private class Test2Job extends Job {
        @Override
        public String getId() {
            return "2 id";
        }

        @Override
        public void execute() {
            executionCount++;
        }
    }

    private class TestLongRunningJob extends Job {
        @Override
        public String getId() {
            return "long running id";
        }

        @Override
        public void execute() throws Exception {
            executionCount++;
            Thread.sleep(LONG_RUNNING_TIME);
        }
    }

    private class TestExceptionJob extends Job {
        @Override
        public String getId() {
            return "exception id";
        }

        @Override
        public void execute() throws Exception {
            executionCount++;
            throw new Exception("Test");
        }
    }

    private static final int INTERVAL_TIME = 50;
    private static final int LONG_RUNNING_TIME = INTERVAL_TIME*3;
    private Clock clock;
    private JobDispatcher jobDispatcher;
    private int executionCount = 0;

    @BeforeEach
    public void setup() {
        executionCount = 0;

        clock = mock(Clock.class);
        when(clock.now()).thenReturn(Instant.EPOCH);
        jobDispatcher = new JobDispatcher("test dispatcher", INTERVAL_TIME, 3, clock);
    }

    @AfterEach
    public void teardown() throws Exception {
        jobDispatcher.shutdown();

        JobInfo executingJobInfo = jobDispatcher.getExecutingJobInfo();
        if (executingJobInfo != null && executingJobInfo.getId().equals("long running id")) {
            Thread.sleep(LONG_RUNNING_TIME);
        }
    }

    @Test
    public void testClearWithEmptyJobQueue() {
        jobDispatcher.clear();

        assertTrue(jobDispatcher.getJobQueueInfo().isEmpty());
    }

    @Test
    public void testClearWithNonEmptyJobQueue() {
        jobDispatcher.enqueue(new TestJob());
        jobDispatcher.clear();

        assertTrue(jobDispatcher.getJobQueueInfo().isEmpty());
    }

    @Test
    public void testShutdown() {
        jobDispatcher.enqueue(new TestExceptionJob());
        jobDispatcher.shutdown();

        assertFalse(jobDispatcher.isStarted());
        assertTrue(jobDispatcher.getJobQueueInfo().isEmpty());
    }

    @Test
    public void testGetJobQueueInfoWithEmptyJobQueue() {
        assertTrue(jobDispatcher.getJobQueueInfo().isEmpty());
    }

    @Test
    public void testGetJobQueueInfoWithOneJob() {
        jobDispatcher.enqueue(new TestJob());

        List<JobInfo> expected = new ArrayList<>();
        addJobInfo(expected, new TestJob(), false);

        assertEquals(expected, jobDispatcher.getJobQueueInfo());
    }

    @Test
    public void testGetJobQueueInfoWithDuplicateJobs() {
        jobDispatcher.enqueue(new TestJob());
        jobDispatcher.enqueue(new TestJob());
        jobDispatcher.enqueue(new TestJob());
        jobDispatcher.enqueue(new TestJob());
        jobDispatcher.enqueue(new TestJob());

        List<JobInfo> expected = new ArrayList<>();
        addJobInfo(expected, new TestJob(), false);

        assertEquals(expected, jobDispatcher.getJobQueueInfo());
    }

    @Test
    public void testGetJobQueueInfoWithDifferentUniqueJobs() {
        jobDispatcher.enqueue(new TestJob());
        jobDispatcher.enqueue(new TestLongRunningJob());
        jobDispatcher.enqueue(new Test2Job());
        jobDispatcher.enqueue(new TestJob());
        jobDispatcher.enqueue(new TestExceptionJob());
        jobDispatcher.enqueue(new TestLongRunningJob());
        jobDispatcher.enqueue(new TestExceptionJob());
        jobDispatcher.enqueue(new TestJob());
        jobDispatcher.enqueue(new TestLongRunningJob());

        List<JobInfo> expected = new ArrayList<>();
        addJobInfo(expected, new TestJob(), false);
        addJobInfo(expected, new TestLongRunningJob(), false);
        addJobInfo(expected, new Test2Job(), false);
        addJobInfo(expected, new TestExceptionJob(), false);

        assertEquals(expected, jobDispatcher.getJobQueueInfo());
    }

    @Test
    public void testGetJobQueueInfoWithExecutingJob() throws Exception {
        jobDispatcher.enqueue(new TestLongRunningJob());
        jobDispatcher.enqueue(new TestJob());
        jobDispatcher.start();
        Thread.sleep(INTERVAL_TIME);

        List<JobInfo> expected = new ArrayList<>();
        addJobInfo(expected, new TestLongRunningJob(), true);
        addJobInfo(expected, new TestJob(), false);

        assertTrue(jobDispatcher.isExecutingJob());
        assertEquals(1, executionCount);
        assertEquals(expected, jobDispatcher.getJobQueueInfo());
    }

    @Test
    public void testJobExecutionWithOneJob() throws Exception {
        jobDispatcher.enqueue(new TestJob());
        jobDispatcher.start();
        Thread.sleep(INTERVAL_TIME);

        assertFalse(jobDispatcher.isExecutingJob());
        assertEquals(1, executionCount);
        assertTrue(jobDispatcher.getJobQueueInfo().isEmpty());
    }

    @Test
    public void testJobExecutionWithDuplicateJobs() throws Exception {
        jobDispatcher.enqueue(new TestJob());
        jobDispatcher.enqueue(new TestJob());
        jobDispatcher.start();
        Thread.sleep(INTERVAL_TIME);

        assertFalse(jobDispatcher.isExecutingJob());
        assertEquals(1, executionCount);
        assertTrue(jobDispatcher.getJobQueueInfo().isEmpty());
    }

    @Test
    public void testJobExecutionWithDifferentUniqueJobs() throws Exception {
        jobDispatcher.enqueue(new TestJob());
        jobDispatcher.enqueue(new Test2Job());
        jobDispatcher.start();
        Thread.sleep(INTERVAL_TIME*2);

        assertFalse(jobDispatcher.isExecutingJob());
        assertEquals(2, executionCount);
        assertTrue(jobDispatcher.getJobQueueInfo().isEmpty());
    }

    @Test
    public void testJobExecutionRetry() throws Exception {
        jobDispatcher.enqueue(new TestExceptionJob());
        jobDispatcher.start();
        Thread.sleep(INTERVAL_TIME);

        assertFalse(jobDispatcher.isExecutingJob());
        assertEquals(3, executionCount);
        assertTrue(jobDispatcher.getJobQueueInfo().isEmpty());
    }

    @Test
    public void testJobExecutionDurationWithNoJobInQueueAndNoExecutingJob() {
        assertEquals(0, jobDispatcher.getExecutionDuration());
    }

    @Test
    public void testJobExecutionDurationWithJobsInQueueNoExecutingJob() {
        jobDispatcher.enqueue(new TestJob());

        assertEquals(0, jobDispatcher.getExecutionDuration());
    }

    @Test
    public void testJobExecutionDuration() throws Exception {
        when(clock.now())
                .thenReturn(Instant.EPOCH) // For addedToQueueAt
                .thenReturn(Instant.EPOCH) // For startedExecutingAt
                .thenReturn(Instant.EPOCH.plusMillis(1000)); // For actual execution duration calculation

        jobDispatcher.enqueue(new TestLongRunningJob());
        jobDispatcher.start();
        Thread.sleep(INTERVAL_TIME);

        assertEquals(1000, jobDispatcher.getExecutionDuration());
    }

    private void addJobInfo(List<JobInfo> jobInfos, Job job, boolean executing) {
        job.setAddedToQueueAt(Instant.EPOCH);
        if (executing) {
            job.setStartedExecutingAt(Instant.EPOCH);
        }
        jobInfos.add(new JobInfo(job, executing));
    }
}
