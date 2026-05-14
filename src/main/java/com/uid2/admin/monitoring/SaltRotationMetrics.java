package com.uid2.admin.monitoring;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.concurrent.atomic.AtomicLong;

public final class SaltRotationMetrics {
    private static final AtomicLong lastRotatedSaltCount = new AtomicLong(-1);

    public static void register(MeterRegistry registry) {
        // Reports NaN until the first rotation completes, so the alert does not fire on cold start.
        Gauge.builder("uid2_salts_rotated_last_cycle", lastRotatedSaltCount, SaltRotationMetrics::asGaugeValue)
                .description("Number of salts rotated in the most recent successful salt rotation cycle")
                .strongReference(true)
                .register(registry);
    }

    public static void recordRotated(int count) {
        lastRotatedSaltCount.set(count);
    }

    private static double asGaugeValue(AtomicLong ref) {
        long value = ref.get();
        return value < 0 ? Double.NaN : (double) value;
    }

    private SaltRotationMetrics() {}
}
