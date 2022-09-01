package com.uid2.admin.audit;

import com.amazon.ion.IonList;
import com.amazon.ion.IonStruct;
import com.amazon.ion.IonSystem;
import com.amazon.ion.system.IonSystemBuilder;
import com.uid2.admin.Constants;
import com.uid2.admin.vertx.service.IService;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import software.amazon.qldb.QldbDriver;
import software.amazon.qldb.Result;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles creating a table, indices, and inserts documents passed to it. Note that all classes that extend
 * QLDBInit should initialize qldbDriver and qldbTableName in their constructor.
 */
public abstract class QLDBInit implements IAuditInit{

    protected final IonSystem ionSys = IonSystemBuilder.standard().build();
    protected QldbDriver qldbDriver;
    protected String qldbTableName;
    private final Logger LOGGER = LoggerFactory.getLogger(QLDBInit.class);

    @Override
    public void init(Collection<OperationModel> modelList){
        try {
            if (!hasTableBeenCreated()) {
                createTable();
            }
            if(!haveIndicesBeenCreated()){
                createIndices();
            }
            for(OperationModel model : modelList){
                insertIntoQLDB(model);
            }
            LOGGER.info("initialized qldb");
        }
        catch(Exception e){
            LOGGER.warn("qldb not initialized");
        }
    }

    protected boolean hasTableBeenCreated() {
        try {
            AtomicBoolean hasTableBeenCreated = new AtomicBoolean(false);
            qldbDriver.execute(txn -> {
                Result result = txn.execute("SELECT * FROM information_schema.user_tables WHERE name = ?",
                        ionSys.newString(qldbTableName));
                hasTableBeenCreated.set(!result.isEmpty());
            });
            return hasTableBeenCreated.get();
        } catch (Exception e) {
            throw new RuntimeException("AWS configuration not set up");
        }
    }

    protected boolean haveIndicesBeenCreated(){ //Assumes table exists
        try {
            AtomicBoolean hasTableBeenCreated = new AtomicBoolean(false);
            qldbDriver.execute(txn -> {
                Result result = txn.execute("SELECT indexes FROM information_schema.user_tables WHERE name = ?",
                        ionSys.newString(qldbTableName));
                hasTableBeenCreated.set(((IonList)(((IonStruct)(result.iterator().next())).iterator().next())).size() != 0);
            });
            return hasTableBeenCreated.get();
        }
        catch (Exception e) {
            throw new RuntimeException("AWS configuration not set up");
        }
    }

    private void createTable() { //creates the logs table. Assumes it doesn't already exist
        try {
            qldbDriver.execute(txn -> {
                txn.execute("CREATE TABLE " + qldbTableName);
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createIndices() { //creates indices on itemType and itemKey. Assumes they don't already exist.
        try {
            qldbDriver.execute(txn -> {
                txn.execute("CREATE INDEX ON " + qldbTableName + "(itemType)");
                txn.execute("CREATE INDEX ON " + qldbTableName + "(itemKey)");
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void insertIntoQLDB(OperationModel model) { //populates qldb
        QLDBAuditModel auditModel = new QLDBAuditModel(model.itemType, model.itemKey, model.actionTaken, null,
                null, null, -1, model.itemHash, model.summary);
        qldbDriver.execute(txn -> {
            txn.execute("INSERT INTO " + qldbTableName + " VALUE ?",
                    ionSys.newLoader().load(auditModel.writeToJson().toString()).get(0));
        });
    }
}
