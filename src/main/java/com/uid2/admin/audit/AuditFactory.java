package com.uid2.admin.audit;

/**
 * AuditFactory controls the instantiation/creation of AuditMiddleware objects.
 * Depending on the needs of the specific implementation, the AuditFactory
 * can be implemented to always return the same AuditMiddleware object, create a new
 * AuditMiddleware object for every class that calls getAuditMiddleware(Class), or
 * exhibit some other behavior.
 */
public class AuditFactory {
    private static final AuditWriter auditWriter = new QLDBAuditWriter();

    public static final AuditMiddleware auditMiddleware = new QLDBAuditMiddleware(auditWriter);

    /**
     * Returns an AuditMiddleware object for the designated class to use.
     *
     * @param clazz the class that requires an AuditMiddleware object.
     * @return the designated AuditMiddleware object for the passed class.
     */
    public static AuditMiddleware getAuditMiddleware(Class<?> clazz){
        return auditMiddleware;
    }
}
