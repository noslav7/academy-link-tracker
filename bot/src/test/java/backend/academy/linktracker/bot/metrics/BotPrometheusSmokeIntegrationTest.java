package backend.academy.linktracker.bot.metrics;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.wiremock.spring.EnableWireMock;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "app.telegram.polling-enabled=false",
            "app.telegram.set-my-commands-on-startup=false",
            "management.server.port=0",
            "management.prometheus.metrics.export.pushgateway.enabled=false"
        })
@ActiveProfiles("test")
@EnableWireMock
@Tag("integration")
@Tag("sanity")
class BotPrometheusSmokeIntegrationTest {
    private static final Duration HTTP_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration HTTP_REQUEST_TIMEOUT = Duration.ofSeconds(4);
    private static final String UPDATE_PAYLOAD = """
            {
              "id": 100,
              "url": "https://github.com/org/repo",
              "description": "updated",
              "tgChatIds": [101]
            }
            """;

    @LocalServerPort
    private int port;

    @LocalManagementPort
    private int managementPort;

    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(HTTP_CONNECT_TIMEOUT).build();

    @BeforeEach
    void setUpTelegramStub() {
        stubFor(post(urlMatching("/bot[^/]+/sendMessage"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"ok\":true}")));
    }

    @Test
    // Проверяет успешный прием обновления ботом.
    void givenValidLinkUpdateWhenPostedToUpdatesThenResponseStatusIsOk() throws IOException, InterruptedException {
        HttpResponse<String> updateResponse = postUpdate();
        assertEquals(200, updateResponse.statusCode());
    }

    @Test
    // Проверяет полный эталонный блок sent_notification_total в prometheus-выводе.
    void givenNotificationSentWhenMetricsRequestedThenMetricsContainExactSentNotificationMetricBlock()
            throws IOException, InterruptedException {
        postUpdate();
        HttpResponse<String> metricsResponse = getMetrics();
        String expected = """
                # HELP sent_notification_total Total sent notifications from bot
                # TYPE sent_notification_total counter
                sent_notification_total <value>
                """;
        assertEquals(expected.strip(), normalizeMetricBlock(metricsResponse.body(), "sent_notification_total"));
    }

    private HttpResponse<String> postUpdate() throws IOException, InterruptedException {
        return httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/updates"))
                        .timeout(HTTP_REQUEST_TIMEOUT)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .POST(HttpRequest.BodyPublishers.ofString(UPDATE_PAYLOAD))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getMetrics() throws IOException, InterruptedException {
        return httpClient.send(
                HttpRequest.newBuilder(URI.create(managementBaseUrl() + "/metrics"))
                        .timeout(HTTP_REQUEST_TIMEOUT)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String normalizeMetricBlock(String metricsBody, String metricName) {
        String helpLine = null;
        String typeLine = null;
        String sampleLine = null;
        for (String line : metricsBody.split("\\R")) {
            if (line.startsWith("# HELP " + metricName + " ")) {
                helpLine = line;
            } else if (line.startsWith("# TYPE " + metricName + " ")) {
                typeLine = line;
            } else if (line.startsWith(metricName + " ")) {
                sampleLine = metricName + " <value>";
            }
        }
        return String.join("\n", helpLine, typeLine, sampleLine);
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private String managementBaseUrl() {
        return "http://localhost:" + managementPort;
    }
}
