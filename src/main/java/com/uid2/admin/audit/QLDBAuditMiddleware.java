package com.uid2.admin.audit;

import com.amazon.ion.IonStruct;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonValue;
import com.amazon.ion.system.IonSystemBuilder;
import com.uid2.admin.auth.AdminUserProvider;
import com.uid2.shared.auth.IAuthorizable;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import software.amazon.awssdk.services.qldbsession.QldbSessionClient;
import software.amazon.qldb.QldbDriver;
import software.amazon.qldb.RetryPolicy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class QLDBAuditMiddleware implements AuditMiddleware{

    public static final String LEDGER_NAME = "audit-logs";
    public static final String TABLE_NAME = "logs";
    private static final IonSystem ionSys = IonSystemBuilder.standard().build();
    private static final QldbDriver qldbDriver = QldbDriver.builder()
            .ledger(LEDGER_NAME)
            .transactionRetryPolicy(RetryPolicy.builder().maxRetries(3).build())
            .sessionClientBuilder(QldbSessionClient.builder())
            .build();
    private static final Logger logger = LoggerFactory.getLogger(QLDBAuditMiddleware.class);

    protected QLDBAuditMiddleware(){

    }

    @Override
    public Handler<RoutingContext> handle(AuditHandler<RoutingContext> handler) {
        InnerAuditHandler auditHandler = new InnerAuditHandler(handler, this);
        return auditHandler::handle;
    }

    @Override
    public void writeLog(AuditModel model) {
        if(model == null){
            return;
        }
        // write to QLDB; update this to UPDATE in one command instead of two
        qldbDriver.execute(txn -> {
            List<IonValue> sanitizedInputs = new ArrayList<>();
            StringBuilder query = new StringBuilder("UPDATE " + TABLE_NAME + " AS t SET ");
            JsonObject jsonObject = model.writeToJson();
            Iterator<String> iterator = jsonObject.fieldNames().iterator();
            while(iterator.hasNext()){
                String key = iterator.next();
                query.append("t.").append(key).append(" = ?");
                if(jsonObject.getValue(key) == null){
                    sanitizedInputs.add(ionSys.newNull());
                }
                else {
                    try {
                        sanitizedInputs.add(ionSys.newInt(jsonObject.getInteger(key)));
                    } catch (ClassCastException e1) {
                        sanitizedInputs.add(ionSys.newString(jsonObject.getString(key)));
                    }
                }
                if(iterator.hasNext()){
                    query.append(", ");
                }
            }
            txn.execute(query.toString(), sanitizedInputs);
        });

        // old code; performs two queries which the QLDB treats as two history entries
//        qldbDriver.execute(txn -> {
//            txn.execute("DELETE FROM " + TABLE_NAME);
//            JsonObject jsonObject = model.writeToJson();
//            txn.execute("INSERT INTO " + TABLE_NAME + " VALUE ?",
//                    ionSys.newLoader().load(jsonObject.toString()).get(0));
//        });

        // write to Loki
        logger.info(model.writeToString());
    }

    private static class InnerAuditHandler{

        private final AuditHandler<RoutingContext> innerHandler;
        private final AuditMiddleware auditWriter;

        private InnerAuditHandler(AuditHandler<RoutingContext> handler, AuditMiddleware auditWriter){
            this.innerHandler = handler;
            this.auditWriter = auditWriter;
        }

        public void handle(RoutingContext rc){
            OperationModel model = innerHandler.handle(rc);
            if(rc.statusCode() < 300){
                AuditModel auditModel = new QLDBAuditModel(model.itemType, model.itemKey, model.actionTaken,
                        getIPAddress(rc),
                        ((IAuthorizable)rc.data().get("api-client")).getContact(),
                        System.getenv("HOSTNAME"), Instant.now().getEpochSecond(), model.itemHash, model.summary);
                auditWriter.writeLog(auditModel);
            }
        }

        private static String extractBearerToken(String headerValue) {
            if (headerValue == null) {
                return null;
            } else {
                String v = headerValue.trim();
                if (v.length() < "bearer ".length()) {
                    return null;
                } else {
                    String givenPrefix = v.substring(0, "bearer ".length());
                    return !"bearer ".equalsIgnoreCase(givenPrefix) ? null : v.substring("bearer ".length());
                }
            }
        }

        private static String getIPAddress(RoutingContext rc){
            List<String> listIP = rc.request().headers().getAll("X-Forwarded-For");
            System.out.println();
            if(listIP.isEmpty()){
                return rc.request().remoteAddress().toString();
            }
            else{
                return listIP.get(0); //arbitrary if multiple
            }
        }
    }
}
