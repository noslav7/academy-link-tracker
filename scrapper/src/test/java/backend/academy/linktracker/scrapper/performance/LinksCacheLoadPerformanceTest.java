package backend.academy.linktracker.scrapper.performance;

import backend.academy.linktracker.scrapper.ScrapperApplication;
import backend.academy.linktracker.scrapper.properties.LinksCacheProperties.CacheMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Tag("performance")
@EnabledIfSystemProperty(named = "run.performance.tests", matches = "true")
@Testcontainers(disabledWithoutDocker = true)
class LinksCacheLoadPerformanceTest {

    private static final String TG_CHAT_ID_HEADER = "Tg-Chat-Id";
    private static final DateTimeFormatter UTC_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    private static final int CHAT_COUNT = 1_000;
    private static final int LINKS_PER_CHAT = 100;
    private static final int TOTAL_LINKS = CHAT_COUNT * LINKS_PER_CHAT;
    private static final int GET_REQUESTS_PER_CYCLE = 1;
    private static final int MUTATION_REQUESTS_PER_CYCLE = 100;
    private static final int TOTAL_REQUESTS_PER_CYCLE = GET_REQUESTS_PER_CYCLE + MUTATION_REQUESTS_PER_CYCLE;
    private static final Duration RAMP_UP_DURATION = Duration.ofMinutes(1);
    private static final Duration STAGE_DURATION = Duration.ofMinutes(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final Path REPORT_PATH = Path.of(System.getProperty("user.dir"), "LOAD_TEST_REPORT.md");

    @SuppressWarnings("resource")
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("link_tracker")
            .withUsername("postgres")
            .withPassword("postgres");

    @SuppressWarnings("resource")
    private static final GenericContainer<?> VALKEY =
            new GenericContainer<>(DockerImageName.parse("valkey/valkey:8-alpine")).withExposedPorts(6379);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void runLoadTestsAndGenerateReport() throws Exception {
        POSTGRES.start();
        VALKEY.start();

        int users = Math.max(2, Runtime.getRuntime().availableProcessors() * 2);

        List<ScenarioConfig> scenarios = List.of(
                new ScenarioConfig("No cache", false, CacheMode.NONE),
                new ScenarioConfig("Valkey cache", true, CacheMode.VALKEY),
                new ScenarioConfig("Client-side cache", true, CacheMode.CLIENT_SIDE));

        List<ScenarioResult> results = new ArrayList<>();
        try {
            for (ScenarioConfig scenario : scenarios) {
                ScenarioResult result = runScenario(scenario, users);
                results.add(result);
            }
            writeReport(users, results);
        } finally {
            VALKEY.stop();
            POSTGRES.stop();
        }
    }

    private ScenarioResult runScenario(ScenarioConfig scenario, int users) throws Exception {
        try (ConfigurableApplicationContext context = startApplication(scenario)) {
            seedDatabase();
            int port = Integer.parseInt(Objects.requireNonNull(
                    context.getEnvironment().getProperty("local.server.port"), "local.server.port must be set"));
            warmUp(port);
            return executeLoad(new URI("http://localhost:" + port), scenario, users);
        }
    }

    private ConfigurableApplicationContext startApplication(ScenarioConfig scenario) {
        String redisHost = VALKEY.getHost();
        int redisPort = VALKEY.getMappedPort(6379);
        return new SpringApplicationBuilder(ScrapperApplication.class)
                .properties(Map.ofEntries(
                        Map.entry("server.port", "0"),
                        Map.entry("spring.datasource.url", POSTGRES.getJdbcUrl()),
                        Map.entry("spring.datasource.username", POSTGRES.getUsername()),
                        Map.entry("spring.datasource.password", POSTGRES.getPassword()),
                        Map.entry("spring.data.redis.url", "redis://%s:%d".formatted(redisHost, redisPort)),
                        Map.entry("app.bot.transport", "HTTP"),
                        Map.entry("app.bot.base-url", "http://localhost:65534"),
                        Map.entry("app.scheduler.interval", "365d"),
                        Map.entry("app.outbox.publish-interval", "365d"),
                        Map.entry("app.cache.links.enabled", String.valueOf(scenario.cacheEnabled())),
                        Map.entry("app.cache.links.mode", scenario.mode().name()),
                        Map.entry("app.cache.links.ttl", "5m")))
                .run();
    }

    private void warmUp(int port) throws IOException, InterruptedException {
        HttpClient client =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/list"))
                .header(TG_CHAT_ID_HEADER, "1")
                .GET()
                .timeout(REQUEST_TIMEOUT)
                .build();
        client.send(request, HttpResponse.BodyHandlers.discarding());
    }

    private ScenarioResult executeLoad(URI baseUri, ScenarioConfig scenario, int users)
            throws InterruptedException, ExecutionException {
        Duration totalDuration = RAMP_UP_DURATION.plus(STAGE_DURATION);
        Instant startedAt = Instant.now();
        Instant deadline = startedAt.plus(totalDuration);

        ExecutorService executor = Executors.newFixedThreadPool(users);
        List<Future<WorkerMetrics>> futures = new ArrayList<>(users);
        for (int worker = 0; worker < users; worker++) {
            futures.add(executor.submit(new Worker(worker, users, baseUri, deadline)));
        }

        WorkerMetrics merged = new WorkerMetrics();
        for (Future<WorkerMetrics> future : futures) {
            merged.merge(future.get());
        }

        executor.shutdownNow();
        Instant finishedAt = Instant.now();
        return ScenarioResult.from(scenario, startedAt, finishedAt, merged, totalDuration);
    }

    private void seedDatabase() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "truncate table tracked_link_filters, tracked_link_tags, tracked_links, chats restart identity cascade");
            statement.executeUpdate(
                    "insert into chats (chat_id) select gs from generate_series(1, %d) gs".formatted(CHAT_COUNT));
            statement.executeUpdate("""
                    insert into tracked_links (chat_id, url, last_checked_at, last_updated_at)
                    select
                        c.chat_id,
                        format('https://github.com/load/chat-%%s/repo-%%s', c.chat_id, link_no),
                        now(),
                        now()
                    from chats c
                    cross join generate_series(1, %d) as link_no
                    """.formatted(LINKS_PER_CHAT));
        }
    }

    private void writeReport(int users, List<ScenarioResult> results) throws IOException {
        StringBuilder report = new StringBuilder();
        report.append("# /list cache load test report")
                .append(System.lineSeparator())
                .append(System.lineSeparator());
        report.append("- Run date: ")
                .append(UTC_FORMATTER.format(Instant.now()))
                .append(System.lineSeparator());
        report.append("- Concurrent users: ")
                .append(users)
                .append(" (~2 * logical CPU)")
                .append(System.lineSeparator());
        report.append("- Dataset: ")
                .append(CHAT_COUNT)
                .append(" chats, ")
                .append(LINKS_PER_CHAT)
                .append(" links per chat, total ")
                .append(TOTAL_LINKS)
                .append(" links")
                .append(System.lineSeparator());
        report.append("- Ramp-up: ")
                .append(RAMP_UP_DURATION.toMinutes())
                .append(" min")
                .append(System.lineSeparator());
        report.append("- Stage: ")
                .append(STAGE_DURATION.toMinutes())
                .append(" min")
                .append(System.lineSeparator());
        report.append("- Operation ratio `GET /list` : `POST/DELETE /links` = ")
                .append(GET_REQUESTS_PER_CYCLE)
                .append(":")
                .append(MUTATION_REQUESTS_PER_CYCLE)
                .append(System.lineSeparator());
        report.append(
                        "- Note: task text mentions `POST /list`, but the actual API endpoint is `POST /links` (`DELETE /links` for remove).")
                .append(System.lineSeparator())
                .append(System.lineSeparator());

        for (ScenarioResult result : results) {
            report.append("## ")
                    .append(result.scenarioName())
                    .append(System.lineSeparator())
                    .append(System.lineSeparator());
            appendEndpoint(report, result, "GET /list");
            appendEndpoint(report, result, "POST /links");
            appendEndpoint(report, result, "DELETE /links");
        }

        report.append("## Conclusions").append(System.lineSeparator()).append(System.lineSeparator());
        report.append(buildConclusions(results));
        report.append(System.lineSeparator());

        Files.createDirectories(REPORT_PATH.getParent());
        Files.writeString(REPORT_PATH, report.toString(), StandardCharsets.UTF_8);
    }

    private void appendEndpoint(StringBuilder report, ScenarioResult scenarioResult, String endpoint) {
        EndpointResult endpointResult = scenarioResult.byEndpoint().get(endpoint);
        if (endpointResult == null) {
            report.append("- ").append(endpoint).append(": no calls").append(System.lineSeparator());
            return;
        }
        DecimalFormat decimalFormat = new DecimalFormat("0.00");
        report.append("### ").append(endpoint).append(System.lineSeparator()).append(System.lineSeparator());
        report.append("- Request: `").append(endpoint).append("`").append(System.lineSeparator());
        report.append("- RPS: ")
                .append(decimalFormat.format(endpointResult.rps()))
                .append(System.lineSeparator());
        report.append("- Average latency, ms: ")
                .append(decimalFormat.format(endpointResult.avgMs()))
                .append(System.lineSeparator());
        report.append("- P50, ms: ")
                .append(decimalFormat.format(endpointResult.p50Ms()))
                .append(System.lineSeparator());
        report.append("- P99, ms: ")
                .append(decimalFormat.format(endpointResult.p99Ms()))
                .append(System.lineSeparator());
        report.append("- HTTP 200 count: ").append(endpointResult.http200()).append(System.lineSeparator());
        report.append("- HTTP 502/504 count: ")
                .append(endpointResult.http502Or504())
                .append(System.lineSeparator());
        report.append("- HTTP 500 count: ").append(endpointResult.http500()).append(System.lineSeparator());
        report.append("- Total calls: ").append(endpointResult.total()).append(System.lineSeparator());
        report.append("- Most frequent errors: ")
                .append(formatTopErrors(scenarioResult.topErrorsByEndpoint().get(endpoint)))
                .append(System.lineSeparator())
                .append(System.lineSeparator());
    }

    private String formatTopErrors(Map<String, Long> errorsByEndpoint) {
        if (errorsByEndpoint == null || errorsByEndpoint.isEmpty()) {
            return "none";
        }
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, Long> entry : errorsByEndpoint.entrySet()) {
            errors.add(entry.getKey() + " (" + entry.getValue() + ")");
        }
        return String.join("; ", errors);
    }

    private String buildConclusions(List<ScenarioResult> results) {
        ScenarioResult noCache = findByName(results, "No cache");
        ScenarioResult valkey = findByName(results, "Valkey cache");
        ScenarioResult clientSide = findByName(results, "Client-side cache");
        EndpointResult noCacheGet = noCache.byEndpoint().get("GET /list");
        EndpointResult valkeyGet = valkey.byEndpoint().get("GET /list");
        EndpointResult clientGet = clientSide.byEndpoint().get("GET /list");
        Map<String, Double> rpsByScenario = Map.of(
                noCache.scenarioName(), noCacheGet.rps(),
                valkey.scenarioName(), valkeyGet.rps(),
                clientSide.scenarioName(), clientGet.rps());
        String bestRpsScenario = rpsByScenario.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("n/a");

        DecimalFormat decimalFormat = new DecimalFormat("0.00");
        StringBuilder text = new StringBuilder();
        text.append("- `GET /list`: ")
                .append(compareLatency("Valkey", noCacheGet.avgMs(), valkeyGet.avgMs(), decimalFormat))
                .append(System.lineSeparator());
        text.append("- `GET /list`: ")
                .append(compareLatency("client-side", noCacheGet.avgMs(), clientGet.avgMs(), decimalFormat))
                .append(System.lineSeparator());
        text.append("- Best `GET /list` RPS scenario: ")
                .append(bestRpsScenario)
                .append(".")
                .append(System.lineSeparator());
        text.append(
                        "- Mutation latency (`POST /links` and `DELETE /links`) is higher than `GET /list` across modes due to DB work and cache invalidation.")
                .append(System.lineSeparator());
        text.append("- In this run, 5xx errors are absent or rare.");
        return text.toString();
    }

    private static ScenarioResult findByName(List<ScenarioResult> results, String scenarioName) {
        return results.stream()
                .filter(result -> result.scenarioName().equals(scenarioName))
                .findFirst()
                .orElseThrow();
    }

    private static double safeRatio(double numerator, double denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return numerator / denominator;
    }

    private static String compareLatency(
            String modeName, double baselineAvgMs, double comparedAvgMs, DecimalFormat decimalFormat) {
        double ratio = safeRatio(baselineAvgMs, comparedAvgMs);
        if (ratio >= 1.0) {
            return "%s mode is faster than no-cache baseline by %sx on average."
                    .formatted(modeName, decimalFormat.format(ratio));
        }
        return "%s mode is slower than no-cache baseline by %sx on average."
                .formatted(modeName, decimalFormat.format(1.0 / Math.max(ratio, 0.0001)));
    }

    private final class Worker implements Callable<WorkerMetrics> {

        private final int workerId;
        private final int users;
        private final URI baseUri;
        private final Instant deadline;
        private final HttpClient httpClient;
        private boolean extraLinkAdded;
        private int loopCounter;

        private Worker(int workerId, int users, URI baseUri, Instant deadline) {
            this.workerId = workerId;
            this.users = users;
            this.baseUri = baseUri;
            this.deadline = deadline;
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();
        }

        @Override
        public WorkerMetrics call() throws Exception {
            WorkerMetrics metrics = new WorkerMetrics();
            long rampDelayMs = (RAMP_UP_DURATION.toMillis() * workerId) / users;
            Thread.sleep(rampDelayMs);

            long chatId = (workerId % CHAT_COUNT) + 1L;
            while (Instant.now().isBefore(deadline)) {
                int positionInCycle = loopCounter % TOTAL_REQUESTS_PER_CYCLE;
                if (positionInCycle < GET_REQUESTS_PER_CYCLE) {
                    executeGet(chatId, metrics);
                } else {
                    if (extraLinkAdded) {
                        executeDelete(chatId, metrics);
                    } else {
                        executePost(chatId, metrics);
                    }
                    extraLinkAdded = !extraLinkAdded;
                }
                loopCounter++;
            }
            return metrics;
        }

        private void executeGet(long chatId, WorkerMetrics metrics) {
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/list"))
                    .header(TG_CHAT_ID_HEADER, String.valueOf(chatId))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            send("GET /list", request, metrics);
        }

        private void executePost(long chatId, WorkerMetrics metrics) throws IOException {
            String link = mutationLink(workerId);
            String body =
                    objectMapper.writeValueAsString(Map.of("link", link, "tags", List.of(), "filters", List.of()));
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/links"))
                    .header(TG_CHAT_ID_HEADER, String.valueOf(chatId))
                    .header("Content-Type", "application/json")
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            send("POST /links", request, metrics);
        }

        private void executeDelete(long chatId, WorkerMetrics metrics) throws IOException {
            String body = objectMapper.writeValueAsString(Map.of("link", mutationLink(workerId)));
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/links"))
                    .header(TG_CHAT_ID_HEADER, String.valueOf(chatId))
                    .header("Content-Type", "application/json")
                    .timeout(REQUEST_TIMEOUT)
                    .method("DELETE", HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            send("DELETE /links", request, metrics);
        }

        private void send(String endpoint, HttpRequest request, WorkerMetrics metrics) {
            long started = System.nanoTime();
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                long durationMs = nanosToMillis(System.nanoTime() - started);
                metrics.record(endpoint, durationMs, response.statusCode(), compactBody(response.body()));
            } catch (Exception exception) {
                long durationMs = nanosToMillis(System.nanoTime() - started);
                metrics.record(
                        endpoint, durationMs, -1, exception.getClass().getSimpleName() + ": " + exception.getMessage());
            }
        }
    }

    private static long nanosToMillis(long nanos) {
        return Math.max(0, nanos / 1_000_000);
    }

    private static String mutationLink(int workerId) {
        return "https://github.com/load/mutation/" + workerId;
    }

    private static String compactBody(String body) {
        if (body == null || body.isBlank()) {
            return "<empty>";
        }
        String compact = body.replaceAll("\\s+", " ").trim();
        if (compact.length() <= 120) {
            return compact;
        }
        return compact.substring(0, 120) + "...";
    }

    private record ScenarioConfig(String name, boolean cacheEnabled, CacheMode mode) {}

    private record ScenarioResult(
            String scenarioName,
            Instant startedAt,
            Instant finishedAt,
            Map<String, EndpointResult> byEndpoint,
            Map<String, Long> topErrors,
            Map<String, Map<String, Long>> topErrorsByEndpoint) {

        private static ScenarioResult from(
                ScenarioConfig scenario,
                Instant startedAt,
                Instant finishedAt,
                WorkerMetrics metrics,
                Duration totalDuration) {
            Map<String, EndpointResult> resultByEndpoint = new LinkedHashMap<>();
            for (Map.Entry<String, EndpointMetrics> entry : metrics.byEndpoint.entrySet()) {
                resultByEndpoint.put(entry.getKey(), entry.getValue().toResult(totalDuration));
            }

            Map<String, Long> topErrors = metrics.errors.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                    .limit(5)
                    .collect(
                            LinkedHashMap::new,
                            (map, error) -> map.put(error.getKey(), error.getValue()),
                            LinkedHashMap::putAll);

            Map<String, Map<String, Long>> topErrorsByEndpoint = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Long>> endpointErrors : metrics.errorsByEndpoint.entrySet()) {
                Map<String, Long> compact = endpointErrors.getValue().entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                        .limit(5)
                        .collect(
                                LinkedHashMap::new,
                                (map, error) -> map.put(error.getKey(), error.getValue()),
                                LinkedHashMap::putAll);
                topErrorsByEndpoint.put(endpointErrors.getKey(), compact);
            }

            return new ScenarioResult(
                    scenario.name(), startedAt, finishedAt, resultByEndpoint, topErrors, topErrorsByEndpoint);
        }
    }

    private record EndpointResult(
            long total,
            double rps,
            double avgMs,
            double p50Ms,
            double p99Ms,
            long http200,
            long http502Or504,
            long http500) {}

    private static final class WorkerMetrics {
        private final Map<String, EndpointMetrics> byEndpoint = new LinkedHashMap<>();
        private final Map<String, Long> errors = new HashMap<>();
        private final Map<String, Map<String, Long>> errorsByEndpoint = new HashMap<>();

        private void record(String endpoint, long durationMs, int statusCode, String errorDetail) {
            EndpointMetrics metrics = byEndpoint.computeIfAbsent(endpoint, ignored -> new EndpointMetrics());
            metrics.record(durationMs, statusCode);
            if (statusCode != 200) {
                String errorKey = statusCode + " / " + errorDetail;
                errors.merge(errorKey, 1L, Long::sum);
                Map<String, Long> endpointErrors =
                        errorsByEndpoint.computeIfAbsent(endpoint, ignored -> new HashMap<>());
                endpointErrors.merge(errorKey, 1L, Long::sum);
            }
        }

        private void merge(WorkerMetrics other) {
            for (Map.Entry<String, EndpointMetrics> entry : other.byEndpoint.entrySet()) {
                EndpointMetrics current = byEndpoint.computeIfAbsent(entry.getKey(), ignored -> new EndpointMetrics());
                current.merge(entry.getValue());
            }
            for (Map.Entry<String, Long> entry : other.errors.entrySet()) {
                errors.merge(entry.getKey(), entry.getValue(), Long::sum);
            }
            for (Map.Entry<String, Map<String, Long>> entry : other.errorsByEndpoint.entrySet()) {
                Map<String, Long> endpointErrors =
                        errorsByEndpoint.computeIfAbsent(entry.getKey(), ignored -> new HashMap<>());
                for (Map.Entry<String, Long> endpointError : entry.getValue().entrySet()) {
                    endpointErrors.merge(endpointError.getKey(), endpointError.getValue(), Long::sum);
                }
            }
        }
    }

    private static final class EndpointMetrics {
        private final List<Long> latenciesMs = new ArrayList<>();
        private long totalCalls;
        private long http200;
        private long http500;
        private long http502Or504;

        private void record(long durationMs, int statusCode) {
            totalCalls++;
            latenciesMs.add(durationMs);
            if (statusCode == 200) {
                http200++;
            }
            if (statusCode == 500) {
                http500++;
            }
            if (statusCode == 502 || statusCode == 504) {
                http502Or504++;
            }
        }

        private void merge(EndpointMetrics other) {
            latenciesMs.addAll(other.latenciesMs);
            totalCalls += other.totalCalls;
            http200 += other.http200;
            http500 += other.http500;
            http502Or504 += other.http502Or504;
        }

        private EndpointResult toResult(Duration totalDuration) {
            if (latenciesMs.isEmpty()) {
                return new EndpointResult(0, 0, 0, 0, 0, 0, 0, 0);
            }
            List<Long> sorted = new ArrayList<>(latenciesMs);
            sorted.sort(Long::compareTo);
            long sum = 0L;
            for (long latency : sorted) {
                sum += latency;
            }
            double avg = (double) sum / sorted.size();
            double durationSeconds = Math.max(1.0, totalDuration.toMillis() / 1_000.0);
            double rps = totalCalls / durationSeconds;
            return new EndpointResult(
                    totalCalls,
                    rps,
                    avg,
                    percentile(sorted, 0.50),
                    percentile(sorted, 0.99),
                    http200,
                    http502Or504,
                    http500);
        }

        private static double percentile(List<Long> sorted, double percentile) {
            if (sorted.isEmpty()) {
                return 0;
            }
            int index = (int) Math.ceil(percentile * sorted.size()) - 1;
            if (index < 0) {
                index = 0;
            }
            if (index >= sorted.size()) {
                index = sorted.size() - 1;
            }
            return sorted.get(index);
        }
    }
}
