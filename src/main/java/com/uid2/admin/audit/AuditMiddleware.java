package com.uid2.admin.audit;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * AuditMiddleware objects are intended to be attached to any endpoint that the system
 * wants to keep track of via logging to an external source.
 */
public interface AuditMiddleware {

    /**
     * Handle to attach to any route whose actions require logging.
     * Dev: method signature may need to be modified
     *
     * @param handler the method that performs the action that requires logging,
     *                converted into {@literal Handler<RoutingContext>} via SAM
     * @return a new method that logs in addition to executing the method
     * specified in handler.
     */
    Handler<RoutingContext> handle(AuditHandler<RoutingContext> handler);

    /**
     * Logs the information in the AuditModel to an external database(s).
     * Does not log any information if model == null.
     *
     * @param model the AuditModel to write out.
     */
    void writeLog(AuditModel model);
}
