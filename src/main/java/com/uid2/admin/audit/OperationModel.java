package com.uid2.admin.audit;

/**
 * Store the type of data, action that occurred, and any extra information necessary to know about the operation.
 * Also stores the itemKey representing the operation. itemKey should be designed such that all read/writes
 * affecting the same row(s) in the same table share the same value. It should be hashed in case the row identifier
 * itself is sensitive information.
 */
public class OperationModel {
    public final Type itemType;
    public final String itemKey;
    public final Actions actionTaken;
    public final String itemHash;
    public final String summary;

    public OperationModel(Type itemType, String itemKey, Actions actionTaken,
                          String itemHash, String summary){
        this.itemType = itemType;
        this.itemKey = itemKey;
        this.actionTaken = actionTaken;
        this.itemHash = itemHash;
        this.summary = summary;
    }
}
