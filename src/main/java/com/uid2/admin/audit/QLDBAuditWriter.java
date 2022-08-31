package com.uid2.admin.audit;

import com.amazon.ion.IonSystem;
import com.amazon.ion.IonValue;
import com.amazon.ion.system.IonSystemBuilder;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import software.amazon.awssdk.services.qldbsession.QldbSessionClient;
import software.amazon.qldb.QldbDriver;
import software.amazon.qldb.Result;
import software.amazon.qldb.RetryPolicy;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class QLDBAuditWriter implements AuditWriter{
    private static final IonSystem ionSys = IonSystemBuilder.standard().build();
    private static final Logger logger = LoggerFactory.getLogger(QLDBAuditWriter.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("com.uid2.admin.audit");
    private QldbDriver qldbDriver;
    private final String logTable;
    private final boolean qldbLogging;
    public QLDBAuditWriter(JsonObject config){
        try {
            qldbDriver = QldbDriver.builder()
                    .ledger(config.getString("qldb_ledger_name"))
                    .transactionRetryPolicy(RetryPolicy.builder().maxRetries(3).build())
                    .sessionClientBuilder(QldbSessionClient.builder())
                    .build();
        }
        catch (Exception e){
            logger.error("cannot establish connection with QLDB");
        }
        logTable = config.getString("qldb_table_name");
        qldbLogging = config.getBoolean("enable_qldb_admin_logging");
    }
    @Override
    public boolean writeLogs(Collection<AuditModel> models) {
        AtomicBoolean successfulLog = new AtomicBoolean(true);
        try {
            if (qldbLogging) {
                qldbDriver.execute(txn -> {
                    for(AuditModel model : models){
                        if(!(model instanceof QLDBAuditModel)){ //should never be true, but check in case
                            successfulLog.set(false);
                            logger.error("Only QLDBAuditModel should be passed into QLDBAuditWriter");
                            txn.abort();
                            break;
                        }
                        QLDBAuditModel qldbModel = (QLDBAuditModel) model;
                        JsonObject jsonObject = qldbModel.writeToJson();
                        String query;
                        List<IonValue> sanitizedInputs = new ArrayList<>();
                        if(qldbModel.actionTaken == Actions.CREATE){
                            query = "INSERT INTO " + logTable + " VALUE ?";
                            JsonObject wrapped = new JsonObject().put("data", jsonObject);
                            sanitizedInputs.add(ionSys.newLoader().load(wrapped.toString()).get(0));
                        }
                        else{
                            query = "UPDATE " + logTable + " AS t SET data = ? WHERE t.data.itemType = ? AND t.data.itemKey = ?";
                            sanitizedInputs.add(ionSys.newLoader().load(jsonObject.toString()).get(0));
                            sanitizedInputs.add(ionSys.newString(qldbModel.itemType.toString()));
                            sanitizedInputs.add(ionSys.newString(qldbModel.itemKey));
                        }
                        Result r = txn.execute(query, sanitizedInputs);
                        if (!r.iterator().hasNext()) {
                            logger.error("Malformed audit log input: no log written to QLDB");
                            successfulLog.set(false);
                            txn.abort();
                            break;
                        }
                    }
                });
            }
            if(successfulLog.get()) {
                for (AuditModel model : models) {
                    auditLogger.info(model.writeToString());
                }
            }
            return successfulLog.get();
        }
        catch(Exception e){
            logger.error("QLDB log failed: " + e.getClass().getSimpleName());
            auditLogger.error("QLDB log failed" + e.getClass().getSimpleName());
            return false;
        }
    }
}
