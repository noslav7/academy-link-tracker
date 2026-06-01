package backend.academy.linktracker.scrapper.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.dto.AddLinkRequest;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("integration")
class LinkUpdatesSchedulerBatchAndPersistenceIntegrationTest extends BaseLinkUpdatesSchedulerIntegrationTest {

    @Test
    void givenBatchOfLinksWhenSchedulerRunsThenAllLinksAreProcessedAndNotified() throws Exception {
        // Проверяет, что пакетная обработка scheduler отправляет уведомления по всем обновленным ссылкам.
        LinkUpdatesSchedulerTestSupport.stubBotNotificationsAlwaysOk();
        long chatId = 503L;
        chatService.register(chatId);

        List<String> links = List.of(
                "https://github.com/octocat/repo-a",
                "https://github.com/octocat/repo-b",
                "https://github.com/octocat/repo-c",
                "https://github.com/octocat/repo-d",
                "https://github.com/octocat/repo-e");

        for (String link : links) {
            String repository = link.substring(link.lastIndexOf('/') + 1);
            LinkUpdatesSchedulerTestSupport.stubGithubUpdatedAt("octocat", repository, "2026-03-01T00:00:00Z", 10);
            linkService.add(chatId, new AddLinkRequest(link, List.of(), List.of()));
        }

        for (String link : links) {
            String repository = link.substring(link.lastIndexOf('/') + 1);
            LinkUpdatesSchedulerTestSupport.stubGithubUpdatedAt("octocat", repository, "2026-03-02T00:00:00Z", 1);
        }

        linkUpdatesScheduler.checkUpdates();
        outboxPublisher.publishPendingNotifications();

        Set<String> actualUrls = LinkUpdatesSchedulerTestSupport.capturedBotUpdates(objectMapper).stream()
                .map(update -> update.path("url").asText())
                .collect(java.util.stream.Collectors.toSet());

        assertAll(() -> assertEquals(5, actualUrls.size()), () -> assertEquals(Set.copyOf(links), actualUrls));
    }

    @Test
    void givenPartiallyFailingBatchWhenSchedulerRunsThenFailureIsIsolatedWithoutSystemErrorReport() throws Exception {
        // Проверяет изоляцию ошибки доставки: уведомления по ссылкам отправляются без system error report.
        LinkUpdatesSchedulerTestSupport.stubBotFailureForUrl("https://github.com/octocat/bad-repo");

        long chatId = 504L;
        chatService.register(chatId);
        linkService.add(chatId, new AddLinkRequest("https://github.com/octocat/good-repo", List.of(), List.of()));
        linkService.add(chatId, new AddLinkRequest("https://github.com/octocat/bad-repo", List.of(), List.of()));

        LinkUpdatesSchedulerTestSupport.stubGithubUpdatedAt("octocat", "good-repo", "2026-03-01T00:00:00Z", 10);
        LinkUpdatesSchedulerTestSupport.stubGithubUpdatedAt("octocat", "bad-repo", "2026-03-01T00:00:00Z", 10);

        // Ensure both links are considered updated during scheduler run.
        TrackedLink good = maintenanceService.fetchBatch(0).stream()
                .filter(link -> link.url().endsWith("/good-repo"))
                .findFirst()
                .orElseThrow();
        TrackedLink bad = maintenanceService.fetchBatch(0).stream()
                .filter(link -> link.url().endsWith("/bad-repo"))
                .findFirst()
                .orElseThrow();
        maintenanceService.updateTrackedLink(good.withLastCheck(good.lastCheckedAt(), Instant.EPOCH.minusSeconds(1)));
        maintenanceService.updateTrackedLink(bad.withLastCheck(bad.lastCheckedAt(), Instant.EPOCH.minusSeconds(1)));

        LinkUpdatesSchedulerTestSupport.stubGithubUpdatedAt("octocat", "good-repo", "2026-03-02T00:00:00Z", 1);
        LinkUpdatesSchedulerTestSupport.stubGithubUpdatedAt("octocat", "bad-repo", "2026-03-02T00:00:00Z", 1);

        linkUpdatesScheduler.checkUpdates();
        outboxPublisher.publishPendingNotifications();

        List<String> actualUrls = LinkUpdatesSchedulerTestSupport.capturedBotUpdates(objectMapper).stream()
                .map(update -> update.path("url").asText())
                .sorted()
                .toList();
        List<String> expectedUrls =
                List.of("https://github.com/octocat/bad-repo", "https://github.com/octocat/good-repo");
        assertAll(
                () -> assertEquals(expectedUrls, actualUrls),
                () -> assertEquals(
                        0,
                        actualUrls.stream()
                                .filter(url -> url.startsWith("system://scheduler-error-report"))
                                .count()));
    }

    @Test
    void givenSubscribeWithTagsAndFiltersWhenSavingThenAllEntitiesArePersisted() {
        // Проверяет сохранение связанной модели подписки: чат, ссылка, теги и фильтры.
        long chatId = 505L;
        String link = "https://github.com/octocat/entities-repo";
        LinkUpdatesSchedulerTestSupport.stubGithubUpdatedAt("octocat", "entities-repo", "2026-03-01T00:00:00Z", 1);

        chatService.register(chatId);
        linkService.add(chatId, new AddLinkRequest(link, List.of("work", "urgent"), List.of("author=me", "label=bug")));

        int chatsCount = queryRequiredCount("select count(*) from chats where chat_id = ?", chatId);
        int linksCount =
                queryRequiredCount("select count(*) from tracked_links where chat_id = ? and url = ?", chatId, link);
        int tagsCount = queryRequiredCount("""
                select count(*)
                from tracked_link_tags tlt
                join tracked_links tl on tl.id = tlt.tracked_link_id
                where tl.chat_id = ? and tl.url = ?
                """, chatId, link);
        int filtersCount = queryRequiredCount("""
                select count(*)
                from tracked_link_filters tlf
                join tracked_links tl on tl.id = tlf.tracked_link_id
                where tl.chat_id = ? and tl.url = ?
                """, chatId, link);

        assertAll(
                () -> assertEquals(1, chatsCount),
                () -> assertEquals(1, linksCount),
                () -> assertEquals(2, tagsCount),
                () -> assertEquals(2, filtersCount));
    }

    private int queryRequiredCount(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        if (value == null) {
            throw new IllegalStateException("Expected non-null count for query: " + sql);
        }
        return value;
    }
}
