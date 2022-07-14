package com.uid2.admin.audit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.vertx.core.json.JsonObject;

public class AdminAuditModel implements AuditModel{

    public final String tableActioned;
    public final Actions action;
    public final String key;
    public final String clientIP;
    public final String user;
    public final String hostNode;
    public final long timeEpochSecond;
    public final AdditionalInfoModel additionalInfo;

    public AdminAuditModel(String tableActioned, Actions action, String key, String clientIP, String user,
                           String hostNode, long timeEpochSecond, AdditionalInfoModel additionalInfo){
        this.tableActioned = tableActioned;
        this.action = action;
        this.key = key;
        this.clientIP = clientIP;
        this.user = user;
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
        return "-audit " + user + "/" + clientIP + " performed a(n) " + action.toString() + " action on " + tableActioned
                + " with key " + this.key + " at time " + timeEpochSecond + " handled by " + hostNode + "; additional info: "
                + this.additionalInfo;
    }

    public static class AdditionalInfoModel{
        public final String summary;
        public final String entityName;
        public final String entityHash;

        public AdditionalInfoModel(String summary, String entityName, String entityHash){
            this.summary = summary;
            this.entityName = entityName;
            this.entityHash = entityHash;
        }

    }
}
