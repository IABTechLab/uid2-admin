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
    public QLDBAuditWriter(){

    }
    @Override
    public void writeLog(AuditModel model) {
        if(model == null){ //should never be true, but check exists in case
            return;
        }
        // write to QLDB; update this to UPDATE in one command instead of two
        qldbDriver.execute(txn -> {
            List<IonValue> sanitizedInputs = new ArrayList<>();
            JsonObject jsonObject = model.writeToJson();
            StringBuilder query = new StringBuilder("UPDATE " + Constants.QLDB_TABLE_NAME + " AS t SET data = ?");
            sanitizedInputs.add(ionSys.newLoader().load(jsonObject.toString()).get(0));
            query.append(" WHERE t.data.itemType = ? AND t.data.itemKey = ?");
            sanitizedInputs.add(ionSys.newString(jsonObject.getString("itemType")));
            sanitizedInputs.add(ionSys.newString(jsonObject.getString("itemKey")));

            Result r = txn.execute(query.toString(), sanitizedInputs);
            if(!r.iterator().hasNext()){
                logger.warn("Malformed audit log input: no log written to QLDB");
            }
        });

        // write to Loki
        logger.info(model.writeToString());
    }
}
