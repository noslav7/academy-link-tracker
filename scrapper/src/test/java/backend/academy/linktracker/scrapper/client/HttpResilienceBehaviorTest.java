package backend.academy.linktracker.scrapper.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.academy.linktracker.scrapper.properties.GithubProperties;
import backend.academy.linktracker.scrapper.properties.HttpResilienceProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Tag("unit")
class HttpResilienceBehaviorTest {

    private static final long TIMING_TOLERANCE_MS = 80L;

    private WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void givenSlowExternalApiWhenTimeoutExceededThenFailsFastWithTimeoutError() {
        // Проверяет, что timeout прерывает зависший внешний вызов раньше фактического ответа.
        wireMockServer.stubFor(get(urlEqualTo("/timeout"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(800).withBody("{}")));

        HttpResilienceProperties properties = properties(Duration.ofMillis(120), 1, Duration.ofMillis(100));
        RestClient restClient =
                restClient("http://localhost:%d".formatted(wireMockServer.port()), properties.getTimeout());
        ResilientHttpExecutor resilientHttpExecutor = new ResilientHttpExecutor(properties);

        long startNanos = System.nanoTime();
        assertThrows(
                ResourceAccessException.class,
                () -> resilientHttpExecutor.execute(
                        () -> restClient.get().uri("/timeout").retrieve().toBodilessEntity()));
        long elapsedMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();

        assertTrue(elapsedMs < 800, "HTTP timeout должен завершить вызов раньше ответа внешнего сервиса");
    }

    @Test
    void givenRetryable5xxStatusesWhenCallingGithubThenRetriesAndEventuallySucceeds() {
        // Проверяет, что retry на 5xx выполняет повторные попытки и завершает запрос успешным ответом.
        String scenario = "github-retry-5xx";
        wireMockServer.stubFor(get(urlEqualTo("/github/repos/user/repo"))
                .inScenario(scenario)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("second-attempt"));
        wireMockServer.stubFor(get(urlEqualTo("/github/repos/user/repo"))
                .inScenario(scenario)
                .whenScenarioStateIs("second-attempt")
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("third-attempt"));
        wireMockServer.stubFor(get(urlEqualTo("/github/repos/user/repo"))
                .inScenario(scenario)
                .whenScenarioStateIs("third-attempt")
                .willReturn(okJson("{\"updated_at\":\"2026-03-02T00:00:00Z\"}")));

        RestGithubClient githubClient = githubClient(properties(Duration.ofSeconds(1), 3, Duration.ofMillis(80)));
        Instant updatedAt = githubClient.fetchRepositoryUpdatedAt("user", "repo");

        assertEquals(Instant.parse("2026-03-02T00:00:00Z"), updatedAt);
        wireMockServer.verify(exactly(3), getRequestedFor(urlPathEqualTo("/github/repos/user/repo")));
    }

    @Test
    void givenNonRetryable4xxStatusWhenCallingGithubThenDoesNotRetry() {
        // Проверяет, что 4xx не считается retryable и запрос выполняется ровно один раз.
        wireMockServer.stubFor(get(urlEqualTo("/github/repos/user/repo"))
                .willReturn(aResponse().withStatus(400)));

        RestGithubClient githubClient = githubClient(properties(Duration.ofSeconds(1), 3, Duration.ofMillis(80)));
        Instant updatedAt = githubClient.fetchRepositoryUpdatedAt("user", "repo");

        assertEquals(Instant.EPOCH, updatedAt);
        wireMockServer.verify(exactly(1), getRequestedFor(urlPathEqualTo("/github/repos/user/repo")));
    }

    @Test
    void givenRetryable5xxStatusWhenAllAttemptsFailThenReturnsFallbackAfterMaxAttempts() {
        // Проверяет, что после исчерпания maxAttempts возвращается fallback-значение.
        wireMockServer.stubFor(get(urlEqualTo("/github/repos/user/repo"))
                .willReturn(aResponse().withStatus(503)));

        int maxAttempts = 4;
        RestGithubClient githubClient =
                githubClient(properties(Duration.ofSeconds(1), maxAttempts, Duration.ofMillis(50)));
        Instant updatedAt = githubClient.fetchRepositoryUpdatedAt("user", "repo");

        assertEquals(Instant.EPOCH, updatedAt);
        wireMockServer.verify(exactly(maxAttempts), getRequestedFor(urlPathEqualTo("/github/repos/user/repo")));
    }

    @Test
    void givenConstantBackoffWhenRetryingThenWaitsConfiguredIntervalBetweenAttempts() {
        // Проверяет, что constant backoff выдерживает заданный интервал между повторами.
        Duration retryDelay = Duration.ofMillis(120);
        HttpResilienceProperties properties = properties(Duration.ofSeconds(1), 3, retryDelay);
        ResilientHttpExecutor executor = new ResilientHttpExecutor(properties);
        AtomicInteger attempts = new AtomicInteger();
        List<Long> attemptTimes = new ArrayList<>();

        String result = executor.execute(() -> {
            attemptTimes.add(System.nanoTime());
            if (attempts.getAndIncrement() < 2) {
                throw HttpServerErrorException.create(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "error",
                        HttpHeaders.EMPTY,
                        new byte[0],
                        StandardCharsets.UTF_8);
            }
            return "ok";
        });

        long firstIntervalMs =
                Duration.ofNanos(attemptTimes.get(1) - attemptTimes.get(0)).toMillis();
        long secondIntervalMs =
                Duration.ofNanos(attemptTimes.get(2) - attemptTimes.get(1)).toMillis();

        assertEquals("ok", result);
        assertEquals(3, attemptTimes.size());
        assertTrue(
                firstIntervalMs >= retryDelay.toMillis() - TIMING_TOLERANCE_MS,
                "Интервал между 1 и 2 попыткой меньше configured delay");
        assertTrue(
                secondIntervalMs >= retryDelay.toMillis() - TIMING_TOLERANCE_MS,
                "Интервал между 2 и 3 попыткой меньше configured delay");
    }

    @Test
    void givenExponentialBackoffWhenRetryingThenAlternativeStrategyIsApplied() {
        // Проверяет, что exponential backoff увеличивает паузу на каждой новой попытке.
        HttpResilienceProperties properties = properties(Duration.ofSeconds(1), 3, Duration.ofMillis(100));
        properties.getRetry().setBackoffStrategy(HttpResilienceProperties.BackoffStrategy.EXPONENTIAL);
        properties.getRetry().setExponentialMultiplier(2.0d);
        ResilientHttpExecutor executor = new ResilientHttpExecutor(properties);
        AtomicInteger attempts = new AtomicInteger();
        List<Long> attemptTimes = new ArrayList<>();

        String result = executor.execute(() -> {
            attemptTimes.add(System.nanoTime());
            if (attempts.getAndIncrement() < 2) {
                throw HttpServerErrorException.create(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "error",
                        HttpHeaders.EMPTY,
                        new byte[0],
                        StandardCharsets.UTF_8);
            }
            return "ok";
        });

        long firstIntervalMs =
                Duration.ofNanos(attemptTimes.get(1) - attemptTimes.get(0)).toMillis();
        long secondIntervalMs =
                Duration.ofNanos(attemptTimes.get(2) - attemptTimes.get(1)).toMillis();

        assertEquals("ok", result);
        assertEquals(3, attemptTimes.size());
        assertTrue(
                firstIntervalMs >= 100 - TIMING_TOLERANCE_MS,
                "Первая пауза exponential backoff должна быть не меньше 100ms");
        assertTrue(
                secondIntervalMs >= 200 - TIMING_TOLERANCE_MS,
                "Вторая пауза exponential backoff должна быть не меньше 200ms");
        assertTrue(secondIntervalMs > firstIntervalMs, "При exponential вторая пауза должна быть больше первой");
    }

    private RestGithubClient githubClient(HttpResilienceProperties properties) {
        GithubProperties githubProperties = new GithubProperties();
        githubProperties.setBaseUrl("http://localhost:%d/github".formatted(wireMockServer.port()));
        RestClient restClient = restClient(githubProperties.getBaseUrl(), properties.getTimeout());
        return new RestGithubClient(restClient, githubProperties, new ResilientHttpExecutor(properties));
    }

    private static HttpResilienceProperties properties(Duration timeout, int maxAttempts, Duration delay) {
        HttpResilienceProperties properties = new HttpResilienceProperties();
        properties.setTimeout(timeout);

        HttpResilienceProperties.Retry retry = new HttpResilienceProperties.Retry();
        retry.setMaxAttempts(maxAttempts);
        retry.setDelay(delay);
        retry.setRetryableStatuses(List.of(500, 502, 503, 504));
        properties.setRetry(retry);
        return properties;
    }

    private static RestClient restClient(String baseUrl, Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .build();
    }
}
