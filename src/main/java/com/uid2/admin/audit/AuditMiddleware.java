package com.uid2.admin.audit;

import com.uid2.admin.vertx.service.IService;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

import java.util.List;

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
    Handler<RoutingContext> handle(AuditHandler<RoutingContext> handler);

    /**
     * Executes any necessary initialization of the AuditMiddleware and/or the database
     * it logs to.
     * @param services A list of all services that can be called by the client.
     */
    void startup(List<IService> services);
}
