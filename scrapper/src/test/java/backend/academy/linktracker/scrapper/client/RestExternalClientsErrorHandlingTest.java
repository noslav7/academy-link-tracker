package backend.academy.linktracker.scrapper.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import backend.academy.linktracker.scrapper.client.GithubClient.GithubIssueOrPullRequestUpdate;
import backend.academy.linktracker.scrapper.client.StackoverflowClient.StackoverflowAnswerOrCommentUpdate;
import backend.academy.linktracker.scrapper.properties.GithubProperties;
import backend.academy.linktracker.scrapper.properties.StackoverflowProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class RestExternalClientsErrorHandlingTest {

    private WireMockServer wireMockServer;
    private RestGithubClient githubClient;
    private RestStackoverflowClient stackoverflowClient;

    @BeforeEach
    void setUpClients() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();

        GithubProperties githubProperties = new GithubProperties();
        githubProperties.setBaseUrl("http://localhost:%d/github".formatted(wireMockServer.port()));
        githubClient = new RestGithubClient(
                RestClient.builder().baseUrl(githubProperties.getBaseUrl()).build(), githubProperties);

        StackoverflowProperties stackoverflowProperties = new StackoverflowProperties();
        stackoverflowProperties.setBaseUrl("http://localhost:%d/so".formatted(wireMockServer.port()));
        stackoverflowClient = new RestStackoverflowClient(
                RestClient.builder()
                        .baseUrl(stackoverflowProperties.getBaseUrl())
                        .build(),
                stackoverflowProperties);
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void givenGithubClientWhenExternalApiReturnsHttpErrorThenReturnsEpoch() {
        // Проверяет fallback в Instant.EPOCH при HTTP-ошибке GitHub API.
        wireMockServer.stubFor(get(urlEqualTo("/github/repos/user/repo"))
                .willReturn(aResponse().withStatus(503).withBody("upstream unavailable")));

        Instant updatedAt = githubClient.fetchRepositoryUpdatedAt("user", "repo");

        assertEquals(Instant.EPOCH, updatedAt);
    }

    @Test
    void givenGithubClientWhenExternalApiReturnsMalformedBodyThenReturnsEpoch() {
        // Проверяет fallback в Instant.EPOCH при невалидном JSON от GitHub API.
        wireMockServer.stubFor(get(urlEqualTo("/github/repos/user/repo"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"updated_at\": }")));

        Instant updatedAt = githubClient.fetchRepositoryUpdatedAt("user", "repo");

        assertEquals(Instant.EPOCH, updatedAt);
    }

    @Test
    void givenStackoverflowClientWhenExternalApiReturnsHttpErrorThenReturnsEpoch() {
        // Проверяет fallback в Instant.EPOCH при HTTP-ошибке StackOverflow API.
        wireMockServer.stubFor(get(urlEqualTo("/so/questions/123?site=stackoverflow"))
                .willReturn(aResponse().withStatus(500).withBody("internal error")));

        Instant updatedAt = stackoverflowClient.fetchQuestionUpdatedAt(123L);

        assertEquals(Instant.EPOCH, updatedAt);
    }

    @Test
    void givenStackoverflowClientWhenExternalApiReturnsMalformedBodyThenReturnsEpoch() {
        // Проверяет fallback в Instant.EPOCH при невалидном JSON от StackOverflow API.
        wireMockServer.stubFor(get(urlEqualTo("/so/questions/123?site=stackoverflow"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"items\":[{\"last_activity_date\":\"wrong\"}]}")));

        Instant updatedAt = stackoverflowClient.fetchQuestionUpdatedAt(123L);

        assertEquals(Instant.EPOCH, updatedAt);
    }

    @Test
    void givenGithubClientWhenLatestIssueApiReturnsValidBodyThenReturnsStructuredDetails() {
        // Проверяет полный контракт маппинга последнего Issue/PR из GitHub API.
        wireMockServer.stubFor(get(urlEqualTo("/github/repos/user/repo/issues?state=all&sort=created&direction=desc&per_page=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [{
                                  "title":"Fix flaky test",
                                  "body":"Description",
                                  "user":{"login":"octocat"},
                                  "created_at":"2026-03-02T00:00:00Z",
                                  "pull_request":null
                                }]
                                """)));

        var result = githubClient.fetchLatestIssueOrPullRequest("user", "repo");

        assertEquals(
                Optional.of(new GithubIssueOrPullRequestUpdate(
                        true,
                        "Fix flaky test",
                        "octocat",
                        Instant.parse("2026-03-02T00:00:00Z"),
                        "Description")),
                result);
    }

    @Test
    void givenStackoverflowClientWhenLatestActivityApisReturnValidBodyThenReturnsNewestEvent() {
        // Проверяет полный контракт выбора и маппинга самого нового события StackOverflow.
        wireMockServer.stubFor(get(urlEqualTo("/so/questions/123?site=stackoverflow&filter=withbody"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"items":[{"title":"How to test scheduler?"}]}
                                """)));
        wireMockServer.stubFor(get(urlEqualTo(
                        "/so/questions/123/answers?site=stackoverflow&order=desc&sort=creation&filter=withbody&pagesize=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"items":[{"creation_date":1772409600,"body":"answer body","owner":{"display_name":"alice"}}]}
                                """)));
        wireMockServer.stubFor(get(urlEqualTo(
                        "/so/questions/123/comments?site=stackoverflow&order=desc&sort=creation&filter=withbody&pagesize=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"items":[{"creation_date":1772300000,"body":"comment body","owner":{"display_name":"bob"}}]}
                                """)));

        var result = stackoverflowClient.fetchLatestAnswerOrCommentUpdate(123L);

        assertEquals(
                Optional.of(new StackoverflowAnswerOrCommentUpdate(
                        "How to test scheduler?",
                        true,
                        "alice",
                        Instant.parse("2026-03-02T00:00:00Z"),
                        "answer body")),
                result);
    }
}
