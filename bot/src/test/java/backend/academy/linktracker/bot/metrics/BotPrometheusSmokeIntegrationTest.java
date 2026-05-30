package backend.academy.linktracker.bot.metrics;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
            "management.server.port=0"
        })
@ActiveProfiles("test")
@EnableWireMock
@Tag("integration")
class BotPrometheusSmokeIntegrationTest {
    private static final Duration HTTP_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration HTTP_REQUEST_TIMEOUT = Duration.ofSeconds(10);

    @LocalServerPort
    private int port;

    @LocalManagementPort
    private int managementPort;

    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(HTTP_CONNECT_TIMEOUT).build();

    @Test
    void givenApplicationStartedWhenPrometheusEndpointRequestedThenContainsCustomBotMetrics()
            throws IOException, InterruptedException {
        stubFor(post(urlMatching("/bot[^/]+/sendMessage"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"ok\":true}")));

        HttpResponse<String> updateResponse = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/updates"))
                        .timeout(HTTP_REQUEST_TIMEOUT)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .POST(HttpRequest.BodyPublishers.ofString("""
                                {
                                  "id": 100,
                                  "url": "https://github.com/org/repo",
                                  "description": "updated",
                                  "tgChatIds": [101]
                                }
                                """))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, updateResponse.statusCode());

        HttpResponse<String> metricsResponse = httpClient.send(
                HttpRequest.newBuilder(URI.create(managementBaseUrl() + "/metrics"))
                        .timeout(HTTP_REQUEST_TIMEOUT)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, metricsResponse.statusCode());
        assertTrue(metricsResponse.body().contains("sent_notification_total"));
        assertTrue(metricsResponse.body().contains("command_duration_ms_total"));
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private String managementBaseUrl() {
        return "http://localhost:" + managementPort;
    }
}
