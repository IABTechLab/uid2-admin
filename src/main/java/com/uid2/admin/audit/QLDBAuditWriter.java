package com.uid2.admin.audit;

import com.amazon.ion.IonStruct;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonValue;
import com.amazon.ion.system.IonSystemBuilder;
import com.uid2.admin.Constants;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import software.amazon.awssdk.services.qldbsession.QldbSessionClient;
import software.amazon.qldb.QldbDriver;
import software.amazon.qldb.Result;
import software.amazon.qldb.RetryPolicy;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class QLDBAuditWriter implements AuditWriter{
    private static final IonSystem ionSys = IonSystemBuilder.standard().build();
    private static final QldbDriver qldbDriver = QldbDriver.builder()
            .ledger(Constants.QLDB_LEDGER_NAME)
            .transactionRetryPolicy(RetryPolicy.builder().maxRetries(3).build())
            .sessionClientBuilder(QldbSessionClient.builder())
            .build();
    private static final Logger logger = LoggerFactory.getLogger(QLDBAuditWriter.class);
    @Override
    public void writeLog(AuditModel model) {
        if(model == null){ //should never be true, but check exists in case
            return;
        }
        // write to QLDB; update this to UPDATE in one command instead of two
        qldbDriver.execute(txn -> {
            List<IonValue> sanitizedInputs = new ArrayList<>();
            StringBuilder query = new StringBuilder("UPDATE " + Constants.QLDB_TABLE_NAME + " AS t SET ");
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
            query.append(" WHERE t.itemType = ? AND t.itemKey = ?");
            sanitizedInputs.add(ionSys.newString(jsonObject.getString("itemType")));
            if(jsonObject.getValue("itemKey") == null){
                sanitizedInputs.add(ionSys.newNull());
            }
            else{
                sanitizedInputs.add(ionSys.newString(jsonObject.getString("itemKey")));
            }
            Result r = txn.execute(query.toString(), sanitizedInputs);
            if(!r.iterator().hasNext()){
                logger.warn("Malformed audit log input: no log written to QLDB");
            }
        });

        // write to Loki
        logger.info(model.writeToString());
    }

    public boolean isSetup(){
        try {
            final IonStruct[] count = new IonStruct[1];
            qldbDriver.execute(txn -> {
                Result result = txn.execute("SELECT COUNT(*) AS \"count\" FROM " + Constants.QLDB_TABLE_NAME);
                count[0] = (IonStruct) result.iterator().next();
            });
            return !count[0].get("count").equals(ionSys.newInt(0));
        }
        catch (Exception e){
            throw new RuntimeException("AWS configuration not set up");
        }
    }

    public void setup(OperationModel model){
        QLDBAuditModel auditModel = new QLDBAuditModel(model.itemType, model.itemKey, model.actionTaken, null,
                null, null, -1, model.itemHash, model.summary);
        qldbDriver.execute(txn -> {
            txn.execute("INSERT INTO " + Constants.QLDB_TABLE_NAME + " VALUE ?",
                    ionSys.newLoader().load(auditModel.writeToJson().toString()).get(0));
        });
    }
}
