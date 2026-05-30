package backend.academy.linktracker.scrapper.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Recorder пользовательских метрик scrapper-сервиса.
 */
@Component
public class ScrapperMetricsRecorder {
    private static final String METRIC_LINKS_ON_TRACK = "links_on_track_total";
    private static final String METRIC_REQUEST_DURATION = "request_duration_ms_total";
    private static final String METRIC_API_REQUESTS = "api_requests_total";
    private static final String METRIC_API_REQUEST_ERRORS = "api_requests_errors_total";
    private static final long[] REQUEST_DURATION_BUCKETS_MS = {5, 10, 25, 50, 100, 250, 500, 1_000, 2_000, 5_000};
    private static final String TRACKED_SOURCE_GITHUB = "github";
    private static final String TRACKED_SOURCE_STACKOVERFLOW = "stackoverflow";

    private final MeterRegistry meterRegistry;
    private final Map<String, AtomicLong> linksOnTrackBySource = new ConcurrentHashMap<>();
    private final Map<String, DistributionSummary> requestDurationSummaries = new ConcurrentHashMap<>();
    private final Map<String, Counter> apiRequestCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> apiRequestErrorCounters = new ConcurrentHashMap<>();

    public ScrapperMetricsRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        linksOnTrackBySource.put(TRACKED_SOURCE_GITHUB, registerLinksGauge(TRACKED_SOURCE_GITHUB));
        linksOnTrackBySource.put(TRACKED_SOURCE_STACKOVERFLOW, registerLinksGauge(TRACKED_SOURCE_STACKOVERFLOW));
    }

    public static ScrapperMetricsRecorder noop() {
        return new NoopScrapperMetricsRecorder();
    }

    public void setTrackedLinksCount(String trackedSource, long count) {
        if (trackedSource == null || trackedSource.isBlank()) {
            return;
        }
        AtomicLong value = linksOnTrackBySource.computeIfAbsent(trackedSource, this::registerLinksGauge);
        value.set(Math.max(0L, count));
    }

    public void incrementApiRequest(String source) {
        if (source == null || source.isBlank()) {
            return;
        }
        apiRequestCounters.computeIfAbsent(source, this::buildApiRequestCounter).increment();
    }

    public void incrementApiRequestError(String source) {
        if (source == null || source.isBlank()) {
            return;
        }
        apiRequestErrorCounters
                .computeIfAbsent(source, this::buildApiRequestErrorCounter)
                .increment();
    }

    public void recordDuration(String scope, String scopeType, long durationNanos) {
        if (scope == null || scope.isBlank() || scopeType == null || scopeType.isBlank()) {
            return;
        }
        double durationMs = TimeUnit.NANOSECONDS.toMillis(Math.max(0L, durationNanos));
        requestDurationSummaries
                .computeIfAbsent(scope + ":" + scopeType, ignored -> buildRequestDurationSummary(scope, scopeType))
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

    private AtomicLong registerLinksGauge(String trackedSource) {
        AtomicLong gaugeValue = new AtomicLong(0L);
        meterRegistry.gauge(METRIC_LINKS_ON_TRACK, Tags.of("tracked_source", trackedSource), gaugeValue);
        return gaugeValue;
    }

    private Counter buildApiRequestCounter(String source) {
        return Counter.builder(METRIC_API_REQUESTS)
                .description("Total number of incoming API requests")
                .tags("source", source)
                .register(meterRegistry);
    }

    private Counter buildApiRequestErrorCounter(String source) {
        return Counter.builder(METRIC_API_REQUEST_ERRORS)
                .description("Total number of failed incoming API requests")
                .tags("source", source)
                .register(meterRegistry);
    }

    private DistributionSummary buildRequestDurationSummary(String scope, String scopeType) {
        double[] buckets = new double[REQUEST_DURATION_BUCKETS_MS.length];
        for (int i = 0; i < REQUEST_DURATION_BUCKETS_MS.length; i++) {
            buckets[i] = REQUEST_DURATION_BUCKETS_MS[i];
        }
        return DistributionSummary.builder(METRIC_REQUEST_DURATION)
                .description("Single operation duration in milliseconds")
                .baseUnit("milliseconds")
                .serviceLevelObjectives(buckets)
                .tags("scope", scope, "scope_type", scopeType)
                .register(meterRegistry);
    }

    private static final class NoopScrapperMetricsRecorder extends ScrapperMetricsRecorder {
        private NoopScrapperMetricsRecorder() {
            super(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        }

        @Override
        public void setTrackedLinksCount(String trackedSource, long count) {}

        @Override
        public void incrementApiRequest(String source) {}

        @Override
        public void incrementApiRequestError(String source) {}

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
