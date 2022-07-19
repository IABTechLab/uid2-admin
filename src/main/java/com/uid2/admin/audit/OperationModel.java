package com.uid2.admin.audit;

/**
 * Store the type of data, action that occurred, and any extra information necessary to know about the operation.
 * Also stores the itemKey representing the operation. itemKey should be designed such that all read/writes
 * affecting the same row(s) in the same table share the same value. It should be hashed in case the row identifier
 * itself is sensitive information.
 */
public class OperationModel {

    public final Type tableActioned;
    public final String itemActioned;
    public final Actions actionTaken;
    public final String itemKey;
    public final String itemHash;
    public final String summary;

    public OperationModel(Type tableActioned, String itemActioned, Actions actionTaken, String itemKey,
                          String itemHash, String summary){
        this.tableActioned = tableActioned;
        this.itemActioned = itemActioned;
        this.actionTaken = actionTaken;
        this.itemKey = itemKey;
        this.itemHash = itemHash;
        this.summary = summary;
    }
}
