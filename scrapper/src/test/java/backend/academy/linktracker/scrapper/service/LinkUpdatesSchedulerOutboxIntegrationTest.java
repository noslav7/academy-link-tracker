package backend.academy.linktracker.scrapper.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class LinkUpdatesSchedulerOutboxIntegrationTest extends BaseLinkUpdatesSchedulerIntegrationTest {

    @Test
    void givenUpdatedLinkWhenSchedulerRunsThenOutboxRecordIsCreatedAsPending() throws Exception {
        // Проверяет Transactional Outbox: после детекции обновления формируется PENDING-запись (processed_at = null).
        LinkUpdatesSchedulerTestSupport.stubGithubUpdatedAt("octocat", "outbox-pending", "2026-03-01T00:00:00Z", 10);
        long chatId = 506L;
        registerAndTrack(chatId, "https://github.com/octocat/outbox-pending", List.of(), List.of());

        LinkUpdatesSchedulerTestSupport.stubGithubUpdatedAt("octocat", "outbox-pending", "2026-03-02T00:00:00Z", 1);
        linkUpdatesScheduler.checkUpdates();

        Integer pendingCount = LinkUpdatesSchedulerTestSupport.pendingOutboxCount(jdbcTemplate);
        String payloadUrl = LinkUpdatesSchedulerTestSupport.latestOutboxPayloadUrl(jdbcTemplate);

        assertAll(
                () -> assertNotNull(pendingCount),
                () -> assertEquals(1, pendingCount),
                () -> assertEquals("https://github.com/octocat/outbox-pending", payloadUrl));
    }

    @Test
    void givenOutboxDeliveryFailureWhenPublisherRunsThenMessageRemainsPendingUntilSuccessfulRetry() {
        // Проверяет, что processed_at проставляется только после успешной доставки; при ошибке запись остается pending.
        LinkUpdatesSchedulerTestSupport.stubGithubUpdatedAt("octocat", "outbox-retry", "2026-03-01T00:00:00Z", 10);
        long chatId = 507L;
        registerAndTrack(chatId, "https://github.com/octocat/outbox-retry", List.of(), List.of());
        LinkUpdatesSchedulerTestSupport.stubGithubUpdatedAt("octocat", "outbox-retry", "2026-03-02T00:00:00Z", 1);

        LinkUpdatesSchedulerTestSupport.stubBotFailureForUrl("https://github.com/octocat/outbox-retry");
        linkUpdatesScheduler.checkUpdates();
        outboxPublisher.publishPendingNotifications();

        Long messageId = LinkUpdatesSchedulerTestSupport.latestOutboxMessageId(jdbcTemplate);
        Integer pendingAfterFailure = LinkUpdatesSchedulerTestSupport.pendingOutboxCountById(jdbcTemplate, messageId);
        assertAll(
                () -> assertNotNull(messageId),
                () -> assertNotNull(pendingAfterFailure),
                () -> assertEquals(1, pendingAfterFailure));

        LinkUpdatesSchedulerTestSupport.stubBotNotificationsAlwaysOk();
        outboxPublisher.publishPendingNotifications();

        Integer processedAfterRecovery =
                LinkUpdatesSchedulerTestSupport.processedOutboxCountById(jdbcTemplate, messageId);
        assertAll(() -> assertNotNull(processedAfterRecovery), () -> assertEquals(1, processedAfterRecovery));
    }
}
