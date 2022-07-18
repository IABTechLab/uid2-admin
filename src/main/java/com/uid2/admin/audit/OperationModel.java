package com.uid2.admin.audit;

/**
 * Store the type of data, action that occurred, and any extra information necessary to know about the operation
 * also store the key representing the operation. The key should be designed such that all operations affecting the same
 * row(s) in the same table share the same key.
 */
public class OperationModel {

    public final Tables tableActioned;
    public final Actions actionTaken;
    public final AdminAuditModel.SummaryModel additionalInfo;
    public final String actionKey;

    public OperationModel(Tables tableActioned, Actions actionTaken, AdminAuditModel.SummaryModel additionalInfo, String actionKey){
        this.tableActioned = tableActioned;
        this.actionTaken = actionTaken;
        this.additionalInfo = additionalInfo;
        this.actionKey = actionKey;
    }
}
