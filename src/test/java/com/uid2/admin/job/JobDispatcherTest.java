package com.uid2.admin.job;

import com.uid2.admin.job.model.Job;
import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JobDispatcherTest {

    private class TestJob implements Job {

        @Override
        public String getId() {
            return "id";
        }

        @Override
        public void execute() {
            executionCount.incrementAndGet();
        }

    }

    private class TestExceptionJob implements Job {

        @Override
        public String getId() {
            return "exception id";
        }

        @Override
        public void execute() throws Exception {
            executionCount.incrementAndGet();
            throw new Exception("Test");
        }

    }

    private final JobDispatcher jobDispatcher = JobDispatcher.getInstance();
    private AtomicInteger executionCount = new AtomicInteger();

    @BeforeEach
    public void setup() {
        executionCount = new AtomicInteger();
    }

    @AfterEach
    public void teardown() {
        jobDispatcher.stop();
    }

    @Test
    public void testEmptyJobQueueBeforeStart() {
        assertTrue(jobDispatcher.getJobQueueInfo().isEmpty());
    }

    @Test
    public void testJobQueueInfoWithOneJob() {
        jobDispatcher.enqueue(new TestJob());

        assertEquals("id", jobDispatcher.getJobQueueInfo().get(0).getId());
    }

    @Test
    public void testJobQueueInfoWithDuplicateJob() {
        jobDispatcher.enqueue(new TestJob());
        jobDispatcher.enqueue(new TestJob());
        jobDispatcher.enqueue(new TestJob());
        jobDispatcher.enqueue(new TestJob());
        jobDispatcher.enqueue(new TestJob());

        assertEquals("id", jobDispatcher.getJobQueueInfo().get(0).getId());
        assertEquals(1, jobDispatcher.getJobQueueInfo().size());
    }

    @Test
    public void testJobExecutionWithOneJob() throws Exception {
        jobDispatcher.enqueue(new TestJob());
        jobDispatcher.start(10, 3);
        Thread.sleep(20);

        assertEquals(1, executionCount.get());
    }

    @Test
    public void testJobExecutionWithDuplicateJobs() throws Exception {
        jobDispatcher.enqueue(new TestJob());
        jobDispatcher.enqueue(new TestJob());
        jobDispatcher.start(10, 3);
        Thread.sleep(20);

        assertEquals(1, executionCount.get());
        assertTrue(jobDispatcher.getJobQueueInfo().isEmpty());
    }

    @Test
    public void testJobExecutionRetry() throws Exception {
        jobDispatcher.enqueue(new TestExceptionJob());
        jobDispatcher.start(10, 3);
        Thread.sleep(40);

        assertEquals(3, executionCount.get());
        assertTrue(jobDispatcher.getJobQueueInfo().isEmpty());
    }

}
