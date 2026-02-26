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
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class BotScrapperContainersE2ETest {

    private static final Network NETWORK = Network.newNetwork();
    private static final Path PROJECT_ROOT = resolveProjectRoot();
    private static final String BOT_IMAGE = "link-tracker-bot-it:test";
    private static final String SCRAPPER_IMAGE = "link-tracker-scrapper-it:test";

    static {
        buildDockerImage(BOT_IMAGE, PROJECT_ROOT.resolve("bot/Dockerfile"));
        buildDockerImage(SCRAPPER_IMAGE, PROJECT_ROOT.resolve("scrapper/Dockerfile"));
    }

    @SuppressWarnings("resource")
    @Container
    private static final GenericContainer<?> wiremock = new GenericContainer<>("wiremock/wiremock:3.13.1")
            .withNetwork(NETWORK)
            .withNetworkAliases("wiremock")
            .withExposedPorts(8080)
            .waitingFor(Wait.forListeningPort());

    @SuppressWarnings("resource")
    @Container
    private static final GenericContainer<?> bot = new GenericContainer<>(BOT_IMAGE)
            .withNetwork(NETWORK)
            .withNetworkAliases("bot")
            .withExposedPorts(8080)
            .withEnv("TELEGRAM_TOKEN", "test-token")
            .withEnv("APP_TELEGRAM_URL", "http://wiremock:8080/bot")
            .withEnv("APP_TELEGRAM_POLLING_ENABLED", "false")
            .withEnv("APP_TELEGRAM_SET_MY_COMMANDS_ON_STARTUP", "false")
            .withEnv("BOT_GRPC_SERVER_ENABLED", "false")
            .waitingFor(Wait.forListeningPort());

    @SuppressWarnings("resource")
    @Container
    private static final GenericContainer<?> scrapper = new GenericContainer<>(SCRAPPER_IMAGE)
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

    private static Path resolveProjectRoot() {
        String multiModuleRoot = System.getProperty("maven.multiModuleProjectDirectory");
        if (multiModuleRoot != null && !multiModuleRoot.isBlank()) {
            Path fromProperty =
                    findProjectRoot(Path.of(multiModuleRoot).toAbsolutePath().normalize());
            if (fromProperty != null) {
                return fromProperty;
            }
        }

        Path fromCwd = findProjectRoot(Path.of("").toAbsolutePath().normalize());
        if (fromCwd != null) {
            return fromCwd;
        }

        throw new IllegalStateException(
                "Unable to resolve multi-module project root directory for Testcontainers build context");
    }

    private static Path findProjectRoot(Path start) {
        Path current = start;
        while (current != null) {
            if (hasRequiredModules(current)) {
                return current;
            }
            Path nested = current.resolve("link-tracker");
            if (hasRequiredModules(nested)) {
                return nested;
            }
            current = current.getParent();
        }
        return null;
    }

    private static boolean hasRequiredModules(Path candidate) {
        return Files.exists(candidate.resolve("bot/pom.xml")) && Files.exists(candidate.resolve("scrapper/pom.xml"));
    }

    private static void buildDockerImage(String imageTag, Path dockerfile) {
        ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "build", "-f", dockerfile.toString(), "-t", imageTag, PROJECT_ROOT.toString());
        processBuilder.redirectErrorStream(true);
        try {
            Process process = processBuilder.start();
            String output;
            try (InputStream stream = process.getInputStream()) {
                output = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("docker build failed for image '%s'%n%s".formatted(imageTag, output));
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while building Docker image: " + imageTag, ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to execute docker build for image: " + imageTag, ex);
        }
    }

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

        await().atMost(30, SECONDS)
                .untilAsserted(() -> WireMock.verify(postRequestedFor(urlMatching("/bot[^/]+/sendMessage"))
                        .withRequestBody(WireMock.containing("chat_id=42"))));

        WireMock.verify(WireMock.getRequestedFor(urlEqualTo("/github/repos/octocat/Hello-World")));
        WireMock.verify(postRequestedFor(urlMatching("/bot[^/]+/sendMessage"))
                .withRequestBody(WireMock.containing("chat_id=42")));
    }
}
