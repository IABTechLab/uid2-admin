package com.uid2.admin.audit;

import io.vertx.ext.web.RoutingContext;

import java.util.List;
import java.util.function.Function;

/**
 * AuditMiddleware objects are intended to be attached to any endpoint that the system
 * wants to keep track of via logging to an external source, and pass logging data to an AuditWriter object.
 */
public interface IAuditMiddleware {

    /**
     * Handle to attach to any route whose actions require logging.
     *
     * @param rc the RoutingContext of the endpoint access that initiated the request.
     * @return a function that takes a List of OperationModels and returns whether or not the audit
     * writing was successful.
     */
    Function<List<OperationModel>, Boolean> handle(RoutingContext rc);
}
