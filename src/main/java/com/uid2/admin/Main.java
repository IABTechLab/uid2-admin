// Copyright (c) 2021 The Trade Desk, Inc
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice,
//    this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice,
//    this list of conditions and the following disclaimer in the documentation
//    and/or other materials provided with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
// POSSIBILITY OF SUCH DAMAGE.

package com.uid2.admin;

import com.uid2.admin.audit.AuditFactory;
import com.uid2.admin.audit.AuditMiddleware;
import com.uid2.admin.audit.QLDBAuditMiddleware;
import com.uid2.admin.auth.*;
import com.uid2.admin.secret.IKeyGenerator;
import com.uid2.admin.secret.ISaltRotation;
import com.uid2.admin.secret.SaltRotation;
import com.uid2.admin.secret.SecureKeyGenerator;
import com.uid2.admin.store.CloudStorageManager;
import com.uid2.admin.store.IStorageManager;
import com.uid2.admin.store.RotatingPartnerStore;
import com.uid2.admin.store.RotatingSiteStore;
import com.uid2.admin.vertx.WriteLock;
import com.uid2.admin.vertx.service.*;
import com.uid2.shared.Const;
import com.uid2.shared.Utils;
import com.uid2.shared.auth.EnclaveIdentifierProvider;
import com.uid2.shared.auth.RotatingClientKeyProvider;
import com.uid2.shared.auth.RotatingKeyAclProvider;
import com.uid2.shared.auth.RotatingOperatorKeyProvider;
import com.uid2.shared.cloud.CloudUtils;
import com.uid2.shared.cloud.ICloudStorage;
import com.uid2.shared.jmx.AdminApi;
import com.uid2.shared.middleware.AuthMiddleware;
import com.uid2.shared.store.RotatingKeyStore;
import com.uid2.shared.store.RotatingSaltProvider;
import com.uid2.shared.vertx.RotatingStoreVerticle;
import com.uid2.shared.vertx.VertxUtils;
import com.uid2.admin.vertx.AdminVerticle;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micrometer.prometheus.PrometheusRenameFilter;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.micrometer.Label;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxPrometheusOptions;
import io.vertx.micrometer.backends.BackendRegistries;

import javax.management.*;
import java.lang.management.ManagementFactory;
import java.util.*;

