package backend.academy.linktracker.scrapper;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
            "app.rate-limiting.enabled=true",
            "app.rate-limiting.limit-for-period=2",
            "app.rate-limiting.limit-refresh-period=1h",
            "app.rate-limiting.timeout-duration=0s"
        })
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
class RateLimitingHttpContractTest {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final Duration HTTP_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration HTTP_REQUEST_TIMEOUT = Duration.ofSeconds(5);

    @LocalServerPort
    private int port;

    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(HTTP_CONNECT_TIMEOUT).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void givenRequestsFromSameIpWhenLimitExceededThenReturnsTooManyRequestsStatus()
            throws IOException, InterruptedException {
        // Проверяет статусный контракт: после исчерпания квоты сервис отвечает 429.
        String clientIp = "198.51.100.101";
        HttpResponse<String> first = postWithoutBody("/tg-chat/5001", clientIp);
        HttpResponse<String> second = postWithoutBody("/tg-chat/5002", clientIp);
        HttpResponse<String> limited = postWithoutBody("/tg-chat/5003", clientIp);

        assertAll(
                () -> assertEquals(200, first.statusCode()),
                () -> assertEquals(200, second.statusCode()),
                () -> assertEquals(429, limited.statusCode()));
    }

    @Test
    void givenRequestsFromSameIpWhenLimitExceededThenReturnsTooManyRequestsPayload()
            throws IOException, InterruptedException {
        // Проверяет контракт тела ошибки 429 для исчерпанной квоты по IP.
        String clientIp = "198.51.100.102";
        postWithoutBody("/tg-chat/5021", clientIp);
        postWithoutBody("/tg-chat/5022", clientIp);
        HttpResponse<String> limited = postWithoutBody("/tg-chat/5023", clientIp);

        assertEquals(429, limited.statusCode());
        assertRateLimitError(limited.body(), clientIp);
    }

    @Test
    void givenDifferentForwardedIpsWhenOneIpExhaustedThenOtherIpIsStillAllowed()
            throws IOException, InterruptedException {
        // Проверяет изоляцию квот: лимит одного IP не должен блокировать другой IP.
        assertEquals(200, postWithoutBody("/tg-chat/5011", "203.0.113.10").statusCode());
        assertEquals(200, postWithoutBody("/tg-chat/5012", "203.0.113.10").statusCode());
        assertEquals(429, postWithoutBody("/tg-chat/5013", "203.0.113.10").statusCode());

        HttpResponse<String> secondIpFirst = postWithoutBody("/tg-chat/5014", "203.0.113.11");

        assertEquals(200, secondIpFirst.statusCode());
    }

    private void assertRateLimitError(String body, String expectedClientIp) throws IOException {
        JsonNode error = objectMapper.readTree(body);
        assertAll(
                () -> assertEquals("429", error.path("code").asText()),
                () -> assertEquals(
                        "RateLimitExceededException",
                        error.path("exceptionName").asText()),
                () -> assertEquals(
                        "Rate limit exceeded for IP: " + expectedClientIp,
                        error.path("exceptionMessage").asText()),
                () -> assertTrue(error.path("stacktrace").isArray()),
                () -> assertNotEquals(0, error.path("stacktrace").size()));
    }

    private HttpResponse<String> postWithoutBody(String path, String forwardedForHeader)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .timeout(HTTP_REQUEST_TIMEOUT)
                .header(FORWARDED_FOR_HEADER, forwardedForHeader)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
