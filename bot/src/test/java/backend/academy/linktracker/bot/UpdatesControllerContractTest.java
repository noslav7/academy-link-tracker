package backend.academy.linktracker.bot;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathTemplate;
import static org.junit.jupiter.api.Assertions.assertEquals;

import backend.academy.linktracker.bot.command.BotMessages;
import backend.academy.linktracker.bot.properties.TelegramProperties;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
@Tag("integration")
class UpdatesControllerContractTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TelegramProperties telegramProperties;

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

    @Test
    void givenLongDescriptionWhenPostUpdatesThenTelegramTextContainsPreviewTrimmedTo200Chars()
            throws IOException, InterruptedException {
        // Проверяет, что длинное описание обновления отправляется как превью не длиннее 200 символов.
        stubTelegramSendMessage();
        String longDescription = "x".repeat(260);

        httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/updates"))
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .POST(HttpRequest.BodyPublishers.ofString("""
                                {
                                  "id": 2,
                                  "url": "https://github.com/user/repo",
                                  "description": "%s",
                                  "tgChatIds": [303]
                                }
                                """.formatted(longDescription)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        String sentText = extractSingleTelegramMessageText();
        String expectedPrefix = BotMessages.UPDATE_NOTIFICATION + "\n\nПревью: ";
        assertEquals(expectedPrefix + "x".repeat(200), sentText);
    }

    @Test
    void givenDescriptionLengthExactly200WhenPostUpdatesThenTelegramTextEqualsBaseNotification()
            throws IOException, InterruptedException {
        // Проверяет граничный кейс: при длине description=200 превью не добавляется.
        stubTelegramSendMessage();
        String description = "y".repeat(200);

        httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/updates"))
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .POST(HttpRequest.BodyPublishers.ofString("""
                                {
                                  "id": 3,
                                  "url": "https://github.com/user/repo",
                                  "description": "%s",
                                  "tgChatIds": [404]
                                }
                                """.formatted(description)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        String sentText = extractSingleTelegramMessageText();
        assertEquals(BotMessages.UPDATE_NOTIFICATION, sentText);
    }

    @Test
    void givenDescriptionLength201WhenPostUpdatesThenTelegramTextContainsPreviewOfExactly200Chars()
            throws IOException, InterruptedException {
        // Проверяет граничный кейс: при длине description=201 превью обрезается ровно до 200.
        stubTelegramSendMessage();
        String description = "z".repeat(201);

        httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/updates"))
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .POST(HttpRequest.BodyPublishers.ofString("""
                                {
                                  "id": 4,
                                  "url": "https://github.com/user/repo",
                                  "description": "%s",
                                  "tgChatIds": [505]
                                }
                                """.formatted(description)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        String sentText = extractSingleTelegramMessageText();
        assertEquals(BotMessages.UPDATE_NOTIFICATION + "\n\nПревью: " + "z".repeat(200), sentText);
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

    private String extractSingleTelegramMessageText() {
        var requestPattern = postRequestedFor(urlPathTemplate("/bot{token}/sendMessage"))
                .withPathParam(
                        "token",
                        com.github.tomakehurst.wiremock.client.WireMock.equalTo(telegramProperties.getToken()));
        assertEquals(1, findAll(requestPattern).size());
        return parseFormData(findAll(requestPattern).getFirst().getBodyAsString())
                .get("text");
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
}
