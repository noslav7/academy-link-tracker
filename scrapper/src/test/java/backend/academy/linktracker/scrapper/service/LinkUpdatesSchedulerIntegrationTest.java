package backend.academy.linktracker.scrapper.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import backend.academy.linktracker.scrapper.ScrapperApplication;
import backend.academy.linktracker.scrapper.TestcontainersConfiguration;
import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.dto.AddLinkRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.wiremock.spring.EnableWireMock;

@SpringBootTest(
        classes = {ScrapperApplication.class, TestcontainersConfiguration.class},
        properties = {
            "app.github.base-url=${wiremock.server.baseUrl}/github",
            "app.stackoverflow.base-url=${wiremock.server.baseUrl}/so",
            "app.bot.base-url=${wiremock.server.baseUrl}/bot",
            "app.scheduler.interval=365d",
            "app.scheduler.batch-size=2",
            "app.scheduler.thread-count=2"
        })
@EnableWireMock
@Tag("integration")
class LinkUpdatesSchedulerIntegrationTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private LinkService linkService;

    @Autowired
    private LinkUpdatesScheduler linkUpdatesScheduler;

    @Autowired
    private TrackedLinkMaintenanceService maintenanceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from chats");
        WireMock.reset();
    }

    @Test
    void givenGithubLinkWhenRepositoryUpdatedThenBotReceivesGithubSpecificDescription() throws Exception {
        stubBotNotificationsAlwaysOk();
        stubGithubUpdatedAt("octocat", "hello-world", "2026-03-01T00:00:00Z", 10);
        stubGithubLatestIssueOrPr(
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

        stubGithubUpdatedAt("octocat", "hello-world", "2026-03-02T00:00:00Z", 1);
        linkUpdatesScheduler.checkUpdates();

        List<JsonNode> updates = capturedBotUpdates();
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
                        """.formatted("A".repeat(200)),
                        updates.getFirst().path("description").asText()));
    }

    @Test
    void givenStackoverflowLinkWhenQuestionUpdatedThenBotReceivesStackoverflowSpecificDescription() throws Exception {
        stubBotNotificationsAlwaysOk();
        stubStackoverflowUpdatedAt(123L, 1_772_323_200L, 10);
        stubStackoverflowQuestionTitle(123L, "How to build resilient schedulers?", 10);
        stubStackoverflowLatestAnswer(
                123L,
                1_772_409_600L,
                "alice",
                "<p>%s</p>".formatted("B".repeat(260)),
                10);
        stubStackoverflowLatestComment(
                123L,
                1_772_300_000L,
                "bob",
                "<p>Older comment</p>",
                10);

        long chatId = 502L;
        registerAndTrack(chatId, "https://stackoverflow.com/questions/123/example-question", List.of(), List.of());

        stubStackoverflowUpdatedAt(123L, 1_772_409_600L, 1);
        linkUpdatesScheduler.checkUpdates();

        List<JsonNode> updates = capturedBotUpdates();
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
                        """.formatted("B".repeat(200)),
                        updates.getFirst().path("description").asText()));
    }

    @Test
    void givenBatchOfLinksWhenSchedulerRunsThenAllLinksAreProcessedAndNotified() throws Exception {
        stubBotNotificationsAlwaysOk();
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
            stubGithubUpdatedAt("octocat", repository, "2026-03-01T00:00:00Z", 10);
            linkService.add(chatId, new AddLinkRequest(link, List.of(), List.of()));
        }

        for (String link : links) {
            String repository = link.substring(link.lastIndexOf('/') + 1);
            stubGithubUpdatedAt("octocat", repository, "2026-03-02T00:00:00Z", 1);
        }

        linkUpdatesScheduler.checkUpdates();

        Set<String> actualUrls = capturedBotUpdates().stream()
                .map(update -> update.path("url").asText())
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(5, actualUrls.size());
        assertEquals(Set.copyOf(links), actualUrls);
    }

    @Test
    void givenPartiallyFailingBatchWhenSchedulerRunsThenFailureIsIsolatedAndErrorReportIsSent() throws Exception {
        stubFor(post(urlEqualTo("/bot/updates"))
                .withRequestBody(matchingJsonPath("$.url", equalTo("https://github.com/octocat/bad-repo")))
                .willReturn(aResponse().withStatus(503).withBody("bot unavailable")));
        stubFor(post(urlEqualTo("/bot/updates"))
                .atPriority(10)
                .willReturn(aResponse().withStatus(200)));

        long chatId = 504L;
        chatService.register(chatId);
        linkService.add(chatId, new AddLinkRequest("https://github.com/octocat/good-repo", List.of(), List.of()));
        linkService.add(chatId, new AddLinkRequest("https://github.com/octocat/bad-repo", List.of(), List.of()));

        stubGithubUpdatedAt("octocat", "good-repo", "2026-03-01T00:00:00Z", 10);
        stubGithubUpdatedAt("octocat", "bad-repo", "2026-03-01T00:00:00Z", 10);

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

        stubGithubUpdatedAt("octocat", "good-repo", "2026-03-02T00:00:00Z", 1);
        stubGithubUpdatedAt("octocat", "bad-repo", "2026-03-02T00:00:00Z", 1);

        linkUpdatesScheduler.checkUpdates();

        List<String> actualUrls = capturedBotUpdates().stream()
                .map(update -> update.path("url").asText())
                .sorted()
                .toList();
        assertEquals(
                List.of(
                        "https://github.com/octocat/bad-repo",
                        "https://github.com/octocat/bad-repo",
                        "https://github.com/octocat/good-repo",
                        "system://scheduler-error-report"),
                actualUrls);
    }

    @Test
    void givenSubscribeWithTagsAndFiltersWhenSavingThenAllEntitiesArePersisted() {
        long chatId = 505L;
        String link = "https://github.com/octocat/entities-repo";
        stubGithubUpdatedAt("octocat", "entities-repo", "2026-03-01T00:00:00Z", 1);

        chatService.register(chatId);
        linkService.add(chatId, new AddLinkRequest(link, List.of("work", "urgent"), List.of("author=me", "label=bug")));

        int chatsCount = Objects.requireNonNull(
                jdbcTemplate.queryForObject("select count(*) from chats where chat_id = ?", Integer.class, chatId));
        int linksCount = Objects.requireNonNull(jdbcTemplate.queryForObject(
                "select count(*) from tracked_links where chat_id = ? and url = ?", Integer.class, chatId, link));
        int tagsCount = Objects.requireNonNull(jdbcTemplate.queryForObject("""
                select count(*)
                from tracked_link_tags tlt
                join tracked_links tl on tl.id = tlt.tracked_link_id
                where tl.chat_id = ? and tl.url = ?
                """, Integer.class, chatId, link));
        int filtersCount = Objects.requireNonNull(jdbcTemplate.queryForObject("""
                select count(*)
                from tracked_link_filters tlf
                join tracked_links tl on tl.id = tlf.tracked_link_id
                where tl.chat_id = ? and tl.url = ?
                """, Integer.class, chatId, link));

        assertAll(
                () -> assertEquals(1, chatsCount),
                () -> assertEquals(1, linksCount),
                () -> assertEquals(2, tagsCount),
                () -> assertEquals(2, filtersCount));
    }

    private void registerAndTrack(long chatId, String url, List<String> tags, List<String> filters) {
        chatService.register(chatId);
        linkService.add(chatId, new AddLinkRequest(url, tags, filters));
    }

    private void stubBotNotificationsAlwaysOk() {
        stubFor(post(urlEqualTo("/bot/updates")).willReturn(aResponse().withStatus(200)));
    }

    private void stubGithubUpdatedAt(String owner, String repository, String updatedAtIso, int priority) {
        stubFor(get(urlEqualTo("/github/repos/%s/%s".formatted(owner, repository)))
                .atPriority(priority)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"updated_at\":\"%s\"}".formatted(updatedAtIso))));
    }

    private void stubGithubLatestIssueOrPr(
            String owner,
            String repository,
            String title,
            String author,
            String createdAtIso,
            String body,
            boolean issue,
            int priority) {
        String pullRequest = issue ? "null" : "{}";
        stubFor(get(urlEqualTo("/github/repos/%s/%s/issues?state=all&sort=created&direction=desc&per_page=1"
                        .formatted(owner, repository)))
                .atPriority(priority)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {
                                    "title": "%s",
                                    "body": "%s",
                                    "user": {"login": "%s"},
                                    "created_at": "%s",
                                    "pull_request": %s
                                  }
                                ]
                                """
                                .formatted(title, body, author, createdAtIso, pullRequest))));
    }

    private void stubStackoverflowUpdatedAt(long questionId, long lastActivityEpochSeconds, int priority) {
        stubFor(get(urlEqualTo("/so/questions/%d?site=stackoverflow".formatted(questionId)))
                .atPriority(priority)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"items\":[{\"last_activity_date\":%d}]}".formatted(lastActivityEpochSeconds))));
    }

    private void stubStackoverflowQuestionTitle(long questionId, String title, int priority) {
        stubFor(get(urlEqualTo("/so/questions/%d?site=stackoverflow&filter=withbody".formatted(questionId)))
                .atPriority(priority)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"items":[{"title":"%s"}]}
                                """.formatted(title))));
    }

    private void stubStackoverflowLatestAnswer(
            long questionId, long creationDate, String author, String body, int priority) {
        stubFor(get(urlEqualTo(
                        "/so/questions/%d/answers?site=stackoverflow&order=desc&sort=creation&filter=withbody&pagesize=1"
                                .formatted(questionId)))
                .atPriority(priority)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"items":[{"creation_date":%d,"body":"%s","owner":{"display_name":"%s"}}]}
                                """
                                .formatted(creationDate, body, author))));
    }

    private void stubStackoverflowLatestComment(
            long questionId, long creationDate, String author, String body, int priority) {
        stubFor(get(urlEqualTo(
                        "/so/questions/%d/comments?site=stackoverflow&order=desc&sort=creation&filter=withbody&pagesize=1"
                                .formatted(questionId)))
                .atPriority(priority)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"items":[{"creation_date":%d,"body":"%s","owner":{"display_name":"%s"}}]}
                                """
                                .formatted(creationDate, body, author))));
    }

    private List<JsonNode> capturedBotUpdates() {
        return findAll(postRequestedFor(urlEqualTo("/bot/updates"))).stream()
                .map(LoggedRequest::getBodyAsString)
                .map(this::toJson)
                .toList();
    }

    private JsonNode toJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to parse bot update payload: " + value, ex);
        }
    }
}
