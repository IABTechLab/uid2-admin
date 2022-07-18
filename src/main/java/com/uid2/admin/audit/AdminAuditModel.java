package com.uid2.admin.audit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.vertx.core.json.JsonObject;

public class AdminAuditModel implements AuditModel{

    public final Tables tableActioned;
    public final Actions actionTaken;
    public final String actionKey;
    public final String clientIP;
    public final String adminUser;
    public final String hostNode;
    public final long timeEpochSecond;
    public final SummaryModel additionalInfo;

    public AdminAuditModel(Tables tableActioned, Actions actionTaken, String actionKey, String clientIP, String adminUser,
                           String hostNode, long timeEpochSecond, SummaryModel additionalInfo){
        this.tableActioned = tableActioned;
        this.actionTaken = actionTaken;
        this.actionKey = actionKey;
        this.clientIP = clientIP;
        this.adminUser = adminUser;
        this.hostNode = hostNode;
        this.timeEpochSecond = timeEpochSecond;
        this.additionalInfo = additionalInfo;
    }

    @Override
    public JsonObject writeToJson() {
        Gson gson = new GsonBuilder().serializeNulls().create();
        return new JsonObject(gson.toJson(this));
    }

    @Override
    public String writeToString() {
        return "-audit " + adminUser + "/" + clientIP + " performed a(n) " + actionTaken.toString() + " action on " + tableActioned
                + " with action key " + this.actionKey + " at time " + timeEpochSecond + " handled by " + hostNode + "; additional info: "
                + this.additionalInfo.summary;
    }

    public static class SummaryModel {
        public final String summary;
        public final String entityName;
        public final String entityHash;

        public SummaryModel(String summary, String entityName, String entityHash){
            this.summary = summary;
            this.entityName = entityName;
            this.entityHash = entityHash;
        }
    }
}
