package com.uid2.admin.vertx.service;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.job.JobDispatcher;
import com.uid2.admin.vertx.JsonUtil;
import com.uid2.shared.auth.Role;
import com.uid2.shared.middleware.AuthMiddleware;
import io.vertx.ext.web.Router;

public class JobDispatcherService implements IService {
    private final ObjectWriter jsonWriter = JsonUtil.createJsonWriter();
    private final AuthMiddleware auth;
    private final JobDispatcher jobDispatcher;

    public JobDispatcherService(AuthMiddleware auth, JobDispatcher jobDispatcher) {
        this.auth = auth;
        this.jobDispatcher = jobDispatcher;
    }

    @Override
    public void setupRoutes(Router router) {
        router.get("/api/job-dispatcher/current-job").blockingHandler(auth.handle((ctx) -> {
                    try {
                        ctx.response().end(jsonWriter.writeValueAsString(jobDispatcher.getExecutingJobInfo()));
                    } catch (Exception ex) {
                        ctx.fail(ex);
                    }
                },
                //can be other role
                Role.ADMINISTRATOR, Role.SECRET_MANAGER));

        router.get("/api/job-dispatcher/job-queue").blockingHandler(auth.handle((ctx) -> {
                    try {
                        ctx.response().end(jsonWriter.writeValueAsString(jobDispatcher.getJobQueueInfo()));
                    } catch (Exception ex) {
                        ctx.fail(ex);
                    }
                },
                //can be other role
                Role.ADMINISTRATOR, Role.SECRET_MANAGER));
    }
}
