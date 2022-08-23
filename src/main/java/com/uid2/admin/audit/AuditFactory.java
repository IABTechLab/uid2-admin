package com.uid2.admin.audit;

import io.vertx.core.json.JsonObject;

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

    /**
     * Returns an AuditMiddleware object with the designated configuration. If one does
     * not already exist, creates a new AuditMiddleware object using the configuration.
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
