package com.uid2.admin.vertx.service;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.auth.AdminAuthMiddleware;
import com.uid2.admin.job.JobDispatcher;
import com.uid2.admin.vertx.JsonUtil;
import com.uid2.shared.auth.Role;
import io.vertx.ext.web.Router;

import static com.uid2.admin.vertx.Endpoints.API_JOB_DISPATCHER_CURRENT_JOB;
import static com.uid2.admin.vertx.Endpoints.API_JOB_DISPATCHER_JOB_QUEUE;

public class JobDispatcherService implements IService {
    private final ObjectWriter jsonWriter = JsonUtil.createJsonWriter();
    private final AdminAuthMiddleware auth;
    private final JobDispatcher jobDispatcher;

    public JobDispatcherService(AdminAuthMiddleware auth, JobDispatcher jobDispatcher) {
        this.auth = auth;
        this.jobDispatcher = jobDispatcher;
    }

    @Override
    public void setupRoutes(Router router) {
        router.get(API_JOB_DISPATCHER_CURRENT_JOB.toString()).blockingHandler(auth.handle((ctx) -> {
                    try {
                        ctx.response().end(jsonWriter.writeValueAsString(jobDispatcher.getExecutingJobInfo()));
                    } catch (Exception ex) {
                        ctx.fail(ex);
                    }
                },
                //can be other role
            Role.MAINTAINER));

        router.get(API_JOB_DISPATCHER_JOB_QUEUE.toString()).blockingHandler(auth.handle((ctx) -> {
                    try {
                        ctx.response().end(jsonWriter.writeValueAsString(jobDispatcher.getJobQueueInfo()));
                    } catch (Exception ex) {
                        ctx.fail(ex);
                    }
                },
                //can be other role
            Role.MAINTAINER));
    }
}
