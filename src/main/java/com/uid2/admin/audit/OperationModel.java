package com.uid2.admin.audit;

public class OperationModel {

    //Store the type of data, action that occurred, and any extra information necessary to know about the operation
    // also store the key representing the operation

    public final String tableActioned;
    public final Actions action;
    public final AdminAuditModel.AdditionalInfoModel additionalInfo;
    public final String key;

    public OperationModel(String tableActioned, Actions action, AdminAuditModel.AdditionalInfoModel additionalInfo, String key){
        this.tableActioned = tableActioned;
        this.action = action;
        this.additionalInfo = additionalInfo;
        this.key = key;
    }
}
