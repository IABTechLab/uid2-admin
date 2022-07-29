package com.uid2.admin.audit;

import com.amazon.ion.IonStruct;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonValue;
import com.amazon.ion.system.IonSystemBuilder;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.Constants;
import com.uid2.admin.auth.AdminUser;
import com.uid2.admin.auth.AdminUserProvider;
import com.uid2.admin.model.Site;
import com.uid2.admin.store.RotatingPartnerStore;
import com.uid2.admin.store.RotatingSiteStore;
import com.uid2.admin.vertx.JsonUtil;
import com.uid2.admin.vertx.service.*;
import com.uid2.shared.Const;
import com.uid2.shared.Utils;
import com.uid2.shared.auth.*;
import com.uid2.shared.cloud.CloudUtils;
import com.uid2.shared.cloud.ICloudStorage;
import com.uid2.shared.model.EnclaveIdentifier;
import com.uid2.shared.model.EncryptionKey;
import com.uid2.shared.model.SaltEntry;
import com.uid2.shared.store.RotatingKeyStore;
import com.uid2.shared.store.RotatingSaltProvider;
import com.uid2.shared.vertx.VertxUtils;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.codec.digest.DigestUtils;
import software.amazon.awssdk.services.qldbsession.QldbSessionClient;
import software.amazon.qldb.QldbDriver;
import software.amazon.qldb.Result;
import software.amazon.qldb.RetryPolicy;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class QLDBInit {

    private static final IonSystem ionSys = IonSystemBuilder.standard().build();
    private static QldbDriver qldbDriver;
    private static JsonObject config;
    private static String qldbLedgerName;
    private static String qldbTableName;

    private static final Logger LOGGER = LoggerFactory.getLogger(QLDBInit.class);
    private static final ObjectWriter jsonWriter = JsonUtil.createJsonWriter();

    public static void main(String[] args) {
        System.out.println("STARTING\n\n\n");
        final String vertxConfigPath = System.getProperty(Const.Config.VERTX_CONFIG_PATH_PROP);
        if (vertxConfigPath != null) {
            System.out.format("Running CUSTOM CONFIG mode, config: %s\n", vertxConfigPath);
        } else if (!Utils.isProductionEnvironment()) {
            System.out.format("Running LOCAL DEBUG mode, config: %s\n", Const.Config.LOCAL_CONFIG_PATH);
            System.setProperty(Const.Config.VERTX_CONFIG_PATH_PROP, Const.Config.LOCAL_CONFIG_PATH);
        } else {
            System.out.format("Running PRODUCTION mode, config: %s\n", Const.Config.OVERRIDE_CONFIG_PATH);
        }
        AtomicReference<JsonObject> atomicConfig = new AtomicReference<>();
        Vertx vertx = Vertx.vertx();
        VertxUtils.createConfigRetriever(vertx).getConfig(ar -> {
            if (ar.failed()) {
                LOGGER.fatal("Unable to read config: " + ar.cause().getMessage(), ar.cause());
            }
            atomicConfig.set(ar.result());

            config = atomicConfig.get();
            qldbLedgerName = config.getString("qldb_ledger_name");
            qldbTableName = config.getString("qldb_table_name");
            System.out.println("Ledger: " + qldbLedgerName + ", Table: " + qldbTableName);
            qldbDriver = QldbDriver.builder()
                    .ledger(qldbLedgerName)
                    .transactionRetryPolicy(RetryPolicy.builder().maxRetries(3).build())
                    .sessionClientBuilder(QldbSessionClient.builder())
                    .build();
            try {
                ICloudStorage cloudStorage = CloudUtils.createStorage(config.getString(Const.Config.CoreS3BucketProp), config);

                Collection<Collection<OperationModel>> initModelLists = new HashSet<>();

                String adminsMetadataPath = config.getString(AdminUserProvider.ADMINS_METADATA_PATH);
                AdminUserProvider adminUserProvider = new AdminUserProvider(cloudStorage, adminsMetadataPath);
                adminUserProvider.loadContent(adminUserProvider.getMetadata());
                addAdminModels(initModelLists, adminUserProvider);

                String sitesMetadataPath = config.getString(RotatingSiteStore.SITES_METADATA_PATH);
                RotatingSiteStore siteProvider = new RotatingSiteStore(cloudStorage, sitesMetadataPath);
                siteProvider.loadContent(siteProvider.getMetadata());
                addSiteModels(initModelLists, siteProvider);

                String clientMetadataPath = config.getString(Const.Config.ClientsMetadataPathProp);
                RotatingClientKeyProvider clientKeyProvider = new RotatingClientKeyProvider(cloudStorage, clientMetadataPath);
                clientKeyProvider.loadContent();
                addClientModels(initModelLists, clientKeyProvider);

                String keyMetadataPath = config.getString(Const.Config.KeysMetadataPathProp);
                RotatingKeyStore keyProvider = new RotatingKeyStore(cloudStorage, keyMetadataPath);
                keyProvider.loadContent();
                addEncryptionKeyModels(initModelLists, keyProvider);

                String keyAclMetadataPath = config.getString(Const.Config.KeysAclMetadataPathProp);
                RotatingKeyAclProvider keyAclProvider = new RotatingKeyAclProvider(cloudStorage, keyAclMetadataPath);
                keyAclProvider.loadContent();
                addKeyAclModels(initModelLists, keyAclProvider);

                String operatorMetadataPath = config.getString(Const.Config.OperatorsMetadataPathProp);
                RotatingOperatorKeyProvider operatorKeyProvider = new RotatingOperatorKeyProvider(cloudStorage, cloudStorage, operatorMetadataPath);
                operatorKeyProvider.loadContent(operatorKeyProvider.getMetadata());
                addOperatorModels(initModelLists, operatorKeyProvider);

                String enclaveMetadataPath = config.getString(EnclaveIdentifierProvider.ENCLAVES_METADATA_PATH);
                EnclaveIdentifierProvider enclaveIdProvider = new EnclaveIdentifierProvider(cloudStorage, enclaveMetadataPath);
                enclaveIdProvider.loadContent(enclaveIdProvider.getMetadata());
                addEnclaveModels(initModelLists, enclaveIdProvider);

                String saltMetadataPath = config.getString(Const.Config.SaltsMetadataPathProp);
                RotatingSaltProvider saltProvider = new RotatingSaltProvider(cloudStorage, saltMetadataPath);
                saltProvider.loadContent();
                addSaltModels(initModelLists, saltProvider);

                String partnerMetadataPath = config.getString(RotatingPartnerStore.PARTNERS_METADATA_PATH);
                RotatingPartnerStore partnerConfigProvider = new RotatingPartnerStore(cloudStorage, partnerMetadataPath);
                partnerConfigProvider.loadContent();
                addPartnerModels(initModelLists, partnerConfigProvider);

                if(!hasTableBeenCreated()){
                    createTable();
                }
                if (!isTableSetup()) {
                    for (Collection<OperationModel> modelList : initModelLists) {
                        boolean createdAllItems = false;
                        for(OperationModel model : modelList) {
                            if (!createdAllItems) {
                                createdAllItems = true;
                                insertIntoQLDB(new OperationModel(model.itemType, Constants.DEFAULT_ITEM_KEY,
                                        null, null, null));
                            }
                            insertIntoQLDB(model);
                        }
                    }
                }
                vertx.close();
                System.exit(0);
            } catch (Exception e) {
                System.out.println("Failed to initialize QLDB");
                vertx.close();
                System.exit(-1);
            }
        });
    }

    private static boolean hasTableBeenCreated(){
        try{
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
        }
        catch(Exception e){
            throw new RuntimeException("AWS configuration not set up");
        }
    }

    private static boolean isTableSetup() {
        try {
            final IonStruct[] count = new IonStruct[1];
            qldbDriver.execute(txn -> {
                Result result = txn.execute("SELECT COUNT(*) AS \"count\" FROM " + qldbTableName);
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

    private static void addAdminModels(Collection<Collection<OperationModel>> models, AdminUserProvider adminUserProvider) {
        try {
            Collection<AdminUser> adminUsers = adminUserProvider.getAll();
            Collection<OperationModel> newModels = new HashSet<>();
            for (AdminUser a : adminUsers) {
                newModels.add(new OperationModel(Type.ADMIN, a.getName(), null,
                        DigestUtils.sha256Hex(jsonWriter.writeValueAsString(a)), null));
            }
            models.add(newModels);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void addSiteModels(Collection<Collection<OperationModel>> models, RotatingSiteStore siteProvider) {
        try {
            Collection<Site> sites = siteProvider.getAllSites();
            Collection<OperationModel> newModels = new HashSet<>();
            for (Site s : sites) {
                newModels.add(new OperationModel(Type.SITE, s.getName(), null,
                        DigestUtils.sha256Hex(jsonWriter.writeValueAsString(s)), null));
            }
            models.add(newModels);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void addClientModels(Collection<Collection<OperationModel>> models, RotatingClientKeyProvider clientKeyProvider) {
        try {
            Collection<ClientKey> clients = clientKeyProvider.getAll();
            Collection<OperationModel> newModels = new HashSet<>();
            for (ClientKey c : clients) {
                newModels.add(new OperationModel(Type.CLIENT, c.getName(), null,
                        DigestUtils.sha256Hex(jsonWriter.writeValueAsString(c)), null));
            }
            models.add(newModels);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void addEncryptionKeyModels(Collection<Collection<OperationModel>> models, RotatingKeyStore keyProvider) {
        try {
            Collection<EncryptionKey> keys = keyProvider.getSnapshot().getActiveKeySet();
            Collection<OperationModel> newModels = new HashSet<>();
            for (EncryptionKey k : keys) {
                newModels.add(new OperationModel(Type.KEY, String.valueOf(k.getId()), null,
                        EncryptionKeyService.hashedToJsonWithKey(k), null));
            }
            models.add(newModels);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void addKeyAclModels(Collection<Collection<OperationModel>> models, RotatingKeyAclProvider keyAclProvider) {
        try {
            Map<Integer, EncryptionKeyAcl> mapAcl = keyAclProvider.getSnapshot().getAllAcls();
            Collection<OperationModel> newModels = new HashSet<>();
            for (int i : mapAcl.keySet()) {
                JsonArray listedSites = new JsonArray();
                EncryptionKeyAcl acl = mapAcl.get(i);
                acl.getAccessList().stream().sorted().forEach(listedSites::add);
                JsonObject jo = new JsonObject();
                jo.put("site_id", i);
                jo.put(acl.getIsWhitelist() ? "whitelist" : "blacklist", listedSites);

                newModels.add(new OperationModel(Type.KEYACL, String.valueOf(i), null,
                        DigestUtils.sha256Hex(jo.toString()), null));
            }
            models.add(newModels);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void addOperatorModels(Collection<Collection<OperationModel>> models, RotatingOperatorKeyProvider operatorKeyProvider) {
        try {
            Collection<OperatorKey> operatorCollection = operatorKeyProvider.getAll();
            Collection<OperationModel> newModels = new HashSet<>();
            for (OperatorKey o : operatorCollection) {
                newModels.add(new OperationModel(Type.OPERATOR, o.getName(), null,
                        DigestUtils.sha256Hex(jsonWriter.writeValueAsString(o)), null));
            }
            models.add(newModels);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void addEnclaveModels(Collection<Collection<OperationModel>> models, EnclaveIdentifierProvider enclaveIdProvider) {
        try {
            Collection<EnclaveIdentifier> enclaves = enclaveIdProvider.getAll();
            Collection<OperationModel> newModels = new HashSet<>();
            for (EnclaveIdentifier e : enclaves) {
                newModels.add(new OperationModel(Type.ENCLAVE, e.getName(), null,
                        DigestUtils.sha256Hex(jsonWriter.writeValueAsString(e)), null));
            }
            models.add(newModels);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void addSaltModels(Collection<Collection<OperationModel>> models, RotatingSaltProvider saltProvider) {
        try {
            List<RotatingSaltProvider.SaltSnapshot> snapshots = saltProvider.getSnapshots();
            RotatingSaltProvider.SaltSnapshot lastSnapshot = snapshots.get(snapshots.size() - 1);

            JsonObject jo = new JsonObject();
            jo.put("effective", lastSnapshot.getEffective().toEpochMilli());
            jo.put("expires", lastSnapshot.getExpires().toEpochMilli());
            jo.put("salts_count", lastSnapshot.getAllRotatingSalts().length);
            jo.put("min_last_updated", Arrays.stream(lastSnapshot.getAllRotatingSalts())
                    .map(SaltEntry::getLastUpdated)
                    .min(Long::compare).orElse(null));
            jo.put("max_last_updated", Arrays.stream(lastSnapshot.getAllRotatingSalts())
                    .map(SaltEntry::getLastUpdated)
                    .max(Long::compare).orElse(null));

            Collection<OperationModel> newModels = new HashSet<>();
            newModels.add(new OperationModel(Type.SALT, "singleton", null,
                    DigestUtils.sha256Hex(jo.toString()), null));
            models.add(newModels);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void addPartnerModels(Collection<Collection<OperationModel>> models, RotatingPartnerStore partnerConfigProvider) {
        try {
            String config = partnerConfigProvider.getConfig();
            Collection<OperationModel> newModels = new HashSet<>();
            newModels.add(new OperationModel(Type.PARTNER, "singleton", null,
                    DigestUtils.sha256Hex(config), null));
            models.add(newModels);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
