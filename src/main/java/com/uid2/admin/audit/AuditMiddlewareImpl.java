package com.uid2.admin.audit;

import com.uid2.shared.auth.IAuthorizable;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import jdk.dynalink.Operation;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class AuditMiddlewareImpl implements AuditMiddleware{
    private final AuditWriter auditWriter;

    public AuditMiddlewareImpl(AuditWriter writer){
        this.auditWriter = writer;
    }

    @Override
    public Function<List<OperationModel>, Boolean> handle(RoutingContext rc) {
        InnerAuditHandler auditHandler = new InnerAuditHandler(rc, auditWriter);
        return auditHandler::writeLog;
    }

    private static class InnerAuditHandler{
        private final RoutingContext rc;
        private final AuditWriter auditWriter;
        private InnerAuditHandler(RoutingContext rc, AuditWriter auditWriter) {
            this.rc = rc;
            this.auditWriter = auditWriter;
        }

        public Boolean writeLog(List<OperationModel> modelList){
            String ipAddress = getIPAddress(rc);
            boolean logSuccessful = true;
            for(OperationModel model : modelList) {
                AuditModel auditModel = new QLDBAuditModel(model.itemType, model.itemKey, model.actionTaken, ipAddress,
                        ((IAuthorizable) rc.data().get("api-client")).getContact(),
                        System.getenv("HOSTNAME"), Instant.now().getEpochSecond(), model.itemHash, model.summary);
                logSuccessful = auditWriter.writeLog(auditModel);
                if(!logSuccessful){
                    break;
                }
            }
            return logSuccessful;
        }

        private static String getIPAddress(RoutingContext rc) {
            List<String> listIP = rc.request().headers().getAll("X-Forwarded-For");
            List<InetAddress> publicIPs = new ArrayList<>();
            for(String str : listIP){
                try {
                    InetAddress address = InetAddress.getByName(str);
                    if(!address.isSiteLocalAddress()){
                        publicIPs.add(address);
                    }
                }
                catch(UnknownHostException ignored){

                }

            }
            if(publicIPs.isEmpty()){
                return rc.request().remoteAddress().toString();
            }
            else{
                return publicIPs.get(0).getHostAddress(); //arbitrary if multiple
            }
        }
    }
}
