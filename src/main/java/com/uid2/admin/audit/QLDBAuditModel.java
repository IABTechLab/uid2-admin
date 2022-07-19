package com.uid2.admin.audit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.vertx.core.json.JsonObject;

public class QLDBAuditModel implements AuditModel{

    /**
     * The table that the user accesses.
     */
    public final Type itemType;
    /**
     * An identifier for the row in the table that is accessed. Is null if more than one row is accessed at the same
     * time, e.g. listing all values in a table. If itemKey should be secret, hash before putting it into the model.
     */
    public final String itemKey;
    /**
     * Describes the action the user performed on the table (e.g. read ("GET"), write ("CREATE"/"DELETE")...)
     */
    public final Actions actionTaken;
    /**
     * The IP of the user making the HTTP request.
     */
    public final String clientIP;
    /**
     * The email of the user making the HTTP request.
     */
    public final String userEmail;
    /**
     * The server that processed the HTTP request.
     */
    public final String hostNode;
    /**
     * The time that the HTTP request was received by the server.
     */
    public final long timeEpochSecond;
    /**
     * The hash of the entire item being accessed/modified by the user. Is null if more than one
     * row is accessed at the same time (which should only be get/list queries).
     */
    public final String itemHash;
    /**
     * Names the exact operation done to the item (e.g. rekeyed, revealed, disabled, etc.)
     */
    public final String summary;

    public QLDBAuditModel(Type itemType, String itemActioned, Actions actionTaken, String clientIP,
                          String userEmail, String hostNode, long timeEpochSecond, String itemHash, String summary){
        this.itemType = itemType;
        this.itemKey = itemActioned;
        this.actionTaken = actionTaken;
        this.clientIP = clientIP;
        this.userEmail = userEmail;
        this.hostNode = hostNode;
        this.timeEpochSecond = timeEpochSecond;
        this.itemHash = itemHash;
        this.summary = summary;
    }

    @Override
    public JsonObject writeToJson() {
        Gson gson = new GsonBuilder().serializeNulls().create();
        return new JsonObject(gson.toJson(this));
    }

    @Override
    public String writeToString() {
        return writeToJson().toString();
    }
}
