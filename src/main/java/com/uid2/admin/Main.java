package com.uid2.admin;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.auth.AdminAuthMiddleware;
import com.uid2.admin.auth.OktaAuthProvider;
import com.uid2.admin.auth.AuthProvider;
import com.uid2.admin.auth.TokenRefreshHandler;
import com.uid2.admin.cloudencryption.*;
import com.uid2.admin.job.JobDispatcher;
import com.uid2.admin.job.jobsync.EncryptedFilesSyncJob;
import com.uid2.admin.job.jobsync.PrivateSiteDataSyncJob;
import com.uid2.admin.job.jobsync.keyset.ReplaceSharingTypesWithSitesJob;
import com.uid2.admin.legacy.LegacyClientKeyStoreWriter;
import com.uid2.admin.legacy.RotatingLegacyClientKeyProvider;
import com.uid2.admin.managers.KeysetManager;
import com.uid2.admin.monitoring.DataStoreMetrics;
import com.uid2.admin.secret.*;
import com.uid2.admin.store.*;
import com.uid2.admin.store.reader.RotatingAdminKeysetStore;
import com.uid2.admin.store.reader.RotatingPartnerStore;
import com.uid2.admin.store.version.EpochVersionGenerator;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.*;
import com.uid2.admin.vertx.AdminVerticle;
import com.uid2.admin.vertx.Endpoints;
import com.uid2.admin.vertx.JsonUtil;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.admin.vertx.api.V2RouterModule;
import com.uid2.admin.vertx.service.*;
import com.uid2.shared.Const;
import com.uid2.shared.Utils;
import com.uid2.shared.secret.KeyHasher;
import com.uid2.shared.secret.SecureKeyGenerator;
import com.uid2.shared.auth.EnclaveIdentifierProvider;
import com.uid2.shared.auth.RotatingOperatorKeyProvider;
import com.uid2.shared.cloud.CloudStorageException;
import com.uid2.shared.cloud.CloudUtils;
import com.uid2.shared.cloud.TaggableCloudStorage;
import com.uid2.shared.jmx.AdminApi;
import com.uid2.shared.model.Site;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.salt.RotatingSaltProvider;
import com.uid2.shared.store.reader.*;
import com.uid2.shared.store.scope.GlobalScope;
import com.uid2.shared.util.HTTPPathMetricFilter;
import com.uid2.shared.vertx.VertxUtils;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micrometer.prometheus.PrometheusRenameFilter;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.micrometer.Label;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxPrometheusOptions;
import io.vertx.micrometer.backends.BackendRegistries;

import javax.management.*;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.uid2.admin.AdminConst.enableKeysetConfigProp;

