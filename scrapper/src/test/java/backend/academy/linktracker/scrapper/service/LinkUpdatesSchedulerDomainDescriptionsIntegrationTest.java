package backend.academy.linktracker.scrapper.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class LinkUpdatesSchedulerDomainDescriptionsIntegrationTest extends BaseLinkUpdatesSchedulerIntegrationTest {

    @Test
    void givenGithubLinkWhenRepositoryUpdatedThenBotReceivesGithubSpecificDescription() throws Exception {
        // Проверяет интеграционный контракт: обновление GitHub формирует точное доменное описание для bot.
        LinkUpdatesSchedulerTestSupport.stubBotNotificationsAlwaysOk();
        LinkUpdatesSchedulerTestSupport.stubGithubUpdatedAt("octocat", "hello-world", "2026-03-01T00:00:00Z", 10);
        LinkUpdatesSchedulerTestSupport.stubGithubLatestIssueOrPr(
                "octocat",
                "hello-world",
                "Fix scheduler race",
                "dev-user",
                "2026-03-02T00:15:00Z",
                "A".repeat(260),
                true,
                10);

        long chatId = 501L;
        registerAndTrack(chatId, "https://github.com/octocat/hello-world", List.of(), List.of());

        LinkUpdatesSchedulerTestSupport.stubGithubUpdatedAt("octocat", "hello-world", "2026-03-02T00:00:00Z", 1);
        linkUpdatesScheduler.checkUpdates();
        outboxPublisher.publishPendingNotifications();

        List<JsonNode> updates = LinkUpdatesSchedulerTestSupport.capturedBotUpdates(objectMapper);
        assertAll(
                () -> assertEquals(1, updates.size()),
                () -> assertEquals(
                        "https://github.com/octocat/hello-world",
                        updates.getFirst().path("url").asText()),
                () -> assertEquals(
                        """
                        GitHub: новый Issue в octocat/hello-world
                        Название: Fix scheduler race
                        Пользователь: dev-user
                        Время создания: 2026-03-02T00:15:00Z
                        Превью описания: %s
                        """
                                .formatted("A".repeat(200)),
                        updates.getFirst().path("description").asText()));
    }

    @Test
    void givenStackoverflowLinkWhenQuestionUpdatedThenBotReceivesStackoverflowSpecificDescription() throws Exception {
        // Проверяет интеграционный контракт: обновление StackOverflow формирует точное доменное описание для bot.
        LinkUpdatesSchedulerTestSupport.stubBotNotificationsAlwaysOk();
        LinkUpdatesSchedulerTestSupport.stubStackoverflowUpdatedAt(123L, 1_772_323_200L, 10);
        LinkUpdatesSchedulerTestSupport.stubStackoverflowQuestionTitle(123L, "How to build resilient schedulers?", 10);
        LinkUpdatesSchedulerTestSupport.stubStackoverflowLatestAnswer(
                123L, 1_772_409_600L, "alice", "<p>%s</p>".formatted("B".repeat(260)), 10);
        LinkUpdatesSchedulerTestSupport.stubStackoverflowLatestComment(123L, 1_772_300_000L, "bob", "<p>Older comment</p>", 10);

        long chatId = 502L;
        registerAndTrack(chatId, "https://stackoverflow.com/questions/123/example-question", List.of(), List.of());

        LinkUpdatesSchedulerTestSupport.stubStackoverflowUpdatedAt(123L, 1_772_409_600L, 1);
        linkUpdatesScheduler.checkUpdates();
        outboxPublisher.publishPendingNotifications();

        List<JsonNode> updates = LinkUpdatesSchedulerTestSupport.capturedBotUpdates(objectMapper);
        assertAll(
                () -> assertEquals(1, updates.size()),
                () -> assertEquals(
                        "https://stackoverflow.com/questions/123/example-question",
                        updates.getFirst().path("url").asText()),
                () -> assertEquals(
                        """
                        StackOverflow: новый ответ
                        Тема вопроса: How to build resilient schedulers?
                        Пользователь: alice
                        Время создания: 2026-03-02T00:00:00Z
                        Превью: %s
                        """
                                .formatted("B".repeat(200)),
                        updates.getFirst().path("description").asText()));
    }
}
