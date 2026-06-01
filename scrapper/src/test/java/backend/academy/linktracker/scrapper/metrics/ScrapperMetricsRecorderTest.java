package backend.academy.linktracker.scrapper.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit")
class ScrapperMetricsRecorderTest {

    @Test
    // Проверяет инкремент счетчика входящих API-запросов.
    void givenApiRequestIncrementWhenMeasuredThenApiCounterIsUpdated() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ScrapperMetricsRecorder recorder = new ScrapperMetricsRecorder(registry);

        recorder.incrementApiRequest("links");

        double apiCount = registry.find("api_requests_total")
                .tag("source", "links")
                .counter()
                .count();

        assertThat(apiCount).isCloseTo(1.0, within(1.0e-9));
    }

    @Test
    // Проверяет инкремент счетчика ошибочных API-запросов.
    void givenApiRequestErrorIncrementWhenMeasuredThenApiErrorCounterIsUpdated() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ScrapperMetricsRecorder recorder = new ScrapperMetricsRecorder(registry);

        recorder.incrementApiRequestError("links");

        double apiErrorCount = registry.find("api_requests_errors_total")
                .tag("source", "links")
                .counter()
                .count();

        assertThat(apiErrorCount).isCloseTo(1.0, within(1.0e-9));
    }

    @Test
    // Проверяет запись duration-метрики.
    void givenDurationRecordWhenMeasuredThenDurationSummaryCountIsUpdated() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ScrapperMetricsRecorder recorder = new ScrapperMetricsRecorder(registry);

        recorder.recordDuration("external_source", "github", 50_000_000L);

        double durationCount = registry.find("request_duration_ms_total")
                .tag("scope", "external_source")
                .tag("scope_type", "github")
                .summary()
                .count();

        assertThat(durationCount).isCloseTo(1.0, within(1.0e-9));
    }

    @ParameterizedTest
    @MethodSource("trackedLinksGaugeScenarios")
    // Проверяет установку gauge по источнику.
    void givenTrackedLinksCountWhenSetThenGaugeReflectsLastState(String trackedSource, long trackedCount) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ScrapperMetricsRecorder recorder = new ScrapperMetricsRecorder(registry);

        recorder.setTrackedLinksCount(trackedSource, trackedCount);

        double actualGaugeValue = registry.find("links_on_track_total")
                .tag("tracked_source", trackedSource)
                .gauge()
                .value();

        assertThat(actualGaugeValue).isCloseTo((double) trackedCount, within(1.0e-9));
    }

    private static Stream<Arguments> trackedLinksGaugeScenarios() {
        return Stream.of(Arguments.of("github", 7L), Arguments.of("stackoverflow", 3L));
    }

    @Test
    // Проверяет кламп отрицательного значения gauge в ноль.
    void givenNegativeTrackedLinksCountWhenSetThenGaugeValueIsClampedToZero() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ScrapperMetricsRecorder recorder = new ScrapperMetricsRecorder(registry);

        recorder.setTrackedLinksCount("github", -10L);

        double actualGaugeValue = registry.find("links_on_track_total")
                .tag("tracked_source", "github")
                .gauge()
                .value();
        assertThat(actualGaugeValue).isCloseTo(0.0, within(1.0e-9));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {" "})
    // Проверяет игнорирование пустого source для счетчика API-запросов.
    void givenBlankSourceWhenApiRequestIncrementedThenApiCounterIsNotCreated(String source) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ScrapperMetricsRecorder recorder = new ScrapperMetricsRecorder(registry);

        recorder.incrementApiRequest(source);

        assertThat(registry.find("api_requests_total").meters()).isEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {" "})
    // Проверяет игнорирование пустого source для счетчика ошибок API-запросов.
    void givenBlankSourceWhenApiRequestErrorIncrementedThenApiErrorCounterIsNotCreated(String source) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ScrapperMetricsRecorder recorder = new ScrapperMetricsRecorder(registry);

        recorder.incrementApiRequestError(source);

        assertThat(registry.find("api_requests_errors_total").meters()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("invalidDurationTags")
    // Проверяет игнорирование некорректных тегов duration-метрики.
    void givenInvalidDurationScopeWhenRecordedThenDurationSummaryIsNotCreated(String scope, String scopeType) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ScrapperMetricsRecorder recorder = new ScrapperMetricsRecorder(registry);

        recorder.recordDuration(scope, scopeType, 10_000_000L);

        assertThat(registry.find("request_duration_ms_total").meters()).isEmpty();
    }

    private static Stream<Arguments> invalidDurationTags() {
        return Stream.of(Arguments.of("", "github"), Arguments.of("external_source", ""));
    }
}
