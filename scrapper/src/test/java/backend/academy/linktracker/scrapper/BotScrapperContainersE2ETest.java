package backend.academy.linktracker.scrapper;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.client.WireMock;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@EnabledIfSystemProperty(named = "runContainersE2E", matches = "true")
class BotScrapperContainersE2ETest {

    private static final Network NETWORK = Network.newNetwork();
    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Container
    private static final GenericContainer<?> wiremock = new GenericContainer<>("wiremock/wiremock:3.13.1")
            .withNetwork(NETWORK)
            .withNetworkAliases("wiremock")
            .withExposedPorts(8080)
            .waitingFor(Wait.forListeningPort());

    @Container
    private static final GenericContainer<?> bot = new GenericContainer<>(new ImageFromDockerfile("link-tracker-bot-it", false)
                    .withFileFromPath(".", PROJECT_ROOT)
                    .withDockerfile(PROJECT_ROOT.resolve("bot/Dockerfile")))
            .withNetwork(NETWORK)
            .withNetworkAliases("bot")
            .withExposedPorts(8080)
            .withEnv("TELEGRAM_TOKEN", "test-token")
            .withEnv("APP_TELEGRAM_URL", "http://wiremock:8080/bot")
            .withEnv("APP_TELEGRAM_POLLING_ENABLED", "false")
            .withEnv("APP_TELEGRAM_SET_MY_COMMANDS_ON_STARTUP", "false")
            .withEnv("BOT_GRPC_SERVER_ENABLED", "false")
            .waitingFor(Wait.forListeningPort());

    @Container
    private static final GenericContainer<?> scrapper =
            new GenericContainer<>(new ImageFromDockerfile("link-tracker-scrapper-it", false)
                            .withFileFromPath(".", PROJECT_ROOT)
                            .withDockerfile(PROJECT_ROOT.resolve("scrapper/Dockerfile")))
                    .withNetwork(NETWORK)
                    .withNetworkAliases("scrapper")
                    .withExposedPorts(8081)
                    .withEnv("GITHUB_BASE_URL", "http://wiremock:8080/github")
                    .withEnv("STACKOVERFLOW_BASE_URL", "http://wiremock:8080/so")
                    .withEnv("BOT_BASE_URL", "http://bot:8080")
                    .withEnv("BOT_TRANSPORT", "HTTP")
                    .withEnv("SCRAPPER_SCHEDULER_INTERVAL", "2s")
                    .withEnv("SCRAPPER_GRPC_SERVER_ENABLED", "false")
                    .waitingFor(Wait.forListeningPort());

    @Test
    void shouldDeliverUpdateFromScrapperToBotOverHttpInContainers() throws Exception {
        WireMock.configureFor(wiremock.getHost(), wiremock.getMappedPort(8080));

        stubFor(post(urlMatching("/bot[^/]+/sendMessage")).willReturn(okJson("{\"ok\":true}")));

        stubFor(get(urlEqualTo("/github/repos/octocat/Hello-World"))
                .inScenario("Github Updated")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"updated_at\":\"2026-02-25T00:00:00Z\"}"))
                .willSetStateTo("changed"));
        stubFor(get(urlEqualTo("/github/repos/octocat/Hello-World"))
                .inScenario("Github Updated")
                .whenScenarioStateIs("changed")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"updated_at\":\"2026-02-26T00:00:00Z\"}")));

        HttpClient httpClient = HttpClient.newHttpClient();
        String scrapperBaseUrl = "http://%s:%d".formatted(scrapper.getHost(), scrapper.getMappedPort(8081));

        HttpResponse<String> registerResponse = httpClient.send(
                HttpRequest.newBuilder(URI.create(scrapperBaseUrl + "/tg-chat/42"))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        if (registerResponse.statusCode() != 200) {
            throw new IllegalStateException("Unexpected register response code: " + registerResponse.statusCode());
        }

        String addLinkBody = """
                {
                  "link": "https://github.com/octocat/Hello-World",
                  "tags": [],
                  "filters": []
                }
                """;
        HttpResponse<String> addResponse = httpClient.send(
                HttpRequest.newBuilder(URI.create(scrapperBaseUrl + "/links"))
                        .header("Tg-Chat-Id", "42")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(addLinkBody))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        if (addResponse.statusCode() != 200) {
            throw new IllegalStateException("Unexpected add link response code: " + addResponse.statusCode());
        }

        await().atMost(30, SECONDS).untilAsserted(() -> WireMock.verify(
                postRequestedFor(urlMatching("/bot[^/]+/sendMessage"))
                        .withRequestBody(WireMock.containing("chat_id=42"))));

        WireMock.verify(WireMock.getRequestedFor(urlEqualTo("/github/repos/octocat/Hello-World")));
        WireMock.verify(postRequestedFor(urlMatching("/bot[^/]+/sendMessage")).withRequestBody(WireMock.containing("chat_id=42")));
    }
}
