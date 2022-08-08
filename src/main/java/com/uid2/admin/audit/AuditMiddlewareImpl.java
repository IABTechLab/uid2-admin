package com.uid2.admin.audit;

import com.uid2.shared.auth.IAuthorizable;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

public class AuditMiddlewareImpl implements AuditMiddleware{
    private final AuditWriter auditWriter;
    private final JsonObject config;

    protected AuditMiddlewareImpl(AuditWriter writer, JsonObject config){
        this.auditWriter = writer;
        this.config = config;
    }

    @Override
    public Handler<RoutingContext> handle(Function<RoutingContext, List<OperationModel>> handler) {
        InnerAuditHandler auditHandler = new InnerAuditHandler(handler, auditWriter, config.getBoolean("enable_admin_logging"));
        return auditHandler::writeLog;
    }

    private static class InnerAuditHandler{
        private final Function<RoutingContext, List<OperationModel>> innerHandler;
        private final AuditWriter auditWriter;
        private final boolean auditingEnabled;
        private InnerAuditHandler(Function<RoutingContext, List<OperationModel>> handler, AuditWriter auditWriter, boolean auditingEnabled) {
            this.innerHandler = handler;
            this.auditWriter = auditWriter;
            this.auditingEnabled = auditingEnabled;
        }

        public void writeLog(RoutingContext rc){
            List<OperationModel> modelList = innerHandler.apply(rc);
            if(auditingEnabled && modelList != null){
                String ipAddress = getIPAddress(rc);
                for(OperationModel model : modelList) {
                    AuditModel auditModel = new QLDBAuditModel(model.itemType, model.itemKey, model.actionTaken, ipAddress,
                            ((IAuthorizable) rc.data().get("api-client")).getContact(),
                            System.getenv("HOSTNAME"), Instant.now().getEpochSecond(), model.itemHash, model.summary);
                    auditWriter.writeLog(auditModel);
                }
            }
        }

        private static String getIPAddress(RoutingContext rc){
            List<String> listIP = rc.request().headers().getAll("X-Forwarded-For");
            if(listIP == null || listIP.isEmpty()){
                return rc.request().remoteAddress().toString();
            }
            else{
                return listIP.get(0); //arbitrary if multiple
            }
        }
    }
}