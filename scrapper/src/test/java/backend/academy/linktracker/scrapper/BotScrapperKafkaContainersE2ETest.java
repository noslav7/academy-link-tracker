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
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
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
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
class BotScrapperKafkaContainersE2ETest {

    private static final Network NETWORK = Network.newNetwork();
    private static final Path PROJECT_ROOT = resolveProjectRoot();
    private static final String BOT_IMAGE = "localhost/link-tracker-bot-it:test";
    private static final String SCRAPPER_IMAGE = "localhost/link-tracker-scrapper-it:test";

    static {
        buildDockerImage(BOT_IMAGE, PROJECT_ROOT.resolve("bot/Dockerfile.it"));
        buildDockerImage(SCRAPPER_IMAGE, PROJECT_ROOT.resolve("scrapper/Dockerfile.it"));
    }

    @SuppressWarnings("resource")
    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withNetwork(NETWORK)
            .withNetworkAliases("postgres")
            .withDatabaseName("link_tracker")
            .withUsername("postgres")
            .withPassword("postgres")
            .waitingFor(Wait.forListeningPort());

    @SuppressWarnings("resource")
    @Container
    private static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.1.1"))
            .withNetwork(NETWORK)
            .withNetworkAliases("kafka")
            .withStartupTimeout(Duration.ofMinutes(3));

    @SuppressWarnings("resource")
    @Container
    private static final GenericContainer<?> SCHEMA_REGISTRY = new GenericContainer<>(
                    "apicurio/apicurio-registry-mem:2.6.13.Final")
            .withNetwork(NETWORK)
            .withNetworkAliases("schema-registry")
            .withEnv("QUARKUS_HTTP_PORT", "8080")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/apis/registry/v2/system/info")
                    .forStatusCode(200)
                    .forPort(8080));

    @SuppressWarnings("resource")
    @Container
    private static final GenericContainer<?> WIREMOCK = new GenericContainer<>("wiremock/wiremock:3.13.1")
            .withNetwork(NETWORK)
            .withNetworkAliases("wiremock")
            .withExposedPorts(8080)
            .waitingFor(Wait.forListeningPort());

    @SuppressWarnings("resource")
    @Container
    private static final GenericContainer<?> BOT = new GenericContainer<>(BOT_IMAGE)
            .withNetwork(NETWORK)
            .withNetworkAliases("bot")
            .withExposedPorts(8080)
            .dependsOn(WIREMOCK, KAFKA, SCHEMA_REGISTRY)
            .withEnv("TELEGRAM_TOKEN", "test-token")
            .withEnv("APP_TELEGRAM_URL", "http://wiremock:8080/bot")
            .withEnv("APP_TELEGRAM_POLLING_ENABLED", "false")
            .withEnv("APP_TELEGRAM_SET_MY_COMMANDS_ON_STARTUP", "false")
            .withEnv("SCRAPPER_TRANSPORT", "HTTP")
            .withEnv("SCRAPPER_BASE_URL", "http://scrapper:8081")
            .withEnv("KAFKA_BOOTSTRAP_SERVERS", "kafka:9093")
            .withEnv("KAFKA_SCHEMA_REGISTRY_URL", "http://schema-registry:8080/apis/registry/v2")
            .withEnv("KAFKA_TOPIC", "link-updates")
            .withEnv("BOT_GRPC_SERVER_ENABLED", "false")
            .waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200).forPort(8080));

    @SuppressWarnings("resource")
    @Container
    private static final GenericContainer<?> SCRAPPER = new GenericContainer<>(SCRAPPER_IMAGE)
            .withNetwork(NETWORK)
            .withNetworkAliases("scrapper")
            .withExposedPorts(8081)
            .dependsOn(POSTGRES, BOT, WIREMOCK, KAFKA, SCHEMA_REGISTRY)
            .withEnv("GITHUB_BASE_URL", "http://wiremock:8080/github")
            .withEnv("STACKOVERFLOW_BASE_URL", "http://wiremock:8080/so")
            .withEnv("APP_BOT_TRANSPORT", "KAFKA")
            .withEnv("KAFKA_BOOTSTRAP_SERVERS", "kafka:9093")
            .withEnv("KAFKA_SCHEMA_REGISTRY_URL", "http://schema-registry:8080/apis/registry/v2")
            .withEnv("KAFKA_TOPIC", "link-updates")
            .withEnv("SCRAPPER_SCHEDULER_INTERVAL", "500ms")
            .withEnv("OUTBOX_PUBLISH_INTERVAL", "500ms")
            .withEnv("SCRAPPER_GRPC_SERVER_ENABLED", "false")
            .withEnv("LINKS_CACHE_ENABLED", "false")
            .withEnv("MANAGEMENT_HEALTH_REDIS_ENABLED", "false")
            .withEnv("DATASOURCE_URL", "jdbc:postgresql://postgres:5432/link_tracker")
            .withEnv("DATASOURCE_USERNAME", "postgres")
            .withEnv("DATASOURCE_PASSWORD", "postgres")
            .withEnv("LIQUIBASE_ENABLED", "true")
            .waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200).forPort(8081));

    @Test
    void givenUpdatedGithubLinkWhenTransportKafkaThenScrapperSendsToKafkaAndBotNotifiesUser() throws Exception {
        // Проверяет полный E2E-сценарий: обновление GitHub-ссылки в scrapper вызывает Telegram-уведомление через Kafka.
        configureWiremock();
        stubFor(post(urlMatching("/bot[^/]+/sendMessage")).willReturn(okJson("{\"ok\":true}")));
        stubGithubUpdatedAt("2026-02-25T00:00:00Z", 10);
        registerChatAndAddGithubLink(7001L);
        stubGithubUpdatedAt("2026-02-26T00:00:00Z", 1);

        await().atMost(120, SECONDS).untilAsserted(() -> verifySendMessage(7001L, """
                Обнаружено обновление по отслеживаемой ссылке.

                Обновление в репозитории octocat/Hello-World (возможно: новый PR или Issue)""".stripTrailing()));
    }

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

    private void configureWiremock() {
        WireMock.configureFor(WIREMOCK.getHost(), WIREMOCK.getMappedPort(8080));
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
        HttpResponse<String> addResponse = postLink(chatId, "https://github.com/octocat/Hello-World");
        assertAll(
                () -> assertEquals(200, registerResponse.statusCode()),
                () -> assertEquals(200, addResponse.statusCode()));
    }

    private void verifySendMessage(long chatId, String expectedText) {
        var requestPattern = postRequestedFor(urlPathTemplate("/bot{token}/sendMessage"))
                .withPathParam("token", equalTo("test-token"));
        var requests = findAll(requestPattern);
        assertEquals(1, requests.size());
        LoggedRequest request = requests.getFirst();
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
        return "http://%s:%d".formatted(SCRAPPER.getHost(), SCRAPPER.getMappedPort(8081));
    }
}