public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private final Vertx vertx;
    private final JsonObject config;

    public Main(Vertx vertx, JsonObject config) {
        this.vertx = vertx;
        this.config = config;
    }

    public void run() {
        IAuthHandlerFactory authHandlerFactory = new GithubAuthHandlerFactory(config);
        ICloudStorage cloudStorage = CloudUtils.createStorage(config.getString(Const.Config.CoreS3BucketProp), config);
        IStorageManager storageManager = new CloudStorageManager(config, cloudStorage);
        try {
            String adminsMetadataPath = config.getString(AdminUserProvider.ADMINS_METADATA_PATH);
            AdminUserProvider adminUserProvider = new AdminUserProvider(cloudStorage, adminsMetadataPath);
            adminUserProvider.loadContent(adminUserProvider.getMetadata());

            String sitesMetadataPath = config.getString(RotatingSiteStore.SITES_METADATA_PATH);
            RotatingSiteStore siteProvider = new RotatingSiteStore(cloudStorage, sitesMetadataPath);
            siteProvider.loadContent(siteProvider.getMetadata());

            String clientMetadataPath = config.getString(Const.Config.ClientsMetadataPathProp);
            RotatingClientKeyProvider clientKeyProvider = new RotatingClientKeyProvider(cloudStorage, clientMetadataPath);
            clientKeyProvider.loadContent();

            String keyMetadataPath = config.getString(Const.Config.KeysMetadataPathProp);
            RotatingKeyStore keyProvider = new RotatingKeyStore(cloudStorage, keyMetadataPath);
            keyProvider.loadContent();

            String keyAclMetadataPath = config.getString(Const.Config.KeysAclMetadataPathProp);
            RotatingKeyAclProvider keyAclProvider = new RotatingKeyAclProvider(cloudStorage, keyAclMetadataPath);
            keyAclProvider.loadContent();

            String operatorMetadataPath = config.getString(Const.Config.OperatorsMetadataPathProp);
            RotatingOperatorKeyProvider operatorKeyProvider = new RotatingOperatorKeyProvider(cloudStorage, cloudStorage, operatorMetadataPath);
            operatorKeyProvider.loadContent(operatorKeyProvider.getMetadata());

            String enclaveMetadataPath = config.getString(EnclaveIdentifierProvider.ENCLAVES_METADATA_PATH);
            EnclaveIdentifierProvider enclaveIdProvider = new EnclaveIdentifierProvider(cloudStorage, enclaveMetadataPath);
            enclaveIdProvider.loadContent(enclaveIdProvider.getMetadata());

            String saltMetadataPath = config.getString(Const.Config.SaltsMetadataPathProp);
            RotatingSaltProvider saltProvider = new RotatingSaltProvider(cloudStorage, saltMetadataPath);
            saltProvider.loadContent();

            String partnerMetadataPath = config.getString(RotatingPartnerStore.PARTNERS_METADATA_PATH);
            RotatingPartnerStore partnerConfigProvider = new RotatingPartnerStore(cloudStorage, partnerMetadataPath);
            partnerConfigProvider.loadContent();

            AuditMiddleware audit = AuditFactory.getAuditMiddleware(Main.class);
            AuthMiddleware auth = new AuthMiddleware(adminUserProvider);
            WriteLock writeLock = new WriteLock();
            IKeyGenerator keyGenerator = new SecureKeyGenerator();
            ISaltRotation saltRotation = new SaltRotation(config, keyGenerator);

            final EncryptionKeyService encryptionKeyService = new EncryptionKeyService(
                    config, auth, writeLock, storageManager, keyProvider, keyGenerator);

            AdminVerticle adminVerticle = new AdminVerticle(authHandlerFactory, auth, adminUserProvider,
                    new AdminKeyService(config, audit, auth, writeLock, storageManager, adminUserProvider, keyGenerator),
                    new ClientKeyService(config, audit, auth, writeLock, storageManager, clientKeyProvider, siteProvider, keyGenerator),
                    new EnclaveIdService(audit, auth, writeLock, storageManager, enclaveIdProvider),
                    encryptionKeyService,
                    new KeyAclService(auth, writeLock, storageManager, keyAclProvider, siteProvider, encryptionKeyService),
                    new OperatorKeyService(config, auth, writeLock, storageManager, operatorKeyProvider, keyGenerator),
                    new SaltService(auth, writeLock, storageManager, saltProvider, saltRotation),
                    new SiteService(auth, writeLock, storageManager, siteProvider, clientKeyProvider),
                    new PartnerConfigService(auth, writeLock, storageManager, partnerConfigProvider));

            RotatingStoreVerticle rotatingAdminUserStoreVerticle = new RotatingStoreVerticle(
                    "admins", 10000, adminUserProvider);
            vertx.deployVerticle(rotatingAdminUserStoreVerticle);

            vertx.deployVerticle(adminVerticle);
        } catch (Exception e) {
            System.out.println("failed to initialize core verticle: " + e.getMessage());
            System.exit(-1);
        }
    }

    public static void main(String[] args) {
        final String vertxConfigPath = System.getProperty(Const.Config.VERTX_CONFIG_PATH_PROP);
        if (vertxConfigPath != null) {
            System.out.format("Running CUSTOM CONFIG mode, config: %s\n", vertxConfigPath);
        } else if (!Utils.isProductionEnvironment()) {
            System.out.format("Running LOCAL DEBUG mode, config: %s\n", Const.Config.LOCAL_CONFIG_PATH);
            System.setProperty(Const.Config.VERTX_CONFIG_PATH_PROP, Const.Config.LOCAL_CONFIG_PATH);
        } else {
            System.out.format("Running PRODUCTION mode, config: %s\n", Const.Config.OVERRIDE_CONFIG_PATH);
        }

        Vertx vertx = createVertx();
        VertxUtils.createConfigRetriever(vertx).getConfig(ar -> {
            if (ar.failed()) {
                LOGGER.fatal("Unable to read config: " + ar.cause().getMessage(), ar.cause());
                return;
            }

            try {
                Main app = new Main(vertx, ar.result());
                app.run();
            } catch (Exception e) {
                LOGGER.fatal("Error: " +e.getMessage(), e);
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
        } catch (InstanceAlreadyExistsException | MBeanRegistrationException | NotCompliantMBeanException | MalformedObjectNameException e) {
            System.err.format("%s", e.getMessage());
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
