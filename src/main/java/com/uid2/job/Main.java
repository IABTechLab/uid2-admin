package com.uid2.job;

import com.uid2.job.custom.JobDispatcher;
import com.uid2.job.custom.MyJob;

public class Main {

    public static void main(String[] args) throws Exception {
        JobDispatcher jobDispatcher = JobDispatcher.getInstance(1000, 3);

        jobDispatcher.enqueue(new MyJob());
        jobDispatcher.enqueue(new MyJob());
        Thread.sleep(3L * 1000L);
        jobDispatcher.enqueue(new MyJob());
        jobDispatcher.enqueue(new MyJob());
    }

}
