package backend.academy.linktracker.scrapper.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import backend.academy.linktracker.scrapper.properties.GithubProperties;
import backend.academy.linktracker.scrapper.properties.StackoverflowProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.time.Instant;
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
        wireMockServer.stubFor(get(urlEqualTo("/github/repos/user/repo"))
                .willReturn(aResponse().withStatus(503).withBody("upstream unavailable")));

        Instant updatedAt = githubClient.fetchRepositoryUpdatedAt("user", "repo");

        assertEquals(Instant.EPOCH, updatedAt);
    }

    @Test
    void givenGithubClientWhenExternalApiReturnsMalformedBodyThenReturnsEpoch() {
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
        wireMockServer.stubFor(get(urlEqualTo("/so/questions/123?site=stackoverflow"))
                .willReturn(aResponse().withStatus(500).withBody("internal error")));

        Instant updatedAt = stackoverflowClient.fetchQuestionUpdatedAt(123L);

        assertEquals(Instant.EPOCH, updatedAt);
    }

    @Test
    void givenStackoverflowClientWhenExternalApiReturnsMalformedBodyThenReturnsEpoch() {
        wireMockServer.stubFor(get(urlEqualTo("/so/questions/123?site=stackoverflow"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"items\":[{\"last_activity_date\":\"wrong\"}]}")));

        Instant updatedAt = stackoverflowClient.fetchQuestionUpdatedAt(123L);

        assertEquals(Instant.EPOCH, updatedAt);
    }
}
