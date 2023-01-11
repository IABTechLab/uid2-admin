package com.uid2.admin.job;

import com.uid2.admin.job.model.SampleJob;

public class Main {

    public static void main(String[] args) throws Exception {
        JobDispatcher jobDispatcher = JobDispatcher.getInstance();
        jobDispatcher.start(1000, 3);

        jobDispatcher.enqueue(new SampleJob());
        jobDispatcher.enqueue(new SampleJob());
        Thread.sleep(3L * 1000L);
        jobDispatcher.enqueue(new SampleJob());
        jobDispatcher.enqueue(new SampleJob());
    }

}
