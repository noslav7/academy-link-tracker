package backend.academy.linktracker.scrapper.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.academy.linktracker.scrapper.ScrapperApplication;
import backend.academy.linktracker.scrapper.TestcontainersConfiguration;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        classes = {ScrapperApplication.class, TestcontainersConfiguration.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "app.scheduler.interval=365d",
            "management.endpoints.web.exposure.include=health,info,prometheus",
            "management.endpoint.prometheus.access=unrestricted",
            "management.endpoints.web.base-path=/",
            "management.endpoints.web.path-mapping.prometheus=metrics"
        })
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
class ScrapperPrometheusSmokeIntegrationTest {
    private static final Duration HTTP_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration HTTP_REQUEST_TIMEOUT = Duration.ofSeconds(10);

    @LocalServerPort
    private int port;

    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(HTTP_CONNECT_TIMEOUT).build();

    @Test
    void givenApplicationStartedWhenPrometheusEndpointRequestedThenContainsCustomScrapperMetrics()
            throws IOException, InterruptedException {
        HttpResponse<String> registerChatResponse = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/tg-chat/700001"))
                        .timeout(HTTP_REQUEST_TIMEOUT)
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, registerChatResponse.statusCode());

        HttpResponse<String> metricsResponse = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/metrics"))
                        .timeout(HTTP_REQUEST_TIMEOUT)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, metricsResponse.statusCode());
        assertTrue(metricsResponse.body().contains("api_requests_total"));
        assertTrue(metricsResponse.body().contains("request_duration_ms_total"));
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
