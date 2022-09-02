package com.uid2.admin.audit;

import com.amazon.ion.IonStruct;
import com.uid2.admin.Constants;
import com.uid2.admin.vertx.service.IService;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import software.amazon.awssdk.services.qldbsession.QldbSessionClient;
import software.amazon.qldb.QldbDriver;
import software.amazon.qldb.Result;
import software.amazon.qldb.RetryPolicy;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class AdminQLDBInit extends QLDBInit{
    private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    public AdminQLDBInit(JsonObject config){
        qldbDriver = QldbDriver.builder()
                .ledger(config.getString("qldb_ledger_name"))
                .transactionRetryPolicy(RetryPolicy.builder().maxRetries(3).build())
                .sessionClientBuilder(QldbSessionClient.builder())
                .build();
        qldbTableName = config.getString("qldb_table_name");
    }

    public void init(List<IService> services) {
        Collection<OperationModel> totalModelList = new HashSet<>();
        for (IService service : services) {
            if (!hasTableBeenCreated() || !isServiceSetup(service)) {
                totalModelList.addAll(service.qldbSetup());
                totalModelList.add(new OperationModel(service.tableType(), Constants.DEFAULT_ITEM_KEY,
                        null, null, null));
                totalModelList.add(new OperationModel(service.tableType(), Constants.NULL_ITEM_KEY,
                        null, null, null));
            }
        }
        init(totalModelList);
    }

    private boolean isServiceSetup(IService service) {
        try {
            final IonStruct[] count = new IonStruct[1];
            qldbDriver.execute(txn -> {
                Result result = txn.execute("SELECT COUNT(*) AS \"count\" FROM " + qldbTableName
                        + " AS t WHERE t.itemType = ?", ionSys.newString(service.tableType().toString()));
                count[0] = (IonStruct) result.iterator().next();
            });
            return !count[0].get("count").equals(ionSys.newInt(0));
        } catch (Exception e) {
            throw new RuntimeException("AWS configuration not set up");
        }
    }
}
