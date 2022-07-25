package com.uid2.admin.audit;

import com.uid2.admin.Constants;
import com.uid2.admin.vertx.service.IService;
import com.uid2.shared.auth.IAuthorizable;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public class QLDBAuditMiddleware implements AuditMiddleware{
    private final AuditWriter auditWriter;

    protected QLDBAuditMiddleware(AuditWriter writer){
        this.auditWriter = writer;
    }

    @Override
    public Handler<RoutingContext> handle(AuditHandler<RoutingContext> handler) {
        InnerAuditHandler auditHandler = new InnerAuditHandler(handler, auditWriter);
        return auditHandler::writeLog;
    }

    @Override
    public void startup(List<IService> services){
        if(!((QLDBAuditWriter)auditWriter).isSetup()){
            for(IService service : services){
                Collection<OperationModel> models = service.backfill();
                boolean createdAllItems = false;
                for(OperationModel model : models){
                    if(!createdAllItems){
                        createdAllItems = true;
                        ((QLDBAuditWriter) auditWriter).setup(new OperationModel(model.itemType, Constants.DEFAULT_ITEM_KEY,
                                null, null, null));
                    }
                    ((QLDBAuditWriter) auditWriter).setup(model);
                }
            }
        }
    }

    private static class InnerAuditHandler{
        private final AuditHandler<RoutingContext> innerHandler;
        private final AuditWriter auditWriter;
        private InnerAuditHandler(AuditHandler<RoutingContext> handler, AuditWriter auditWriter) {
            this.innerHandler = handler;
            this.auditWriter = auditWriter;
        }

        public void writeLog(RoutingContext rc){
            List<OperationModel> modelList = innerHandler.handle(rc);
            if(modelList != null && rc.statusCode() < 300){
                for(OperationModel model : modelList) {
                    AuditModel auditModel = new QLDBAuditModel(model.itemType, model.itemKey, model.actionTaken,
                            getIPAddress(rc),
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
