package com.uid2.job.custom;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class MyJob implements Job {

    private final static Logger LOGGER = LoggerFactory.getLogger(MyJob.class);

    public MyJob() {
    }

    @Override
    public String getId() {
        return "My Custom Job";
    }

    @Override
    public void execute() throws Exception {
        LOGGER.info("STARTING");
        Thread.sleep(5L * 1000L);
        LOGGER.info("ENDING");
    }

}
