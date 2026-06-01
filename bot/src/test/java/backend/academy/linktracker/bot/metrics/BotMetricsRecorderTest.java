package backend.academy.linktracker.bot.metrics;

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
class BotMetricsRecorderTest {

    @Test
    // Проверяет инкремент счетчика команд.
    void givenCommandRequestRecordedWhenMeasuredThenCounterIsUpdated() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BotMetricsRecorder recorder = new BotMetricsRecorder(registry);

        recorder.incrementCommandRequest("/start");

        double commandCount = registry.find("command_requests_total")
                .tag("command", "/start")
                .counter()
                .count();

        assertThat(commandCount).isCloseTo(1.0, within(1.0e-9));
    }

    @Test
    // Проверяет инкремент счетчика отправленных уведомлений.
    void givenSentNotificationRecordedWhenMeasuredThenCounterIsUpdated() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BotMetricsRecorder recorder = new BotMetricsRecorder(registry);

        recorder.incrementSentNotification();

        double notificationsCount =
                registry.find("sent_notification_total").counter().count();
        assertThat(notificationsCount).isCloseTo(1.0, within(1.0e-9));
    }

    @Test
    // Проверяет факт записи длительности в summary.
    void givenCommandDurationRecordWhenMeasuredThenHistogramCountIncrements() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BotMetricsRecorder recorder = new BotMetricsRecorder(registry);

        recorder.recordDuration("scrapper_sync_api", "getLinks", 15_000_000L);

        double durationCount = registry.find("command_duration_ms_total")
                .tag("scope", "scrapper_sync_api")
                .tag("scope_type", "getLinks")
                .summary()
                .count();

        assertThat(durationCount).isCloseTo(1.0, within(1.0e-9));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {" "})
    // Проверяет игнорирование пустой команды.
    void givenBlankOrNullCommandWhenIncrementedThenCounterIsNotCreated(String command) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BotMetricsRecorder recorder = new BotMetricsRecorder(registry);

        recorder.incrementCommandRequest(command);

        assertThat(registry.find("command_requests_total").meters()).isEmpty();
    }

    @Test
    // Проверяет кламп отрицательной длительности в ноль.
    void givenNegativeDurationWhenRecordedThenDurationAmountIsClampedToZero() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BotMetricsRecorder recorder = new BotMetricsRecorder(registry);

        recorder.recordDuration("scrapper_sync_api", "getLinks", -1L);

        var summary = registry.find("command_duration_ms_total")
                .tag("scope", "scrapper_sync_api")
                .tag("scope_type", "getLinks")
                .summary();
        assertThat(summary.totalAmount()).isEqualTo(0L);
    }

    @ParameterizedTest
    @MethodSource("invalidDurationTags")
    // Проверяет игнорирование некорректных тегов duration-метрики.
    void givenBlankScopeOrScopeTypeWhenDurationRecordedThenSummaryIsNotCreated(String scope, String scopeType) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BotMetricsRecorder recorder = new BotMetricsRecorder(registry);

        recorder.recordDuration(scope, scopeType, 1_000_000L);

        assertThat(registry.find("command_duration_ms_total").meters()).isEmpty();
    }

    private static Stream<Arguments> invalidDurationTags() {
        return Stream.of(Arguments.of("", "getLinks"), Arguments.of("scrapper_sync_api", " "));
    }
}
