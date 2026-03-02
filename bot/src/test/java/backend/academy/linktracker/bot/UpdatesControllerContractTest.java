package backend.academy.linktracker.bot;

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
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.wiremock.spring.EnableWireMock;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.telegram.polling-enabled=false", "app.telegram.set-my-commands-on-startup=false"})
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@EnableWireMock
class UpdatesControllerContractTest {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void givenValidUpdatePayloadWhenPostUpdatesThenReturnsOk() throws IOException, InterruptedException {
        // Проверяет, что валидный payload обновления принимается контроллером с HTTP 200.
        stubTelegramSendMessage();

        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/updates"))
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .POST(HttpRequest.BodyPublishers.ofString("""
                                {
                                  "id": 1,
                                  "url": "https://github.com/user/repo",
                                  "description": "updated",
                                  "tgChatIds": [101, 202]
                                }
                                """))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
    }

    @Test
    void givenInvalidUpdatePayloadWhenPostUpdatesThenReturnsBadRequest() throws IOException, InterruptedException {
        // Проверяет, что невалидный payload отклоняется на этапе десериализации с HTTP 400.
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/updates"))
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .POST(HttpRequest.BodyPublishers.ofString("""
                                {
                                  "id": "wrong-type",
                                  "url": 42,
                                  "description": {},
                                  "tgChatIds": "not-an-array"
                                }
                                """))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
    }

    private void stubTelegramSendMessage() {
        stubFor(post(urlMatching("/bot[^/]+/sendMessage"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"ok\":true}")));
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
