package backend.academy.linktracker.scrapper.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import backend.academy.linktracker.scrapper.ScrapperApplication;
import backend.academy.linktracker.scrapper.TestcontainersConfiguration;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        classes = {ScrapperApplication.class, TestcontainersConfiguration.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "app.scheduler.interval=365d",
            "management.endpoints.web.exposure.include=health,info,prometheus",
            "management.endpoint.prometheus.access=unrestricted",
            "management.endpoints.web.base-path=/",
            "management.endpoints.web.path-mapping.prometheus=metrics",
            "management.prometheus.metrics.export.pushgateway.enabled=false"
        })
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
@Tag("sanity")
class ScrapperPrometheusSmokeIntegrationTest {
    private static final Duration HTTP_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration HTTP_REQUEST_TIMEOUT = Duration.ofSeconds(10);

    @LocalServerPort
    private int port;

    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(HTTP_CONNECT_TIMEOUT).build();

    @Test
    // Проверяет успешную регистрацию чата.
    void givenChatIdWhenPostedToRegisterEndpointThenResponseStatusIsOk() throws IOException, InterruptedException {
        HttpResponse<String> registerChatResponse = registerChat(700001L);
        assertEquals(200, registerChatResponse.statusCode());
    }

    @Test
    // Проверяет полный эталонный блок api_requests_total в prometheus-выводе.
    void givenApiCallPerformedWhenMetricsRequestedThenMetricsContainExactApiRequestsMetricBlock()
            throws IOException, InterruptedException {
        registerAndTrackLink(700002L, "https://github.com/org/repo-700002");
        HttpResponse<String> metricsResponse = getMetrics();
        String expected = """
                # HELP api_requests_total Total number of incoming API requests
                # TYPE api_requests_total counter
                api_requests_total{source="links"} <value>
                api_requests_total{source="tg-chat"} <value>
                """;
        assertEquals(expected.strip(), normalizeMetricBlock(metricsResponse.body(), "api_requests_total"));
    }

    private void registerAndTrackLink(long chatId, String link) throws IOException, InterruptedException {
        registerChat(chatId);
        addLink(chatId, link);
    }

    private HttpResponse<String> registerChat(long chatId) throws IOException, InterruptedException {
        return httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/tg-chat/" + chatId))
                        .timeout(HTTP_REQUEST_TIMEOUT)
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> addLink(long chatId, String link) throws IOException, InterruptedException {
        String payload = """
                {
                  "link": "%s",
                  "tags": [],
                  "filters": []
                }
                """.formatted(link);
        return httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/links"))
                        .timeout(HTTP_REQUEST_TIMEOUT)
                        .header("Tg-Chat-Id", String.valueOf(chatId))
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getMetrics() throws IOException, InterruptedException {
        return httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/metrics"))
                        .timeout(HTTP_REQUEST_TIMEOUT)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String normalizeMetricBlock(String metricsBody, String metricName) {
        String helpLine = null;
        String typeLine = null;
        List<String> sampleLines = new ArrayList<>();
        for (String line : metricsBody.split("\\R")) {
            if (line.startsWith("# HELP " + metricName + " ")) {
                helpLine = line;
            } else if (line.startsWith("# TYPE " + metricName + " ")) {
                typeLine = line;
            } else if (line.startsWith(metricName)) {
                int lastSpace = line.lastIndexOf(' ');
                if (lastSpace > 0) {
                    sampleLines.add(line.substring(0, lastSpace) + " <value>");
                }
            }
        }
        Collections.sort(sampleLines);
        return String.join("\n", helpLine, typeLine, String.join("\n", sampleLines));
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
