package com.uid2.admin.audit;

import java.util.Collection;

/**
 * Responsible for the initialization of the QLDB table and any initial entries it must contain.
 */
public interface IAuditInit {

    /**
     * Creates a table with the name specified in the config, with all entries as specified by modelList, and sets up
     * any necessary configuration.
     * @param modelList the models to add to the audit database.
     */
    void init(Collection<OperationModel> modelList);
}
