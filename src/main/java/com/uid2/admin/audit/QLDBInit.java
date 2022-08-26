package com.uid2.admin.audit;

import com.amazon.ion.IonStruct;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonValue;
import com.amazon.ion.system.IonSystemBuilder;
import com.uid2.admin.Constants;
import com.uid2.admin.vertx.service.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import software.amazon.awssdk.services.qldbsession.QldbSessionClient;
import software.amazon.qldb.QldbDriver;
import software.amazon.qldb.Result;
import software.amazon.qldb.RetryPolicy;

import java.util.*;

public class QLDBInit {

    private static final IonSystem ionSys = IonSystemBuilder.standard().build();
    private static QldbDriver qldbDriver;
    private static String qldbTableName;
    private static final Logger LOGGER = LoggerFactory.getLogger(QLDBInit.class);

    public static void init(List<IService> services, JsonObject config) {
        try {
            qldbDriver = QldbDriver.builder()
                    .ledger(config.getString("qldb_ledger_name"))
                    .transactionRetryPolicy(RetryPolicy.builder().maxRetries(3).build())
                    .sessionClientBuilder(QldbSessionClient.builder())
                    .build();
            qldbTableName = config.getString("qldb_table_name");
            if (!hasTableBeenCreated()) {
                createTable();
            }
            for (IService service : services) {
                if (!isServiceSetup(service)) {
                    Collection<OperationModel> modelList = service.qldbSetup();
                    insertIntoQLDB(new OperationModel(service.tableType(), Constants.DEFAULT_ITEM_KEY,
                            null, null, null));
                    for (OperationModel model : modelList) {
                        insertIntoQLDB(model);
                    }
                }
            }
            LOGGER.info("initialized qldb");
        }
        catch(Exception e){
            LOGGER.warn("qldb not initialized");
        }
    }

    private static boolean hasTableBeenCreated() {
        try {
            final IonStruct[] table = new IonStruct[1];
            qldbDriver.execute(txn -> {
                Result result = txn.execute("SELECT * FROM information_schema.user_tables");
                for (Iterator<IonValue> it = result.iterator(); it.hasNext(); ) {
                    IonStruct struct = (IonStruct) it.next();
                    if (struct.get("name").toString().equals(qldbTableName)) {
                        table[0] = (IonStruct) result.iterator().next();
                    }
                }
            });
            return table[0] != null;
        } catch (Exception e) {
            throw new RuntimeException("AWS configuration not set up");
        }
    }

    private static boolean isServiceSetup(IService service) {
        try {
            final IonStruct[] count = new IonStruct[1];
            qldbDriver.execute(txn -> {
                Result result = txn.execute("SELECT COUNT(*) AS \"count\" FROM " + qldbTableName
                        + " AS t WHERE t.data.itemType = ?", ionSys.newString(service.tableType().toString()));
                count[0] = (IonStruct) result.iterator().next();
            });
            return !count[0].get("count").equals(ionSys.newInt(0));
        } catch (Exception e) {
            throw new RuntimeException("AWS configuration not set up");
        }
    }

    private static void createTable() { //creates the logs table, assuming it doesn't already exist
        try {
            qldbDriver.execute(txn -> {
                txn.execute("CREATE TABLE " + qldbTableName);
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void insertIntoQLDB(OperationModel model) { //populates qldb
        QLDBAuditModel auditModel = new QLDBAuditModel(model.itemType, model.itemKey, model.actionTaken, null,
                null, null, -1, model.itemHash, model.summary);
        qldbDriver.execute(txn -> {
            JsonObject jsonObject = new JsonObject();
            jsonObject.put("data", auditModel.writeToJson());
            txn.execute("INSERT INTO " + qldbTableName + " VALUE ?",
                    ionSys.newLoader().load(jsonObject.toString()).get(0));
        });
    }
}
