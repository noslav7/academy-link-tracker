package backend.academy.linktracker.bot.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class BotMetricsRecorderTest {

    @Test
    void givenCommandAndNotificationRecordsWhenMeasuredThenCountersAreUpdated() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BotMetricsRecorder recorder = new BotMetricsRecorder(registry);

        recorder.incrementCommandRequest("/start");
        recorder.incrementSentNotification();

        double commandCount = registry.find("command_requests_total")
                .tag("command", "/start")
                .counter()
                .count();
        double notificationsCount =
                registry.find("sent_notification_total").counter().count();

        assertAll(() -> assertThat(commandCount).isCloseTo(1.0, within(1.0e-9)), () -> assertThat(notificationsCount)
                .isCloseTo(1.0, within(1.0e-9)));
    }

    @Test
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
}
