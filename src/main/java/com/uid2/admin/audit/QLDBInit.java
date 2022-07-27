package com.uid2.admin.audit;

import com.amazon.ion.IonStruct;
import com.amazon.ion.IonSystem;
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
    private static final QldbDriver qldbDriver = QldbDriver.builder()
            .ledger(Constants.QLDB_LEDGER_NAME)
            .transactionRetryPolicy(RetryPolicy.builder().maxRetries(3).build())
            .sessionClientBuilder(QldbSessionClient.builder())
            .build();

    private static final Logger LOGGER = LoggerFactory.getLogger(QLDBInit.class);
    private static final ObjectWriter jsonWriter = JsonUtil.createJsonWriter();
    
    public static void main(String[] args){
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
        });
        JsonObject config = atomicConfig.get();
        try {
            ICloudStorage cloudStorage = CloudUtils.createStorage(config.getString(Const.Config.CoreS3BucketProp), config);

            Collection<OperationModel> initModelList = new HashSet<>();

            String adminsMetadataPath = config.getString(AdminUserProvider.ADMINS_METADATA_PATH);
            AdminUserProvider adminUserProvider = new AdminUserProvider(cloudStorage, adminsMetadataPath);
            adminUserProvider.loadContent(adminUserProvider.getMetadata());
            addAdminModels(initModelList, adminUserProvider);

            String sitesMetadataPath = config.getString(RotatingSiteStore.SITES_METADATA_PATH);
            RotatingSiteStore siteProvider = new RotatingSiteStore(cloudStorage, sitesMetadataPath);
            siteProvider.loadContent(siteProvider.getMetadata());
            addSiteModels(initModelList, siteProvider);

            String clientMetadataPath = config.getString(Const.Config.ClientsMetadataPathProp);
            RotatingClientKeyProvider clientKeyProvider = new RotatingClientKeyProvider(cloudStorage, clientMetadataPath);
            clientKeyProvider.loadContent();
            addClientModels(initModelList, clientKeyProvider);

            String keyMetadataPath = config.getString(Const.Config.KeysMetadataPathProp);
            RotatingKeyStore keyProvider = new RotatingKeyStore(cloudStorage, keyMetadataPath);
            keyProvider.loadContent();
            addEncryptionKeyModels(initModelList, keyProvider);

            String keyAclMetadataPath = config.getString(Const.Config.KeysAclMetadataPathProp);
            RotatingKeyAclProvider keyAclProvider = new RotatingKeyAclProvider(cloudStorage, keyAclMetadataPath);
            keyAclProvider.loadContent();
            addKeyAclModels(initModelList, keyAclProvider);

            String operatorMetadataPath = config.getString(Const.Config.OperatorsMetadataPathProp);
            RotatingOperatorKeyProvider operatorKeyProvider = new RotatingOperatorKeyProvider(cloudStorage, cloudStorage, operatorMetadataPath);
            operatorKeyProvider.loadContent(operatorKeyProvider.getMetadata());
            addOperatorModels(initModelList, operatorKeyProvider);

            String enclaveMetadataPath = config.getString(EnclaveIdentifierProvider.ENCLAVES_METADATA_PATH);
            EnclaveIdentifierProvider enclaveIdProvider = new EnclaveIdentifierProvider(cloudStorage, enclaveMetadataPath);
            enclaveIdProvider.loadContent(enclaveIdProvider.getMetadata());
            addEnclaveModels(initModelList, enclaveIdProvider);

            String saltMetadataPath = config.getString(Const.Config.SaltsMetadataPathProp);
            RotatingSaltProvider saltProvider = new RotatingSaltProvider(cloudStorage, saltMetadataPath);
            saltProvider.loadContent();
            addSaltModels(initModelList, saltProvider);

            String partnerMetadataPath = config.getString(RotatingPartnerStore.PARTNERS_METADATA_PATH);
            RotatingPartnerStore partnerConfigProvider = new RotatingPartnerStore(cloudStorage, partnerMetadataPath);
            partnerConfigProvider.loadContent();
            addPartnerModels(initModelList, partnerConfigProvider);

            if(!isSetup()){
                boolean createdAllItems = false;
                for(OperationModel model : initModelList){
                    if(!createdAllItems){
                        createdAllItems = true;
                        insertIntoQLDB(new OperationModel(model.itemType, Constants.DEFAULT_ITEM_KEY,
                                null, null, null));
                    }
                    insertIntoQLDB(model);
                }
            }
            vertx.close();
            System.exit(0);
        }
        catch(Exception e){
            System.out.println("Failed to initialize QLDB");
            vertx.close();
            System.exit(-1);
        }
    }

    private static boolean isSetup(){
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

    private static void insertIntoQLDB(OperationModel model){
        QLDBAuditModel auditModel = new QLDBAuditModel(model.itemType, model.itemKey, model.actionTaken, null,
                null, null, -1, model.itemHash, model.summary);
        qldbDriver.execute(txn -> {
            JsonObject jsonObject = new JsonObject();
            jsonObject.put("data", auditModel.writeToJson());
            txn.execute("INSERT INTO " + Constants.QLDB_TABLE_NAME + " VALUE ?",
                    ionSys.newLoader().load(jsonObject.toString()).get(0));
        });
    }

    private static void addAdminModels(Collection<OperationModel> models, AdminUserProvider adminUserProvider){
        try {
            Collection<AdminUser> adminUsers = adminUserProvider.getAll();
            for (AdminUser a : adminUsers) {
                models.add(new OperationModel(Type.ADMIN, a.getName(), null,
                        DigestUtils.sha256Hex(jsonWriter.writeValueAsString(a)), null));
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    private static void addSiteModels(Collection<OperationModel> models, RotatingSiteStore siteProvider){
        try{
            Collection<Site> sites = siteProvider.getAllSites();
            for(Site s : sites){
                models.add(new OperationModel(Type.SITE, s.getName(), null,
                        DigestUtils.sha256Hex(jsonWriter.writeValueAsString(s)), null));
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    private static void addClientModels(Collection<OperationModel> models, RotatingClientKeyProvider clientKeyProvider){
        try {
            Collection<ClientKey> clients = clientKeyProvider.getAll();
            for (ClientKey c : clients) {
                models.add(new OperationModel(Type.CLIENT, c.getName(), null,
                        DigestUtils.sha256Hex(jsonWriter.writeValueAsString(c)), null));
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    private static void addEncryptionKeyModels(Collection<OperationModel> models, RotatingKeyStore keyProvider){
        try{
            Collection<EncryptionKey> keys = keyProvider.getSnapshot().getActiveKeySet();
            for(EncryptionKey k : keys){
                models.add(new OperationModel(Type.KEY, String.valueOf(k.getId()), null,
                        EncryptionKeyService.hashedToJsonWithKey(k), null));
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    private static void addKeyAclModels(Collection<OperationModel> models, RotatingKeyAclProvider keyAclProvider){
        try{
            Map<Integer, EncryptionKeyAcl> mapAcl = keyAclProvider.getSnapshot().getAllAcls();
            for(int i : mapAcl.keySet()){
                JsonArray listedSites = new JsonArray();
                EncryptionKeyAcl acl = mapAcl.get(i);
                acl.getAccessList().stream().sorted().forEach(listedSites::add);
                JsonObject jo = new JsonObject();
                jo.put("site_id", i);
                jo.put(acl.getIsWhitelist() ? "whitelist" : "blacklist", listedSites);

                models.add(new OperationModel(Type.KEYACL, String.valueOf(i), null,
                        DigestUtils.sha256Hex(jo.toString()), null));
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    private static void addOperatorModels(Collection<OperationModel> models, RotatingOperatorKeyProvider operatorKeyProvider){
        try{
            Collection<OperatorKey> operatorCollection = operatorKeyProvider.getAll();
            for(OperatorKey o : operatorCollection){
                models.add(new OperationModel(Type.OPERATOR, o.getName(), null,
                        DigestUtils.sha256Hex(jsonWriter.writeValueAsString(o)), null));
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    private static void addEnclaveModels(Collection<OperationModel> models, EnclaveIdentifierProvider enclaveIdProvider){
        try{
            Collection<EnclaveIdentifier> enclaves = enclaveIdProvider.getAll();
            for(EnclaveIdentifier e : enclaves){
                models.add(new OperationModel(Type.ENCLAVE, e.getName(), null,
                        DigestUtils.sha256Hex(jsonWriter.writeValueAsString(e)), null));
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    private static void addSaltModels(Collection<OperationModel> models, RotatingSaltProvider saltProvider){
        try{
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

            models.add(new OperationModel(Type.SALT, "singleton", null,
                    DigestUtils.sha256Hex(jo.toString()), null));
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    private static void addPartnerModels(Collection<OperationModel> models, RotatingPartnerStore partnerConfigProvider){
        try{
            String config = partnerConfigProvider.getConfig();
            models.add(new OperationModel(Type.PARTNER, "singleton", null,
                    DigestUtils.sha256Hex(config), null));
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }
}
