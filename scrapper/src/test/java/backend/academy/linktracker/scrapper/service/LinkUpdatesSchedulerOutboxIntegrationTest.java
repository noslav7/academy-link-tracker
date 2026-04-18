package backend.academy.linktracker.scrapper.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("integration")
class LinkUpdatesSchedulerOutboxIntegrationTest extends BaseLinkUpdatesSchedulerIntegrationTest {

    @Test
    void givenUpdatedLinkWhenSchedulerRunsThenOutboxRecordIsCreatedAsPending() throws Exception {
        // Проверяет Transactional Outbox: после детекции обновления формируется PENDING-запись (processed_at = null).
        LinkUpdatesSchedulerTestSupport.stubGithubUpdatedAt("octocat", "outbox-pending", "2026-03-01T00:00:00Z", 10);
        long chatId = 506L;
        registerAndTrack(chatId, "https://github.com/octocat/outbox-pending", List.of(), List.of());

        LinkUpdatesSchedulerTestSupport.stubGithubUpdatedAt("octocat", "outbox-pending", "2026-03-02T00:00:00Z", 1);
        linkUpdatesScheduler.checkUpdates();

        int pendingCount = LinkUpdatesSchedulerTestSupport.pendingOutboxCount(jdbcTemplate);
        String payloadUrl = LinkUpdatesSchedulerTestSupport.latestOutboxPayloadUrl(jdbcTemplate);

        assertAll(
                () -> assertEquals(1, pendingCount),
                () -> assertEquals("https://github.com/octocat/outbox-pending", payloadUrl));
    }

    @Test
    void givenOutboxDeliveryFailureWhenPublisherRunsThenMessageRemainsPending() {
        // Проверяет error-path: при недоступном bot-сервисе сообщение остается в pending.
        long messageId = createPendingOutboxMessage("https://github.com/octocat/outbox-retry-failure", 507L);
        int pendingAfterFailure = LinkUpdatesSchedulerTestSupport.pendingOutboxCountById(jdbcTemplate, messageId);
        int processedAfterFailure = LinkUpdatesSchedulerTestSupport.processedOutboxCountById(jdbcTemplate, messageId);
        assertAll(
                () -> assertTrue(messageId > 0),
                () -> assertEquals(1, pendingAfterFailure),
                () -> assertEquals(0, processedAfterFailure));
    }

    @Test
    void givenPendingOutboxMessageWhenPublisherRetriesSuccessfullyThenMessageIsMarkedProcessed() {
        // Проверяет recovery-path: после успешной повторной доставки pending-запись помечается processed.
        long messageId = createPendingOutboxMessage("https://github.com/octocat/outbox-retry-recovery", 508L);
        LinkUpdatesSchedulerTestSupport.stubBotNotificationsAlwaysOk();
        outboxPublisher.publishPendingNotifications();

        int pendingAfterRecovery = LinkUpdatesSchedulerTestSupport.pendingOutboxCountById(jdbcTemplate, messageId);
        int processedAfterRecovery = LinkUpdatesSchedulerTestSupport.processedOutboxCountById(jdbcTemplate, messageId);
        assertAll(() -> assertEquals(0, pendingAfterRecovery), () -> assertEquals(1, processedAfterRecovery));
    }

    private long createPendingOutboxMessage(String trackedUrl, long chatId) {
        String repositoryName = trackedUrl.substring(trackedUrl.lastIndexOf('/') + 1);
        LinkUpdatesSchedulerTestSupport.stubGithubUpdatedAt("octocat", repositoryName, "2026-03-01T00:00:00Z", 10);
        registerAndTrack(chatId, trackedUrl, List.of(), List.of());
        LinkUpdatesSchedulerTestSupport.stubGithubUpdatedAt("octocat", repositoryName, "2026-03-02T00:00:00Z", 1);
        LinkUpdatesSchedulerTestSupport.stubBotFailureForUrl(trackedUrl);
        linkUpdatesScheduler.checkUpdates();
        outboxPublisher.publishPendingNotifications();
        return LinkUpdatesSchedulerTestSupport.latestOutboxMessageId(jdbcTemplate);
    }
}
