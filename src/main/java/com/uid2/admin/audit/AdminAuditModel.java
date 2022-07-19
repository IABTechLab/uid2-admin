package com.uid2.admin.audit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.vertx.core.json.JsonObject;

public class AdminAuditModel implements AuditModel{

    public final Type tableActioned;
    public final String itemActioned; //null value represents all items in table
    public final Actions actionTaken;
    public final String itemKey; // =/= itemHash; itemKey hashes a row identifier and operation; itemHash hashes the entire item
    public final String clientIP;
    public final String adminUser;
    public final String hostNode;
    public final long timeEpochSecond;
    public final String itemHash; //null value for operations affecting more than one row
    public final String summary;

    public AdminAuditModel(Type tableActioned, String itemActioned, Actions actionTaken, String itemKey, String clientIP,
                           String adminUser, String hostNode, long timeEpochSecond, String itemHash, String summary){
        this.tableActioned = tableActioned;
        this.itemActioned = itemActioned;
        this.actionTaken = actionTaken;
        this.itemKey = itemKey;
        this.clientIP = clientIP;
        this.adminUser = adminUser;
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
