package com.uid2.admin.job;

import com.uid2.admin.job.model.Job;
import com.uid2.admin.job.model.JobInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class JobDispatcherTest {

    private class TestJob implements Job {

        @Override
        public String getId() {
            return "id";
        }

        @Override
        public void execute() {
            executionCount++;
        }

    }

    private class Test2Job implements Job {

        @Override
        public String getId() {
            return "2 id";
        }

        @Override
        public void execute() {
            executionCount++;
        }

    }

    private class TestLongRunningJob implements Job {

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

    private class TestExceptionJob implements Job {

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
    private final JobDispatcher jobDispatcher = JobDispatcher.getInstance();
    private int executionCount = 0;

    @BeforeEach
    public void setup() {
        executionCount = 0;
    }

    @AfterEach
    public void teardown() throws Exception {
        jobDispatcher.shutdown();

        Job executingJob = jobDispatcher.getExecutingJob();
        if (executingJob instanceof TestLongRunningJob) {
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
        expected.add(new JobInfo("id", false));

        assertThat(jobDispatcher.getJobQueueInfo())
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("executionTime")
                .isEqualTo(expected);
    }

    @Test
    public void testGetJobQueueInfoWithDuplicateJobs() {
        jobDispatcher.enqueue(new TestJob());
        jobDispatcher.enqueue(new TestJob());
        jobDispatcher.enqueue(new TestJob());
        jobDispatcher.enqueue(new TestJob());
        jobDispatcher.enqueue(new TestJob());

        List<JobInfo> expected = new ArrayList<>();
        expected.add(new JobInfo("id", false));

        assertThat(jobDispatcher.getJobQueueInfo())
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("executionTime")
                .isEqualTo(expected);
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
        expected.add(new JobInfo("id", false));
        expected.add(new JobInfo("long running id", false));
        expected.add(new JobInfo("2 id", false));
        expected.add(new JobInfo("exception id", false));

        assertThat(jobDispatcher.getJobQueueInfo())
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("executionTime")
                .isEqualTo(expected);
    }

    @Test
    public void testGetJobQueueInfoWithExecutingJob() throws Exception {
        jobDispatcher.enqueue(new TestLongRunningJob());
        jobDispatcher.enqueue(new TestJob());
        jobDispatcher.start(INTERVAL_TIME, 3);
        Thread.sleep(INTERVAL_TIME);

        List<JobInfo> expected = new ArrayList<>();
        expected.add(new JobInfo("long running id", true));
        expected.add(new JobInfo("id", false));

        assertTrue(jobDispatcher.isExecutingJob());
        assertEquals(1, executionCount);
        assertThat(jobDispatcher.getJobQueueInfo())
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("executionTime")
                .isEqualTo(expected);
    }

    @Test
    public void testJobExecutionWithOneJob() throws Exception {
        jobDispatcher.enqueue(new TestJob());
        jobDispatcher.start(INTERVAL_TIME, 3);
        Thread.sleep(INTERVAL_TIME);

        assertFalse(jobDispatcher.isExecutingJob());
        assertEquals(1, executionCount);
        assertTrue(jobDispatcher.getJobQueueInfo().isEmpty());
    }

    @Test
    public void testJobExecutionWithDuplicateJobs() throws Exception {
        jobDispatcher.enqueue(new TestJob());
        jobDispatcher.enqueue(new TestJob());
        jobDispatcher.start(INTERVAL_TIME, 3);
        Thread.sleep(INTERVAL_TIME);

        assertFalse(jobDispatcher.isExecutingJob());
        assertEquals(1, executionCount);
        assertTrue(jobDispatcher.getJobQueueInfo().isEmpty());
    }

    @Test
    public void testJobExecutionWithDifferentUniqueJobs() throws Exception {
        jobDispatcher.enqueue(new TestJob());
        jobDispatcher.enqueue(new Test2Job());
        jobDispatcher.start(INTERVAL_TIME, 3);
        Thread.sleep(INTERVAL_TIME*2);

        assertFalse(jobDispatcher.isExecutingJob());
        assertEquals(2, executionCount);
        assertTrue(jobDispatcher.getJobQueueInfo().isEmpty());
    }

    @Test
    public void testJobExecutionRetry() throws Exception {
        jobDispatcher.enqueue(new TestExceptionJob());
        jobDispatcher.start(INTERVAL_TIME, 3);
        Thread.sleep(INTERVAL_TIME);

        assertFalse(jobDispatcher.isExecutingJob());
        assertEquals(3, executionCount);
        assertTrue(jobDispatcher.getJobQueueInfo().isEmpty());
    }

}
