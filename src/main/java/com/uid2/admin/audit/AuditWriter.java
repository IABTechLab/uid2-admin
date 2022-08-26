package com.uid2.admin.audit;

import java.util.Collection;

/**
 * AuditWriter is responsible for the logic to write out to designated logging databases.
 */
public interface AuditWriter {
    /**
     * Logs the information in the AuditModel to an external database(s).
     * Does not log any information if model == null.
     *
     * @param model the AuditModel to write out.
     */
    boolean writeLogs(Collection<AuditModel> model);
}
