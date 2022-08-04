package com.uid2.admin.audit;

import com.uid2.admin.vertx.service.IService;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

import java.util.List;
import java.util.function.Function;

/**
 * AuditMiddleware objects are intended to be attached to any endpoint that the system
 * wants to keep track of via logging to an external source, and pass logging data to an AuditWriter object.
 */
public interface AuditMiddleware {

    /**
     * Handle to attach to any route whose actions require logging.
     *
     * @param handler the method that performs the action that requires logging,
     *                converted into {@literal Handler<RoutingContext>} via SAM
     * @return a new method that logs in addition to executing the method
     * specified in handler.
     */
    Handler<RoutingContext> handle(Function<RoutingContext, List<OperationModel>> handler);
}
