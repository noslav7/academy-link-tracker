package backend.academy.linktracker.bot.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Recorder пользовательских метрик bot-сервиса.
 */
@Component
public class BotMetricsRecorder {
    private static final String METRIC_COMMAND_REQUESTS = "command_requests_total";
    private static final String METRIC_COMMAND_DURATION = "command_duration_ms_total";
    private static final String METRIC_SENT_NOTIFICATION = "sent_notification_total";
    private static final long[] COMMAND_DURATION_BUCKETS_MS = {5, 10, 25, 50, 100, 250, 500, 1_000, 2_000, 5_000};
    private static final BotMetricsRecorder NOOP_INSTANCE = new NoopBotMetricsRecorder();

    private final MeterRegistry meterRegistry;
    private final Map<String, Counter> commandCounters = new ConcurrentHashMap<>();
    private final Map<String, DistributionSummary> commandDurationSummaries = new ConcurrentHashMap<>();
    private final Counter sentNotificationCounter;

    public BotMetricsRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.sentNotificationCounter = Counter.builder(METRIC_SENT_NOTIFICATION)
                .description("Total sent notifications from bot")
                .register(meterRegistry);
    }

    public static BotMetricsRecorder noop() {
        return NOOP_INSTANCE;
    }

    public void incrementCommandRequest(String command) {
        if (command == null || command.isBlank()) {
            return;
        }
        commandCounters.computeIfAbsent(command, this::buildCommandCounter).increment();
    }

    public void incrementSentNotification() {
        sentNotificationCounter.increment();
    }

    public void recordDuration(String scope, String scopeType, long durationNanos) {
        if (scope == null || scope.isBlank() || scopeType == null || scopeType.isBlank()) {
            return;
        }
        double durationMs = TimeUnit.NANOSECONDS.toMillis(Math.max(0L, durationNanos));
        commandDurationSummaries
                .computeIfAbsent(scope + ":" + scopeType, ignored -> buildCommandDurationSummary(scope, scopeType))
                .record(durationMs);
    }

    public void recordDuration(String scope, String scopeType, Runnable operation) {
        long startedAt = System.nanoTime();
        try {
            operation.run();
        } finally {
            recordDuration(scope, scopeType, System.nanoTime() - startedAt);
        }
    }

    public <T> T recordDuration(String scope, String scopeType, Supplier<T> operation) {
        long startedAt = System.nanoTime();
        try {
            return operation.get();
        } finally {
            recordDuration(scope, scopeType, System.nanoTime() - startedAt);
        }
    }

    private Counter buildCommandCounter(String command) {
        return Counter.builder(METRIC_COMMAND_REQUESTS)
                .description("Total number of processed bot commands")
                .tags("command", command)
                .register(meterRegistry);
    }

    private DistributionSummary buildCommandDurationSummary(String scope, String scopeType) {
        double[] buckets = new double[COMMAND_DURATION_BUCKETS_MS.length];
        for (int i = 0; i < COMMAND_DURATION_BUCKETS_MS.length; i++) {
            buckets[i] = COMMAND_DURATION_BUCKETS_MS[i];
        }
        return DistributionSummary.builder(METRIC_COMMAND_DURATION)
                .description("Single bot operation duration in milliseconds")
                .baseUnit("milliseconds")
                .serviceLevelObjectives(buckets)
                .tags("scope", scope, "scope_type", scopeType)
                .register(meterRegistry);
    }

    private static final class NoopBotMetricsRecorder extends BotMetricsRecorder {
        private NoopBotMetricsRecorder() {
            super(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        }

        @Override
        public void incrementCommandRequest(String command) {}

        @Override
        public void incrementSentNotification() {}

        @Override
        public void recordDuration(String scope, String scopeType, long durationNanos) {}

        @Override
        public void recordDuration(String scope, String scopeType, Runnable operation) {
            operation.run();
        }

        @Override
        public <T> T recordDuration(String scope, String scopeType, Supplier<T> operation) {
            return operation.get();
        }
    }
}
