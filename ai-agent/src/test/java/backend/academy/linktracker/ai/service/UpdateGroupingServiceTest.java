package backend.academy.linktracker.ai.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.academy.linktracker.ai.dto.ProcessedLinkUpdate;
import backend.academy.linktracker.ai.dto.UpdatePriority;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class UpdateGroupingServiceTest {

    @Test
    void givenSeveralUpdatesForSameChatInWindowWhenFlushExpiredThenPublishesSingleGroupedMessage() {
        // Цель: проверить базовый сценарий оконной группировки двух событий в одно сообщение.
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
        UpdateGroupingService groupingService = new UpdateGroupingService(30_000, clock);

        assertTrue(groupingService
                .accept("k1", processed(101L, "Critical security update", UpdatePriority.HIGH))
                .isEmpty());
        clock.plusMillis(5_000);
        assertTrue(groupingService
                .accept("k2", processed(102L, "Minor docs cleanup", UpdatePriority.LOW))
                .isEmpty());

        clock.plusMillis(30_000);
        List<GroupedMessage> expired = groupingService.flushExpired();

        ProcessedLinkUpdate grouped = expired.getFirst().processedUpdate();
        assertAll(
                () -> assertEquals(1, expired.size()),
                () -> assertEquals("k1", expired.getFirst().messageKey()),
                () -> assertEquals(101L, grouped.id()),
                () -> assertEquals(List.of(111L), grouped.tgChatIds()),
                () -> assertEquals(UpdatePriority.HIGH, grouped.priority()),
                () -> assertEquals(
                        "1. Critical security update" + System.lineSeparator() + "2. Minor docs cleanup",
                        grouped.description()));
    }

    @Test
    void givenSingleUpdateForChatInWindowWhenFlushExpiredThenPublishesMessageWithoutGrouping() {
        // Цель: проверить passthrough для окна с единственным событием.
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
        UpdateGroupingService groupingService = new UpdateGroupingService(30_000, clock);

        assertTrue(groupingService
                .accept("single-key", processed(777L, "Regular update", UpdatePriority.MEDIUM))
                .isEmpty());
        clock.plusMillis(30_000);

        List<GroupedMessage> expired = groupingService.flushExpired();

        ProcessedLinkUpdate passthrough = expired.getFirst().processedUpdate();
        assertAll(
                () -> assertEquals(1, expired.size()),
                () -> assertEquals("single-key", expired.getFirst().messageKey()),
                () -> assertEquals(777L, passthrough.id()),
                () -> assertEquals("Regular update", passthrough.description()),
                () -> assertEquals(List.of(111L), passthrough.tgChatIds()),
                () -> assertEquals(UpdatePriority.MEDIUM, passthrough.priority()));
    }

    @Test
    void givenWindowNotExpiredWhenFlushExpiredThenReturnsNoMessages() {
        // Цель: проверить границу окна: до истечения flush ничего не публикует.
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
        UpdateGroupingService groupingService = new UpdateGroupingService(30_000, clock);

        assertTrue(groupingService
                .accept("k1", processed(321L, "Update still inside window", UpdatePriority.MEDIUM))
                .isEmpty());
        clock.plusMillis(29_999);

        List<GroupedMessage> expired = groupingService.flushExpired();

        assertTrue(expired.isEmpty());
    }

    @Test
    void givenNullProcessedUpdateWhenAcceptThenThrowsNullPointerException() {
        // Цель: проверить ошибку контракта при null-входе в accept.
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
        UpdateGroupingService groupingService = new UpdateGroupingService(30_000, clock);

        NullPointerException exception =
                assertThrows(NullPointerException.class, () -> groupingService.accept("k1", null));

        assertEquals("processedUpdate", exception.getMessage());
    }

    private static ProcessedLinkUpdate processed(long id, String description, UpdatePriority priority) {
        return new ProcessedLinkUpdate(id, description, List.of(111L), priority);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void plusMillis(long millis) {
            instant = instant.plusMillis(millis);
        }
    }
}