public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private final Vertx vertx;
    private final JsonObject config;
    public Main(Vertx vertx, JsonObject config) {
        this.vertx = vertx;
        this.config = config;
    }

    public void run() {
        try {
            boolean enableKeysets = config.getBoolean(enableKeysetConfigProp);
            AuthProvider authProvider = new OktaAuthProvider(config);
            TaggableCloudStorage cloudStorage = CloudUtils.createStorage(config.getString(Const.Config.CoreS3BucketProp), config);
            FileStorage fileStorage = new TmpFileStorage();
            ObjectWriter jsonWriter = JsonUtil.createJsonWriter();
            FileManager fileManager = new FileManager(cloudStorage, fileStorage);
            Clock clock = new InstantClock();
            VersionGenerator versionGenerator = new EpochVersionGenerator(clock);

            CloudPath sitesMetadataPath = new CloudPath(config.getString(RotatingSiteStore.SITES_METADATA_PATH));
            GlobalScope siteGlobalScope = new GlobalScope(sitesMetadataPath);
            RotatingSiteStore siteProvider = new RotatingSiteStore(cloudStorage, siteGlobalScope);
            siteProvider.loadContent(siteProvider.getMetadata());
            StoreWriter<Collection<Site>> siteStoreWriter = new SiteStoreWriter(siteProvider, fileManager, jsonWriter, versionGenerator, clock, siteGlobalScope);

            CloudPath clientMetadataPath = new CloudPath(config.getString(Const.Config.ClientsMetadataPathProp));
            GlobalScope clientGlobalScope = new GlobalScope(clientMetadataPath);
            RotatingLegacyClientKeyProvider clientKeyProvider = new RotatingLegacyClientKeyProvider(cloudStorage, clientGlobalScope);
            clientKeyProvider.loadContent();
            LegacyClientKeyStoreWriter clientKeyStoreWriter = new LegacyClientKeyStoreWriter(clientKeyProvider, fileManager, jsonWriter, versionGenerator, clock, clientGlobalScope);

            CloudPath keyMetadataPath = new CloudPath(config.getString(Const.Config.KeysMetadataPathProp));
            GlobalScope keyGlobalScope = new GlobalScope(keyMetadataPath);
            RotatingKeyStore keyProvider = new RotatingKeyStore(cloudStorage, keyGlobalScope);
            keyProvider.loadContent();
            EncryptionKeyStoreWriter encryptionKeyStoreWriter = new EncryptionKeyStoreWriter(keyProvider, fileManager, versionGenerator, clock, keyGlobalScope);

            CloudPath keyAclMetadataPath = new CloudPath(config.getString(Const.Config.KeysAclMetadataPathProp));
            GlobalScope keyAclGlobalScope = new GlobalScope(keyAclMetadataPath);
            RotatingKeyAclProvider keyAclProvider = new RotatingKeyAclProvider(cloudStorage, keyAclGlobalScope);
            keyAclProvider.loadContent();
            KeyAclStoreWriter keyAclStoreWriter = new KeyAclStoreWriter(keyAclProvider, fileManager, jsonWriter, versionGenerator, clock, keyAclGlobalScope);

            CloudPath adminKeysetMetadataPath = new CloudPath(config.getString("admin_keysets_metadata_path"));
            GlobalScope adminKeysetGlobalScope = new GlobalScope(adminKeysetMetadataPath);
            RotatingAdminKeysetStore adminKeysetProvider = new RotatingAdminKeysetStore(cloudStorage, adminKeysetGlobalScope);
            AdminKeysetWriter adminKeysetStoreWriter = new AdminKeysetWriter(adminKeysetProvider, fileManager, jsonWriter, versionGenerator, clock, adminKeysetGlobalScope);
            try {
                adminKeysetProvider.loadContent();
            } catch (CloudStorageException e) {
                if (e.getMessage().contains("The specified key does not exist")) {
                    adminKeysetStoreWriter.upload(new HashMap<>(), null);
                    adminKeysetProvider.loadContent();
                } else {
                    throw e;
                }
            }

            CloudPath keysetKeyMetadataPath = new CloudPath(config.getString(Const.Config.KeysetKeysMetadataPathProp));
            GlobalScope keysetKeysGlobalScope = new GlobalScope(keysetKeyMetadataPath);
            RotatingKeysetKeyStore keysetKeysProvider = new RotatingKeysetKeyStore(cloudStorage, keysetKeysGlobalScope);
            KeysetKeyStoreWriter keysetKeyStoreWriter = new KeysetKeyStoreWriter(keysetKeysProvider, fileManager, versionGenerator, clock, keysetKeysGlobalScope, enableKeysets);
            if (enableKeysets) {
                try {
                    keysetKeysProvider.loadContent();
                } catch (CloudStorageException e) {
                    if (e.getMessage().contains("The specified key does not exist")) {
                        keysetKeyStoreWriter.upload(new HashSet<>(), 0);
                        keysetKeysProvider.loadContent();
                    } else {
                        throw e;
                    }
                }
            }

            CloudPath clientSideKeypairMetadataPath = new CloudPath(config.getString(Const.Config.ClientSideKeypairsMetadataPathProp));
            GlobalScope clientSideKeypairGlobalScope = new GlobalScope(clientSideKeypairMetadataPath);
            RotatingClientSideKeypairStore clientSideKeypairProvider = new RotatingClientSideKeypairStore(cloudStorage, clientSideKeypairGlobalScope);
            ClientSideKeypairStoreWriter clientSideKeypairStoreWriter = new ClientSideKeypairStoreWriter(clientSideKeypairProvider, fileManager, versionGenerator, clock, clientSideKeypairGlobalScope);
            try {
                clientSideKeypairProvider.loadContent();
            } catch (CloudStorageException e) {
                if (e.getMessage().contains("The specified key does not exist")) {
                    clientSideKeypairStoreWriter.upload(new HashSet<>(), null);
                    clientSideKeypairProvider.loadContent();
                } else {
                    throw e;
                }
            }

            CloudPath serviceMetadataPath = new CloudPath(config.getString(Const.Config.ServiceMetadataPathProp));
            GlobalScope serviceGlobalScope = new GlobalScope(serviceMetadataPath);
            RotatingServiceStore serviceProvider = new RotatingServiceStore(cloudStorage, serviceGlobalScope);
            ServiceStoreWriter serviceStoreWriter = new ServiceStoreWriter(serviceProvider, fileManager, jsonWriter, versionGenerator, clock, serviceGlobalScope);
            try {
                serviceProvider.loadContent();
            } catch (CloudStorageException e) {
                if (e.getMessage().contains("The specified key does not exist")) {
                    serviceStoreWriter.upload(new HashSet<>(), null);
                    serviceProvider.loadContent();
                } else {
                    throw e;
                }
            }

            CloudPath serviceLinkMetadataPath = new CloudPath(config.getString(Const.Config.ServiceLinkMetadataPathProp));
            GlobalScope serviceLinkGlobalScope = new GlobalScope(serviceLinkMetadataPath);
            RotatingServiceLinkStore serviceLinkProvider = new RotatingServiceLinkStore(cloudStorage, serviceLinkGlobalScope);
            ServiceLinkStoreWriter serviceLinkStoreWriter = new ServiceLinkStoreWriter(serviceLinkProvider, fileManager, jsonWriter, versionGenerator, clock, serviceLinkGlobalScope);
            try {
                serviceLinkProvider.loadContent();
            } catch (CloudStorageException e) {
                if (e.getMessage().contains("The specified key does not exist")) {
                    serviceLinkStoreWriter.upload(new HashSet<>(), null);
                    serviceLinkProvider.loadContent();
                } else {
                    throw e;
                }
            }

            CloudPath operatorMetadataPath = new CloudPath(config.getString(Const.Config.OperatorsMetadataPathProp));
            GlobalScope operatorScope = new GlobalScope(operatorMetadataPath);
            RotatingOperatorKeyProvider operatorKeyProvider = new RotatingOperatorKeyProvider(cloudStorage, cloudStorage, operatorScope);
            operatorKeyProvider.loadContent(operatorKeyProvider.getMetadata());
            OperatorKeyStoreWriter operatorKeyStoreWriter = new OperatorKeyStoreWriter(operatorKeyProvider, fileManager, jsonWriter, versionGenerator);

            CloudPath cloudEncryptionKeyMetadataPath = new CloudPath(config.getString(Const.Config.CloudEncryptionKeysMetadataPathProp));
            GlobalScope cloudEncryptionKeyGlobalScope = new GlobalScope(cloudEncryptionKeyMetadataPath);
            RotatingCloudEncryptionKeyProvider rotatingCloudEncryptionKeyProvider = new RotatingCloudEncryptionKeyProvider(cloudStorage, cloudEncryptionKeyGlobalScope);
            CloudEncryptionKeyStoreWriter cloudEncryptionKeyStoreWriter = new CloudEncryptionKeyStoreWriter(rotatingCloudEncryptionKeyProvider, fileManager, jsonWriter, versionGenerator, clock, cloudEncryptionKeyGlobalScope);
            SecureKeyGenerator keyGenerator = new SecureKeyGenerator();
            try {
                rotatingCloudEncryptionKeyProvider.loadContent();
            } catch (CloudStorageException e) {
                if (e.getMessage().contains("The specified key does not exist")) {
                    cloudEncryptionKeyStoreWriter.upload(new HashMap<>(), null);
                    rotatingCloudEncryptionKeyProvider.loadContent();
                } else {
                    throw e;
                }
            }

            String enclaveMetadataPath = config.getString(EnclaveIdentifierProvider.ENCLAVES_METADATA_PATH);
            EnclaveIdentifierProvider enclaveIdProvider = new EnclaveIdentifierProvider(cloudStorage, enclaveMetadataPath);
            enclaveIdProvider.loadContent(enclaveIdProvider.getMetadata());
            EnclaveStoreWriter enclaveStoreWriter = new EnclaveStoreWriter(enclaveIdProvider, fileManager, jsonWriter, versionGenerator);

            String saltMetadataPath = config.getString(Const.Config.SaltsMetadataPathProp);
            RotatingSaltProvider saltProvider = new RotatingSaltProvider(cloudStorage, saltMetadataPath);
            saltProvider.loadContent();
            SaltStoreWriter saltStoreWriter = new SaltStoreWriter(config, saltProvider, fileManager, cloudStorage, versionGenerator);

            String partnerMetadataPath = config.getString(RotatingPartnerStore.PARTNERS_METADATA_PATH);
            RotatingPartnerStore partnerConfigProvider = new RotatingPartnerStore(cloudStorage, partnerMetadataPath);
            partnerConfigProvider.loadContent();
            PartnerStoreWriter partnerStoreWriter = new PartnerStoreWriter(partnerConfigProvider, fileManager, versionGenerator);

            AdminAuthMiddleware auth = new AdminAuthMiddleware(authProvider, config);
            TokenRefreshHandler tokenRefreshHandler = new TokenRefreshHandler(authProvider.getIdTokenVerifier(), config);
            WriteLock writeLock = new WriteLock();
            KeyHasher keyHasher = new KeyHasher();
            IKeypairGenerator keypairGenerator = new SecureKeypairGenerator();
            ISaltRotation saltRotation = new SaltRotation(keyGenerator);
            EncryptionKeyService encryptionKeyService = new EncryptionKeyService(
                    config, auth, writeLock, encryptionKeyStoreWriter, keysetKeyStoreWriter, keyProvider, keysetKeysProvider, adminKeysetProvider, adminKeysetStoreWriter, keyGenerator, clock);
            KeysetManager keysetManager = new KeysetManager(
                    adminKeysetProvider, adminKeysetStoreWriter, encryptionKeyService, enableKeysets
            );

            JobDispatcher jobDispatcher = new JobDispatcher("job-dispatcher", 1000 * 60, 3, clock);
            jobDispatcher.start();

            ClientSideKeypairService clientSideKeypairService = new ClientSideKeypairService(config, auth, writeLock, clientSideKeypairStoreWriter, clientSideKeypairProvider, siteProvider, keysetManager, keypairGenerator, clock);

            var cloudEncryptionSecretGenerator = new CloudSecretGenerator(keyGenerator);
            var cloudEncryptionKeyRetentionStrategy = new ExpiredKeyCountRetentionStrategy(10);
            var cloudEncryptionKeyRotationStrategy = new CloudKeyStatePlanner(cloudEncryptionSecretGenerator, clock, cloudEncryptionKeyRetentionStrategy);
            var cloudEncryptionKeyManager = new CloudEncryptionKeyManager(rotatingCloudEncryptionKeyProvider, cloudEncryptionKeyStoreWriter, operatorKeyProvider, cloudEncryptionKeyRotationStrategy);

            IService[] services = {
                    new ClientKeyService(config, auth, writeLock, clientKeyStoreWriter, clientKeyProvider, siteProvider, keysetManager, keyGenerator, keyHasher),
                    new EnclaveIdService(auth, writeLock, enclaveStoreWriter, enclaveIdProvider, clock),
                    encryptionKeyService,
                    new KeyAclService(auth, writeLock, keyAclStoreWriter, keyAclProvider, siteProvider, encryptionKeyService),
                    new SharingService(auth, writeLock, adminKeysetProvider, keysetManager, siteProvider, enableKeysets, clientKeyProvider),
                    clientSideKeypairService,
                    new ServiceService(auth, writeLock, serviceStoreWriter, serviceProvider, siteProvider, serviceLinkProvider),
                    new ServiceLinkService(auth, writeLock, serviceLinkStoreWriter, serviceLinkProvider, serviceProvider, siteProvider),
                    new OperatorKeyService(config, auth, writeLock, operatorKeyStoreWriter, operatorKeyProvider, siteProvider, keyGenerator, keyHasher, cloudEncryptionKeyManager),
                    new SaltService(auth, writeLock, saltStoreWriter, saltProvider, saltRotation),
                    new SiteService(auth, writeLock, siteStoreWriter, siteProvider, clientKeyProvider),
                    new PartnerConfigService(auth, writeLock, partnerStoreWriter, partnerConfigProvider),
                    new PrivateSiteDataRefreshService(auth, jobDispatcher, writeLock, config),
                    new EncryptedFilesSyncService(auth, jobDispatcher, writeLock, config, rotatingCloudEncryptionKeyProvider),
                    new JobDispatcherService(auth, jobDispatcher),
                    new SearchService(auth, clientKeyProvider, operatorKeyProvider),
                    new CloudEncryptionKeyService(auth, cloudEncryptionKeyManager, jobDispatcher)
            };


            V2RouterModule v2RouterModule = new V2RouterModule(clientSideKeypairService, auth);

            AdminVerticle adminVerticle = new AdminVerticle(config, authProvider, tokenRefreshHandler, services, v2RouterModule.getRouter());
            vertx.deployVerticle(adminVerticle);

            CloudPath keysetMetadataPath = new CloudPath(config.getString("keysets_metadata_path"));
            GlobalScope keysetGlobalScope = new GlobalScope(keysetMetadataPath);
            RotatingKeysetProvider keysetProvider = new RotatingKeysetProvider(cloudStorage, keysetGlobalScope);
            KeysetStoreWriter keysetStoreWriter = new KeysetStoreWriter(keysetProvider, fileManager, jsonWriter, versionGenerator, clock, keysetGlobalScope, enableKeysets);
            try {
                keysetProvider.loadContent();
            } catch (CloudStorageException e) {
                if (e.getMessage().contains("The specified key does not exist")) {
                    keysetStoreWriter.upload(new HashMap<>(), null);
                    keysetProvider.loadContent();
                } else {
                    throw e;
                }
            }

            synchronized (writeLock) {
                cloudEncryptionKeyManager.backfillKeys();
                rotatingCloudEncryptionKeyProvider.loadContent();
            }

            /*
            This if statement will:
            1. create all copy keysets to admin keysets
            2. Create all the keyset keys from the encryption keys
            This should only need to happen the first time Admin starts and either of the files has not been caught up for that ENV.
            It is synchronized so that this completes before any other operation is started.
            The jobs are executed after because they copy data from these files locations consumed by public and private operators.
            This caused an issue because the files were empty and the job started to fail so the operators got empty files.
             */
            if (enableKeysets) {
                synchronized (writeLock) {
                    //UID2-628 keep keys.json and keyset_keys.json in sync. This function syncs them on start up
                    keysetProvider.loadContent();
                    keysetManager.createAdminKeysets(keysetProvider.getAll());
                    encryptionKeyService.createKeysetKeys();
                }
            }

            // Data type keys should be matching uid2_config_store_version reported by operator, core, etc
            DataStoreMetrics.addDataStoreMetrics("site", siteProvider);
            DataStoreMetrics.addDataStoreMetrics("auth", clientKeyProvider);
            DataStoreMetrics.addDataStoreMetrics("key", keyProvider);
            DataStoreMetrics.addDataStoreMetrics("keys_acl", keyAclProvider);
            DataStoreMetrics.addDataStoreMetrics("keyset", keysetProvider);
            DataStoreMetrics.addDataStoreMetrics("keysetkey", keysetKeysProvider);
            DataStoreMetrics.addDataStoreMetrics("cskeypair", clientSideKeypairProvider);
            DataStoreMetrics.addDataStoreMetrics("operators", operatorKeyProvider);
            DataStoreMetrics.addDataStoreMetrics("enclaves", enclaveIdProvider);
            DataStoreMetrics.addDataStoreMetrics("salt", saltProvider);
            DataStoreMetrics.addDataStoreMetrics("partners", partnerConfigProvider);
            DataStoreMetrics.addDataStoreMetrics("service_link", serviceLinkProvider);
            DataStoreMetrics.addDataStoreServiceLinkEntryCount("snowflake", serviceLinkProvider, serviceProvider);


            ReplaceSharingTypesWithSitesJob replaceSharingTypesWithSitesJob = new ReplaceSharingTypesWithSitesJob(config, writeLock, adminKeysetProvider, keysetProvider, keysetStoreWriter, siteProvider);
            jobDispatcher.enqueue(replaceSharingTypesWithSitesJob);
            CompletableFuture<Boolean> replaceSharingTypesWithSitesJobFuture = jobDispatcher.executeNextJob();
            replaceSharingTypesWithSitesJobFuture.get();

            //UID2-575 set up a job dispatcher that will write private site data periodically if there is any changes
            //check job for every minute
            PrivateSiteDataSyncJob privateSiteDataSyncJob = new PrivateSiteDataSyncJob(config, writeLock);
            jobDispatcher.enqueue(privateSiteDataSyncJob);
            CompletableFuture<Boolean> privateSiteDataSyncJobFuture = jobDispatcher.executeNextJob();
            privateSiteDataSyncJobFuture.get();

            EncryptedFilesSyncJob encryptedFilesSyncJob = new EncryptedFilesSyncJob(config, writeLock, rotatingCloudEncryptionKeyProvider);
            jobDispatcher.enqueue(encryptedFilesSyncJob);
            CompletableFuture<Boolean> encryptedFilesSyncJobFuture = jobDispatcher.executeNextJob();
            encryptedFilesSyncJobFuture.get();
        } catch (Exception e) {
            LOGGER.error("failed to initialize admin verticle", e);
            System.exit(-1);
        }
    }

    public static void main(String[] args) {
        final String vertxConfigPath = System.getProperty(Const.Config.VERTX_CONFIG_PATH_PROP);
        if (vertxConfigPath != null) {
            LOGGER.info("Running CUSTOM CONFIG mode, config: {}", vertxConfigPath);
        } else if (!Utils.isProductionEnvironment()) {
            LOGGER.info("Running LOCAL DEBUG mode, config: {}", Const.Config.LOCAL_CONFIG_PATH);
            System.setProperty(Const.Config.VERTX_CONFIG_PATH_PROP, Const.Config.LOCAL_CONFIG_PATH);
        } else {
            LOGGER.info("Running PRODUCTION mode, config: {}", Const.Config.OVERRIDE_CONFIG_PATH);
        }

        Vertx vertx = createVertx();
        VertxUtils.createConfigRetriever(vertx).getConfig(ar -> {
            if (ar.failed()) {
                LOGGER.error("Unable to read config: " + ar.cause().getMessage(), ar.cause());
                return;
            }

            try {
                Main app = new Main(vertx, ar.result());
                app.run();
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
                vertx.close();
                System.exit(1);
            }
        });
    }

    private static Vertx createVertx() {
        try {
            ObjectName objectName = new ObjectName("uid2.admin:type=jmx,name=AdminApi");
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            server.registerMBean(AdminApi.instance, objectName);
        } catch (InstanceAlreadyExistsException | MBeanRegistrationException | NotCompliantMBeanException |
                 MalformedObjectNameException e) {
            LOGGER.error(e.getMessage(), e);
            System.exit(-1);
        }

        final int portOffset = Utils.getPortOffset();
        VertxPrometheusOptions prometheusOptions = new VertxPrometheusOptions()
                .setStartEmbeddedServer(true)
                .setEmbeddedServerOptions(new HttpServerOptions().setPort(Const.Port.PrometheusPortForAdmin + portOffset))
                .setEnabled(true);

        MicrometerMetricsOptions metricOptions = new MicrometerMetricsOptions()
                .setPrometheusOptions(prometheusOptions)
                .setLabels(EnumSet.of(Label.HTTP_METHOD, Label.HTTP_CODE, Label.HTTP_PATH))
                .setJvmMetricsEnabled(true)
                .setEnabled(true);
        setupMetrics(metricOptions);

        final int threadBlockedCheckInterval = Utils.isProductionEnvironment()
                ? 60 * 1000
                : 3600 * 1000;

        VertxOptions vertxOptions = new VertxOptions()
                .setMetricsOptions(metricOptions)
                .setBlockedThreadCheckInterval(threadBlockedCheckInterval);

        return Vertx.vertx(vertxOptions);
    }

    private static void setupMetrics(MicrometerMetricsOptions metricOptions) {
        BackendRegistries.setupBackend(metricOptions, null);

        MeterRegistry backendRegistry = BackendRegistries.getDefaultNow();
        if (backendRegistry instanceof PrometheusMeterRegistry) {
            // prometheus specific configuration
            PrometheusMeterRegistry prometheusRegistry = (PrometheusMeterRegistry) BackendRegistries.getDefaultNow();

            // see also https://micrometer.io/docs/registry/prometheus
            prometheusRegistry.config()
                    // providing common renaming for prometheus metric, e.g. "hello.world" to "hello_world"
                    .meterFilter(new PrometheusRenameFilter())
                    .meterFilter(MeterFilter.replaceTagValues(Label.HTTP_PATH.toString(),
                            actualPath -> HTTPPathMetricFilter.filterPath(actualPath, Endpoints.pathSet())))
                    // adding common labels
                    .commonTags("application", "uid2-admin");

            // wire my monitoring system to global static state, see also https://micrometer.io/docs/concepts
            Metrics.addRegistry(prometheusRegistry);
        }

        // retrieve image version (will unify when uid2-common is used)
        String version = Optional.ofNullable(System.getenv("IMAGE_VERSION")).orElse("unknown");
        Gauge appStatus = Gauge
                .builder("app.status", () -> 1)
                .description("application version and status")
                .tags("version", version)
                .register(Metrics.globalRegistry);
    }
}
