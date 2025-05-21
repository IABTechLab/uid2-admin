package com.uid2.admin.model;

import io.vertx.core.json.JsonObject;
import java.time.Instant;

public class AuditLog {
    private final Instant timestamp;
    private final String logType;
    private final String source;
    private final int status;
    private final String method;
    private final String endpoint;
    private final String requestId;
    private final JsonObject actor;
    private final String forwardedRequestId;
    private final JsonObject queryParams;
    private final JsonObject requestBody;

    // Constructor for mandatory fields
    private AuditLog(Builder builder) {
        this.timestamp = Instant.now();
        this.logType = "audit";
        this.source = AuditLog.class.getPackage().getName();
        this.status = builder.status;
        this.method = builder.method;
        this.endpoint = builder.endpoint;
        this.requestId = builder.requestId;
        this.actor = builder.actor;
        this.forwardedRequestId = builder.forwardedRequestId;
        this.queryParams = builder.queryParams;
        this.requestBody = builder.requestBody;
    }

    // Optional: JSON serialization for structured logging
    public JsonObject toJson() {
        JsonObject json = new JsonObject()
                .put("timestamp", timestamp.toString())
                .put("log_type", logType)
                .put("source", source)
                .put("status", status)
                .put("method", method)
                .put("endpoint", endpoint)
                .put("request_id", requestId)
                .put("actor", actor);
        if (forwardedRequestId != null) json.put("forwarded_request_id", forwardedRequestId);
        if (queryParams != null) json.put("query_params", queryParams);
        if (requestBody != null) json.put("request_body", requestBody);
        return json;
    }

    @Override
    public String toString() {
        return toJson().encode();
    }

    // Builder
    public static class Builder {
        private String source;
        private final int status;
        private final String method;
        private final String endpoint;
        private final String requestId;
        private final JsonObject actor;

        private String forwardedRequestId;
        private JsonObject queryParams;
        private JsonObject requestBody;

        public Builder(int status, String method, String endpoint, String requestId, JsonObject actor) {
            this.status = status;
            this.method = method;
            this.endpoint = endpoint;
            this.requestId = requestId;
            this.actor = actor;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder forwardedRequestId(String forwardedRequestId) {
            this.forwardedRequestId = forwardedRequestId;
            return this;
        }

        public Builder queryParams(JsonObject queryParams) {
            this.queryParams = queryParams;
            return this;
        }

        public Builder requestBody(JsonObject requestBody) {
            this.requestBody = requestBody;
            return this;
        }

        public AuditLog build() {
            return new AuditLog(this);
        }
    }
}
