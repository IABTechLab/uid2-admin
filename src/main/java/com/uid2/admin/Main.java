package com.uid2.admin;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.uid2.admin.auth.AdminUserProvider;
import com.uid2.admin.auth.GithubAuthFactory;
import com.uid2.admin.auth.AuthFactory;
import com.uid2.admin.job.JobDispatcher;
import com.uid2.admin.job.jobsync.PrivateSiteDataSyncJob;
import com.uid2.admin.model.Site;
import com.uid2.admin.secret.ISaltRotation;
import com.uid2.admin.secret.SaltRotation;
import com.uid2.admin.store.*;
import com.uid2.admin.store.reader.RotatingPartnerStore;
import com.uid2.admin.store.reader.RotatingSiteStore;
import com.uid2.admin.store.version.EpochVersionGenerator;
import com.uid2.admin.store.version.VersionGenerator;
import com.uid2.admin.store.writer.*;
import com.uid2.admin.vertx.AdminVerticle;
import com.uid2.admin.vertx.JsonUtil;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.admin.vertx.service.*;
import com.uid2.shared.Const;
import com.uid2.shared.Utils;
import com.uid2.shared.secret.IKeyGenerator;
import com.uid2.shared.secret.SecureKeyGenerator;
import com.uid2.shared.auth.EnclaveIdentifierProvider;
import com.uid2.shared.auth.RotatingOperatorKeyProvider;
import com.uid2.shared.cloud.CloudUtils;
import com.uid2.shared.cloud.TaggableCloudStorage;
import com.uid2.shared.jmx.AdminApi;
import com.uid2.shared.middleware.AuthMiddleware;
import com.uid2.shared.store.CloudPath;
import com.uid2.shared.store.RotatingSaltProvider;
import com.uid2.shared.store.reader.*;
import com.uid2.shared.store.scope.GlobalScope;
import com.uid2.shared.vertx.RotatingStoreVerticle;
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
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.micrometer.Label;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxPrometheusOptions;
import io.vertx.micrometer.backends.BackendRegistries;

