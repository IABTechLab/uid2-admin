package com.uid2.job.model;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class SampleJob implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(SampleJob.class);

    public SampleJob() {
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
