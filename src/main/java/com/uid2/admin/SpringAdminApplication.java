package com.uid2.admin;

import com.uid2.admin.vertx.SpringVerticle;
import com.uid2.shared.Utils;
import com.uid2.shared.jmx.AdminApi;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micrometer.prometheus.PrometheusRenameFilter;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.micrometer.Label;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxPrometheusOptions;
import io.vertx.micrometer.backends.BackendRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import javax.management.*;
import java.lang.management.ManagementFactory;
import java.util.EnumSet;

@SpringBootApplication
public class SpringAdminApplication {

    private final static Logger LOGGER = LoggerFactory.getLogger(SpringAdminApplication.class);

    private final int prometheusPort;
    private final String imageVersion;
    private final int portOffset;
    private final SpringVerticle springVerticle;

    public SpringAdminApplication(
            @Value("${prometheus.port}") int prometheusPort,
            @Value("${image_version:unknown}") String imageVersion,
            int portOffset,
            SpringVerticle springVerticle) {
        this.prometheusPort = prometheusPort;
        this.imageVersion = imageVersion;
        this.portOffset = portOffset;
        this.springVerticle = springVerticle;
    }

    public static void main(String[] args) {
        SpringApplication.run(SpringAdminApplication.class, args);
    }

    @EventListener
    public void deployVerticles(ApplicationReadyEvent event) {
        final Vertx vertx = createVertx();
        vertx.deployVerticle(springVerticle);
    }

    private Vertx createVertx() {
        try {
            final ObjectName objectName = new ObjectName("uid2.admin:type=jmx,name=AdminApi");
            final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            server.registerMBean(AdminApi.instance, objectName);
        } catch (InstanceAlreadyExistsException | MBeanRegistrationException | NotCompliantMBeanException | MalformedObjectNameException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }

        final VertxPrometheusOptions prometheusOptions = new VertxPrometheusOptions()
                .setStartEmbeddedServer(true)
                .setEmbeddedServerOptions(new HttpServerOptions().setPort(prometheusPort + portOffset))
                .setEnabled(true);

        final MicrometerMetricsOptions metricOptions = new MicrometerMetricsOptions()
                .setPrometheusOptions(prometheusOptions)
                .setLabels(EnumSet.of(Label.HTTP_METHOD, Label.HTTP_CODE, Label.HTTP_PATH))
                .setJvmMetricsEnabled(true)
                .setEnabled(true);
        setupMetrics(metricOptions);

        final int threadBlockedCheckInterval = Utils.isProductionEnvironment()
                ? 60 * 1000
                : 3600 * 1000;

        final VertxOptions vertxOptions = new VertxOptions()
                .setMetricsOptions(metricOptions)
                .setBlockedThreadCheckInterval(threadBlockedCheckInterval);

        return Vertx.vertx(vertxOptions);
    }

    private void setupMetrics(MicrometerMetricsOptions metricOptions) {
        BackendRegistries.setupBackend(metricOptions);

        final MeterRegistry backendRegistry = BackendRegistries.getDefaultNow();
        if (backendRegistry instanceof PrometheusMeterRegistry) {
            // prometheus specific configuration
            final PrometheusMeterRegistry prometheusRegistry = (PrometheusMeterRegistry) BackendRegistries.getDefaultNow();

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
        final Gauge appStatus = Gauge
                .builder("app.status", () -> 1)
                .description("application version and status")
                .tags("version", imageVersion)
                .register(Metrics.globalRegistry);
    }

}
