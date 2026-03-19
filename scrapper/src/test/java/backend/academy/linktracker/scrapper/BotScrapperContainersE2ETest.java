package backend.academy.linktracker.scrapper;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathTemplate;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class BotScrapperContainersE2ETest {

    private static final Network NETWORK = Network.newNetwork();
    private static final Path PROJECT_ROOT = resolveProjectRoot();
    /**
     * Явно указываем registry-qualified имена, чтобы Testcontainers в CI
     * не подменял локально собранные образы через TESTCONTAINERS_HUB_IMAGE_NAME_PREFIX.
     */
    private static final String BOT_IMAGE = "localhost/link-tracker-bot-it:test";

    private static final String SCRAPPER_IMAGE = "localhost/link-tracker-scrapper-it:test";

    static {
        buildDockerImage(BOT_IMAGE, PROJECT_ROOT.resolve("bot/Dockerfile.it"));
        buildDockerImage(SCRAPPER_IMAGE, PROJECT_ROOT.resolve("scrapper/Dockerfile.it"));
    }

    @SuppressWarnings("resource")
    @Container
    private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17")
            .withNetwork(NETWORK)
            .withNetworkAliases("postgres")
            .withDatabaseName("link_tracker")
            .withUsername("postgres")
            .withPassword("postgres")
            .waitingFor(Wait.forListeningPort());

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
            .dependsOn(wiremock)
            .withEnv("TELEGRAM_TOKEN", "test-token")
            .withEnv("APP_TELEGRAM_URL", "http://wiremock:8080/bot")
            .withEnv("SCRAPPER_BASE_URL", "http://scrapper:8081")
            .withEnv("SCRAPPER_TRANSPORT", "HTTP")
            .withEnv("APP_TELEGRAM_POLLING_ENABLED", "false")
            .withEnv("APP_TELEGRAM_SET_MY_COMMANDS_ON_STARTUP", "false")
            .withEnv("BOT_GRPC_SERVER_ENABLED", "false")
            .waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200).forPort(8080));

    @SuppressWarnings("resource")
    @Container
    private static final GenericContainer<?> scrapper = new GenericContainer<>(SCRAPPER_IMAGE)
            .withNetwork(NETWORK)
            .withNetworkAliases("scrapper")
            .withExposedPorts(8081)
            .dependsOn(postgres, bot, wiremock)
            .withEnv("GITHUB_BASE_URL", "http://wiremock:8080/github")
            .withEnv("STACKOVERFLOW_BASE_URL", "http://wiremock:8080/so")
            .withEnv("APP_BOT_BASE_URL", "http://bot:8080")
            .withEnv("APP_BOT_TRANSPORT", "HTTP")
            .withEnv("APP_SCHEDULER_INTERVAL", "500ms")
            .withEnv("BOT_BASE_URL", "http://bot:8080")
            .withEnv("BOT_TRANSPORT", "HTTP")
            .withEnv("SCRAPPER_SCHEDULER_INTERVAL", "500ms")
            .withEnv("SCRAPPER_GRPC_SERVER_ENABLED", "false")
            .withEnv("DATASOURCE_URL", "jdbc:postgresql://postgres:5432/link_tracker")
            .withEnv("DATASOURCE_USERNAME", "postgres")
            .withEnv("DATASOURCE_PASSWORD", "postgres")
            .withEnv("LIQUIBASE_ENABLED", "true")
            .waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200).forPort(8081));

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
    void givenUpdatedGithubLinkWhenSchedulerRunsInContainersThenBotReceivesExactNotificationRequest() throws Exception {
        // Проверяет e2e-контракт: обновление ссылки в scrapper приводит к точному запросу sendMessage в bot.
        configureWiremock();
        stubFor(post(urlMatching("/bot[^/]+/sendMessage")).willReturn(okJson("{\"ok\":true}")));
        stubGithubUpdatedAt("2026-02-25T00:00:00Z", 10);
        registerChatAndAddGithubLink(42L);
        // Force metadata change after link registration to avoid race conditions.
        stubGithubUpdatedAt("2026-02-26T00:00:00Z", 1);

        await().atMost(90, SECONDS)
                .untilAsserted(() -> verifySendMessage(42L, "Обнаружено обновление по отслеживаемой ссылке."));
    }

    @Test
    void givenUpdatedGithubLinkWhenSchedulerRunsInContainersThenScrapperRequestsGithubApi() throws Exception {
        // Проверяет e2e-контракт: scheduler scrapper запрашивает GitHub API при отслеживании обновления.
        configureWiremock();
        stubFor(post(urlMatching("/bot[^/]+/sendMessage")).willReturn(okJson("{\"ok\":true}")));
        stubGithubUpdatedAt("2026-02-25T00:00:00Z", 10);
        registerChatAndAddGithubLink(43L);

        await().atMost(90, SECONDS)
                .untilAsserted(() ->
                        WireMock.verify(WireMock.getRequestedFor(urlEqualTo("/github/repos/octocat/Hello-World"))));
    }

    @Test
    void givenUnknownChatWhenAddLinkRequestedInContainersThenReturnsNotFound() throws Exception {
        // Проверяет e2e-контракт: добавление ссылки в несуществующий чат возвращает HTTP 404.
        configureWiremock();

        HttpResponse<String> addResponse = postLink(999L, "https://github.com/octocat/Hello-World");

        assertEquals(404, addResponse.statusCode());
    }

    @Test
    void givenDeletedChatWhenAddLinkRequestedInContainersThenReturnsNotFound() throws Exception {
        // Проверяет e2e-контракт: добавление ссылки в удалённый чат возвращает HTTP 404.
        configureWiremock();
        HttpResponse<String> registerResponse = postWithoutBody("/tg-chat/77");
        assertEquals(200, registerResponse.statusCode());

        HttpResponse<String> deleteResponse = deleteWithoutBody("/tg-chat/77");
        assertEquals(200, deleteResponse.statusCode());

        HttpResponse<String> addResponse = postLink(77L, "https://github.com/octocat/Hello-World");
        assertEquals(404, addResponse.statusCode());
    }

    private void configureWiremock() {
        WireMock.configureFor(wiremock.getHost(), wiremock.getMappedPort(8080));
        WireMock.reset();
    }

    private void stubGithubUpdatedAt(String updatedAt, int priority) {
        stubFor(get(urlEqualTo("/github/repos/octocat/Hello-World"))
                .atPriority(priority)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"updated_at\":\"%s\"}".formatted(updatedAt))));
    }

    private void registerChatAndAddGithubLink(long chatId) throws IOException, InterruptedException {
        HttpResponse<String> registerResponse = postWithoutBody("/tg-chat/" + chatId);
        assertEquals(200, registerResponse.statusCode());
        HttpResponse<String> addResponse = postLink(chatId, "https://github.com/octocat/Hello-World");
        assertEquals(200, addResponse.statusCode());
    }

    private void verifySendMessage(long chatId, String expectedText) {
        var requestPattern = postRequestedFor(urlPathTemplate("/bot{token}/sendMessage"))
                .withPathParam("token", equalTo("test-token"));
        verify(1, requestPattern);

        LoggedRequest request = findAll(requestPattern).getFirst();
        Map<String, String> actualFormData = parseFormData(request.getBodyAsString());
        Map<String, String> expectedFormData = Map.of("chat_id", String.valueOf(chatId), "text", expectedText);
        assertEquals(expectedFormData, actualFormData);
    }

    private Map<String, String> parseFormData(String body) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            String[] keyValue = pair.split("=", 2);
            String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
            String value = keyValue.length == 2 ? URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8) : "";
            result.put(key, value);
        }
        return result;
    }

    private HttpResponse<String> postWithoutBody(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(scrapperBaseUrl() + path))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> deleteWithoutBody(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(scrapperBaseUrl() + path))
                .DELETE()
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postLink(long chatId, String link) throws IOException, InterruptedException {
        String addLinkBody = """
                {
                  "link": "%s",
                  "tags": [],
                  "filters": []
                }
                """.formatted(link);
        HttpRequest request = HttpRequest.newBuilder(URI.create(scrapperBaseUrl() + "/links"))
                .header("Tg-Chat-Id", String.valueOf(chatId))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(addLinkBody))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String scrapperBaseUrl() {
        return "http://%s:%d".formatted(scrapper.getHost(), scrapper.getMappedPort(8081));
    }
}
