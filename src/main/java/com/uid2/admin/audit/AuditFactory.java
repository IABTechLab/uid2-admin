package com.uid2.admin.audit;

import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * AuditFactory controls the instantiation/creation of AuditMiddleware objects.
 * Depending on the needs of the specific implementation, the AuditFactory
 * can be implemented to always return the same AuditMiddleware object, create a new
 * AuditMiddleware object for every class that calls getAuditMiddleware(Class), or
 * exhibit some other behavior.
 */
public class AuditFactory {
    private static final Map<JsonObject, AuditMiddleware> middlewareMap = new HashMap<>();
    private static final Logger LOGGER = LoggerFactory.getLogger(AuditFactory.class);

    /**
     * Returns an AuditMiddleware object for the designated class to use.
     *
     * @return the designated AuditMiddleware object for the passed class.
     */
    public static AuditMiddleware getAuditMiddleware(JsonObject config){
        if(!middlewareMap.containsKey(config)){
            middlewareMap.put(config, new AuditMiddlewareImpl(new QLDBAuditWriter(config)));
        }
        return middlewareMap.get(config);
    }

}