import javax.management.*;
import java.lang.management.ManagementFactory;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Optional;

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
            AuthFactory authFactory = new GithubAuthFactory(config);
            TaggableCloudStorage cloudStorage = CloudUtils.createStorage(config.getString(Const.Config.CoreS3BucketProp), config);
            FileStorage fileStorage = new TmpFileStorage();
            ObjectWriter jsonWriter = JsonUtil.createJsonWriter();
            FileManager fileManager = new FileManager(cloudStorage, fileStorage);
            Clock clock = new InstantClock();
            VersionGenerator versionGenerator = new EpochVersionGenerator(clock);

            String adminsMetadataPath = config.getString(AdminUserProvider.ADMINS_METADATA_PATH);
            AdminUserProvider adminUserProvider = new AdminUserProvider(cloudStorage, adminsMetadataPath);
            adminUserProvider.loadContent(adminUserProvider.getMetadata());
            AdminUserStoreWriter adminUserStoreWriter = new AdminUserStoreWriter(adminUserProvider, fileManager, jsonWriter, versionGenerator);

            CloudPath sitesMetadataPath = new CloudPath(config.getString(RotatingSiteStore.SITES_METADATA_PATH));
            GlobalScope siteGlobalScope = new GlobalScope(sitesMetadataPath);
            RotatingSiteStore siteProvider = new RotatingSiteStore(cloudStorage, siteGlobalScope);
            siteProvider.loadContent(siteProvider.getMetadata());
            StoreWriter<Collection<Site>> siteStoreWriter = new SiteStoreWriter(siteProvider, fileManager, jsonWriter, versionGenerator, clock, siteGlobalScope);

            CloudPath clientMetadataPath = new CloudPath(config.getString(Const.Config.ClientsMetadataPathProp));
            GlobalScope clientGlobalScope = new GlobalScope(clientMetadataPath);
            RotatingClientKeyProvider clientKeyProvider = new RotatingClientKeyProvider(cloudStorage, clientGlobalScope);
            clientKeyProvider.loadContent();
            ClientKeyStoreWriter clientKeyStoreWriter = new ClientKeyStoreWriter(clientKeyProvider, fileManager, jsonWriter, versionGenerator, clock, clientGlobalScope);

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

            CloudPath keysetMetadataPath = new CloudPath(config.getString(Const.Config.KeysetsMetadataPathProp));
            GlobalScope keysetGlobalScope = new GlobalScope(keysetMetadataPath);
            RotatingKeysetProvider keysetProvider = new RotatingKeysetProvider(cloudStorage, keysetGlobalScope);
            keysetProvider.loadContent();
            KeysetStoreWriter keysetStoreWriter = new KeysetStoreWriter(keysetProvider, fileManager, jsonWriter, versionGenerator, clock, keysetGlobalScope);

            CloudPath keysetKeyMetadataPath = new CloudPath(config.getString(Const.Config.KeysetKeysMetadataPathProp));
            GlobalScope keysetKeysGlobalScope = new GlobalScope(keysetKeyMetadataPath);
            RotatingKeysetKeyStore keysetKeysProvider = new RotatingKeysetKeyStore(cloudStorage, keysetKeysGlobalScope);
            keysetKeysProvider.loadContent();
            KeysetKeyStoreWriter keysetKeyStoreWriter = new KeysetKeyStoreWriter(keysetKeysProvider, fileManager, versionGenerator, clock, keysetKeysGlobalScope);

            CloudPath operatorMetadataPath = new CloudPath(config.getString(Const.Config.OperatorsMetadataPathProp));
            GlobalScope operatorScope = new GlobalScope(operatorMetadataPath);
            RotatingOperatorKeyProvider operatorKeyProvider = new RotatingOperatorKeyProvider(cloudStorage, cloudStorage, operatorScope);
            operatorKeyProvider.loadContent(operatorKeyProvider.getMetadata());
            OperatorKeyStoreWriter operatorKeyStoreWriter = new OperatorKeyStoreWriter(operatorKeyProvider, fileManager, jsonWriter, versionGenerator);

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

            AuthMiddleware auth = new AuthMiddleware(adminUserProvider);
            WriteLock writeLock = new WriteLock();
            IKeyGenerator keyGenerator = new SecureKeyGenerator();
            ISaltRotation saltRotation = new SaltRotation(config, keyGenerator);

            JobDispatcher jobDispatcher = new JobDispatcher("job-dispatcher", 1000 * 60, 3, clock);
            jobDispatcher.start();

            EncryptionKeyService encryptionKeyService = new EncryptionKeyService(
                    config, auth, writeLock, encryptionKeyStoreWriter, keysetKeyStoreWriter, keyProvider, keysetKeysProvider, keysetProvider, keysetStoreWriter, keyGenerator, clock);
            IService[] services = {
                    new AdminKeyService(config, auth, writeLock, adminUserStoreWriter, adminUserProvider, keyGenerator, clientKeyStoreWriter, encryptionKeyStoreWriter, keyAclStoreWriter),
                    new ClientKeyService(config, auth, writeLock, clientKeyStoreWriter, clientKeyProvider, siteProvider, encryptionKeyService, keyGenerator),
                    new EnclaveIdService(auth, writeLock, enclaveStoreWriter, enclaveIdProvider),
                    encryptionKeyService,
                    new KeyAclService(auth, writeLock, keyAclStoreWriter, keyAclProvider, siteProvider, encryptionKeyService),
                    new SharingService(auth, writeLock, keysetStoreWriter, keysetProvider, encryptionKeyService),
                    new OperatorKeyService(config, auth, writeLock, operatorKeyStoreWriter, operatorKeyProvider, siteProvider, keyGenerator),
                    new SaltService(auth, writeLock, saltStoreWriter, saltProvider, saltRotation),
                    new SiteService(auth, writeLock, siteStoreWriter, siteProvider, clientKeyProvider),
                    new PartnerConfigService(auth, writeLock, partnerStoreWriter, partnerConfigProvider),
                    new PrivateSiteDataRefreshService(auth, jobDispatcher, writeLock, config),
                    new JobDispatcherService(auth, jobDispatcher),
                    new SearchService(auth, clientKeyProvider, operatorKeyProvider, adminUserProvider)
            };

            RotatingStoreVerticle rotatingAdminUserStoreVerticle = new RotatingStoreVerticle(
                    "admins", 10000, adminUserProvider);
            vertx.deployVerticle(rotatingAdminUserStoreVerticle);

            AdminVerticle adminVerticle = new AdminVerticle(config, authFactory, adminUserProvider, services);
            vertx.deployVerticle(adminVerticle);

            //UID2-575 set up a job dispatcher that will write private site data periodically if there is any changes
            //check job for every minute
            PrivateSiteDataSyncJob job = new PrivateSiteDataSyncJob(config, writeLock);
            jobDispatcher.enqueue(job);
            jobDispatcher.executeNextJob();

            //UID2-628 keep keys.json and keyset_keys.json in sync. This function syncs them on start up
            encryptionKeyService.createKeysetKeys();
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
        BackendRegistries.setupBackend(metricOptions);

        MeterRegistry backendRegistry = BackendRegistries.getDefaultNow();
        if (backendRegistry instanceof PrometheusMeterRegistry) {
            // prometheus specific configuration
            PrometheusMeterRegistry prometheusRegistry = (PrometheusMeterRegistry) BackendRegistries.getDefaultNow();

            // see also https://micrometer.io/docs/registry/prometheus
            prometheusRegistry.config()
                    // providing common renaming for prometheus metric, e.g. "hello.world" to "hello_world"
                    .meterFilter(new PrometheusRenameFilter())
                    .meterFilter(MeterFilter.replaceTagValues(Label.HTTP_PATH.toString(), actualPath -> {
                        try {
                            return HttpUtils.normalizePath(actualPath).split("\\?")[0];
                        } catch (IllegalArgumentException e) {
                            return actualPath;
                        }
                    }))
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
