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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

final class LinkUpdatesSchedulerTestSupport {

    private LinkUpdatesSchedulerTestSupport() {}

    static void resetState(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("delete from notification_outbox");
        jdbcTemplate.update("delete from chats");
        WireMock.reset();
    }

    static void stubBotNotificationsAlwaysOk() {
        stubFor(post(urlEqualTo("/bot/updates")).willReturn(aResponse().withStatus(200)));
    }

    static void stubBotFailureForUrl(String url) {
        stubFor(post(urlEqualTo("/bot/updates"))
                .withRequestBody(matchingJsonPath("$.url", equalTo(url)))
                .willReturn(aResponse().withStatus(503).withBody("bot unavailable")));
        stubFor(post(urlEqualTo("/bot/updates"))
                .atPriority(10)
                .willReturn(aResponse().withStatus(200)));
    }

    static void stubGithubUpdatedAt(String owner, String repository, String updatedAtIso, int priority) {
        stubFor(get(urlEqualTo("/github/repos/%s/%s".formatted(owner, repository)))
                .atPriority(priority)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"updated_at\":\"%s\"}".formatted(updatedAtIso))));
    }

    static void stubGithubLatestIssueOrPr(
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
                                """.formatted(title, body, author, createdAtIso, pullRequest))));
    }

    static void stubStackoverflowUpdatedAt(long questionId, long lastActivityEpochSeconds, int priority) {
        stubFor(get(urlEqualTo("/so/questions/%d?site=stackoverflow".formatted(questionId)))
                .atPriority(priority)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"items\":[{\"last_activity_date\":%d}]}".formatted(lastActivityEpochSeconds))));
    }

    static void stubStackoverflowQuestionTitle(long questionId, String title, int priority) {
        stubFor(get(urlEqualTo("/so/questions/%d?site=stackoverflow&filter=withbody".formatted(questionId)))
                .atPriority(priority)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"items":[{"title":"%s"}]}
                                """.formatted(title))));
    }

    static void stubStackoverflowLatestAnswer(
            long questionId, long creationDate, String author, String body, int priority) {
        stubFor(get(urlEqualTo(
                        "/so/questions/%d/answers?site=stackoverflow&order=desc&sort=creation&filter=withbody&pagesize=1"
                                .formatted(questionId)))
                .atPriority(priority)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"items":[{"creation_date":%d,"body":"%s","owner":{"display_name":"%s"}}]}
                                """.formatted(creationDate, body, author))));
    }

    static void stubStackoverflowLatestComment(
            long questionId, long creationDate, String author, String body, int priority) {
        stubFor(get(urlEqualTo(
                        "/so/questions/%d/comments?site=stackoverflow&order=desc&sort=creation&filter=withbody&pagesize=1"
                                .formatted(questionId)))
                .atPriority(priority)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"items":[{"creation_date":%d,"body":"%s","owner":{"display_name":"%s"}}]}
                                """.formatted(creationDate, body, author))));
    }

    static List<JsonNode> capturedBotUpdates(ObjectMapper objectMapper) {
        return findAll(postRequestedFor(urlEqualTo("/bot/updates"))).stream()
                .map(LoggedRequest::getBodyAsString)
                .map(value -> toJson(objectMapper, value))
                .toList();
    }

    static int pendingOutboxCount(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from notification_outbox
                where processed_at is null
                """, Integer.class);
    }

    static String latestOutboxPayloadUrl(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.queryForObject("""
                select payload->>'url'
                from notification_outbox
                order by id desc
                limit 1
                """, String.class);
    }

    static long latestOutboxMessageId(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.queryForObject("""
                select id
                from notification_outbox
                order by id desc
                limit 1
                """, Long.class);
    }

    static int pendingOutboxCountById(JdbcTemplate jdbcTemplate, long messageId) {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from notification_outbox
                where id = ? and processed_at is null
                """, Integer.class, messageId);
    }

    static int processedOutboxCountById(JdbcTemplate jdbcTemplate, long messageId) {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from notification_outbox
                where id = ? and processed_at is not null
                """, Integer.class, messageId);
    }

    private static JsonNode toJson(ObjectMapper objectMapper, String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to parse bot update payload: " + value, ex);
        }
    }
}
