package com.uid2.admin;

import com.uid2.admin.job.JobDispatcher;
import com.uid2.admin.job.model.Job;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

public class SampleTest {

    private final Logger LOGGER = LoggerFactory.getLogger(SampleTest.class);

    private final Object lock = new Object();

    class Sample {

        private final Logger LOGGER = LoggerFactory.getLogger(Sample.class);

        public Queue<Integer> queue = new LinkedList<>();
        public Integer curr = null;

        public void enqueue(int i) {
            boolean a;
            boolean b;
            synchronized (lock) {
                LOGGER.info("Acquiring lock 2");
                a = queue.stream().noneMatch(qi -> qi.equals(i));
//                Thread.sleep(1000L);
                b = curr == null || !curr.equals(i);
                LOGGER.info("Releasing lock 2");
            }

            if (a && b) {
                queue.add(i);
                LOGGER.info("Queued new int: {} {}", i, queue);
            } else if (!a) {
                LOGGER.warn("Already queued int: {} {}", i, queue);
            } else {
                LOGGER.warn("Already executing int: {} {}", i, queue);
            }
        }

    }

    static class SampleJob implements Job {

        private final Logger LOGGER = LoggerFactory.getLogger(SampleJob.class);

        public SampleJob() {}

        @Override
        public String getId() {
            return "Sample Job";
        }

        @Override
        public void execute() throws Exception {
            LOGGER.info("STARTING");
            Thread.sleep(5L * 1000L);
            LOGGER.info("ENDING");
        }

    }

    @Disabled
    @Test
    public void testSync() throws Exception {
        Sample sample = new Sample();

        sample.queue.add(2);
        LOGGER.info("Queue: {}", sample.queue);

        new Thread(() -> {
            try {
//                Thread.sleep(10L);

                synchronized (lock) {
                    LOGGER.info("Acquiring lock 1");
                    sample.queue.remove(2);
                    LOGGER.info("Removing 2 from queue: {}", sample.queue);
                    LOGGER.info("Releasing lock 1");
                }
            } catch (Exception e) {
            }
        }).start();
        new Thread(() -> sample.enqueue(2)).start();

        Thread.sleep(1000L);
        LOGGER.info("{}", sample.queue);
    }

    @Disabled
    @Test
    public void testQueue() {
        Queue<Integer> queue = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < 10; i++) {
            queue.add(i);
        }
        LOGGER.info("{}", queue);

        new Thread(() -> {
            try {
                Thread.sleep(200L);
            } catch (Exception e) {
            }
            LOGGER.info("TEST");
            queue.remove(1);
        }).start();
        new Thread(() -> {
            try {
                Thread.sleep(250L);
            } catch (Exception e) {
            }
            LOGGER.info("TEST2");
            queue.remove(2);
        }).start();
        new Thread(() -> {
            try {
                Thread.sleep(3900L);
            } catch (Exception e) {
            }
            LOGGER.info("TEST3");
            queue.remove(9);
        }).start();

        queue.stream()
                .map(Object::toString)
                .map(i -> {
                    try {
                        Thread.sleep(500L);
                    } catch (InterruptedException e) {
                    }
                    return i;
                })
                .peek(LOGGER::info)
                .collect(Collectors.toList());
        LOGGER.info("{}", queue);
    }

    @Disabled
    @Test
    public void testJobDispatcher() throws Exception {
        JobDispatcher jobDispatcher = JobDispatcher.getInstance();
        jobDispatcher.start(1000, 3);
        jobDispatcher.start(2000, 3);
        jobDispatcher.start(3000, 3);


        jobDispatcher.enqueue(new SampleJob());
        jobDispatcher.enqueue(new SampleJob());
        Thread.sleep(3L * 1000L);
        jobDispatcher.enqueue(new SampleJob());
        jobDispatcher.enqueue(new SampleJob());
    }

}
