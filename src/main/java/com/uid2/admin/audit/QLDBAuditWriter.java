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

public class QLDBAuditWriter implements AuditWriter{
    private static final IonSystem ionSys = IonSystemBuilder.standard().build();
    private static final Logger logger = LoggerFactory.getLogger(QLDBAuditWriter.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("com.uid2.admin.audit");
    private final QldbDriver qldbDriver;
    private final String logTable;
    private final boolean qldbLogging;
    public QLDBAuditWriter(JsonObject config){
        qldbDriver = QldbDriver.builder()
                .ledger(config.getString("qldb_ledger_name"))
                .transactionRetryPolicy(RetryPolicy.builder().maxRetries(3).build())
                .sessionClientBuilder(QldbSessionClient.builder())
                .build();
        logTable = config.getString("qldb_table_name");
        qldbLogging = config.getBoolean("enable_qldb_admin_logging");
    }
    @Override
    public void writeLog(AuditModel model) {
        try {
            if(!(model instanceof QLDBAuditModel)){ //should never be true, but check in case
                throw new IllegalArgumentException("Only QLDBAuditModel should be passed into QLDBAuditWriter");
            }
            QLDBAuditModel qldbModel = (QLDBAuditModel) model;
            if (qldbLogging) {
                JsonObject jsonObject = qldbModel.writeToJson();
                if(qldbModel.actionTaken == Actions.CREATE){
                    qldbDriver.execute(txn -> {
                        List<IonValue> sanitizedInputs = new ArrayList<>();
                        String query = "INSERT INTO " + logTable + " VALUE ?";
                        JsonObject wrapped = new JsonObject().put("data", jsonObject);
                        sanitizedInputs.add(ionSys.newLoader().load(wrapped.toString()).get(0));

                        Result r = txn.execute(query, sanitizedInputs);
                        if (!r.iterator().hasNext()) {
                            logger.warn("Malformed audit log input: no log written to QLDB");
                        }
                    });
                }
                else {
                    qldbDriver.execute(txn -> {
                        List<IonValue> sanitizedInputs = new ArrayList<>();
                        StringBuilder query = new StringBuilder("UPDATE " + logTable + " AS t SET data = ?");
                        sanitizedInputs.add(ionSys.newLoader().load(jsonObject.toString()).get(0));
                        query.append(" WHERE t.data.itemType = ? AND t.data.itemKey = ?");
                        sanitizedInputs.add(ionSys.newString(qldbModel.itemType.toString()));
                        sanitizedInputs.add(ionSys.newString(qldbModel.itemKey));

                        Result r = txn.execute(query.toString(), sanitizedInputs);
                        if (!r.iterator().hasNext()) {
                            logger.warn("Malformed audit log input: no log written to QLDB");
                        }
                    });
                }
            }
        }
        catch(Exception e){
            logger.warn("QLDB log failed: " + e.getClass().getSimpleName());
            auditLogger.warn("QLDB log failed" + e.getClass().getSimpleName());
        }
        auditLogger.info(model.writeToString());
    }
}
