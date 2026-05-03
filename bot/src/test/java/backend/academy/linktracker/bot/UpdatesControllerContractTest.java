package backend.academy.linktracker.bot;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathTemplate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import backend.academy.linktracker.bot.command.BotMessages;
import backend.academy.linktracker.bot.properties.TelegramProperties;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.wiremock.spring.EnableWireMock;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.telegram.polling-enabled=false", "app.telegram.set-my-commands-on-startup=false"})
@ActiveProfiles("test")
@EnableWireMock
@Tag("integration")
class UpdatesControllerContractTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TelegramProperties telegramProperties;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void resetWireMock() {
        WireMock.reset();
    }

    @Test
    void givenValidUpdatePayloadWhenPostUpdatesThenReturnsOk() throws IOException, InterruptedException {
        // Проверяет: валидный payload принимается контроллером и возвращает HTTP 200.
        stubTelegramSendMessage();
        HttpResponse<String> response = postUpdate("""
                {
                  "id": 1,
                  "url": "https://github.com/user/repo",
                  "description": "updated",
                  "tgChatIds": [101, 202]
                }
                """);

        assertEquals(200, response.statusCode());
    }

    @Test
    void givenValidUpdatePayloadWhenPostUpdatesThenSendsMessageToEachChatId() throws IOException, InterruptedException {
        // Проверяет: при валидном payload отправка уходит в Telegram ровно по числу chat id.
        stubTelegramSendMessage();
        postUpdate("""
                {
                  "id": 1,
                  "url": "https://github.com/user/repo",
                  "description": "updated",
                  "tgChatIds": [101, 202]
                }
                """);

        verifySendMessageCount(2);
    }

    @Test
    void givenInvalidUpdatePayloadWhenPostUpdatesThenReturnsBadRequest() throws IOException, InterruptedException {
        // Проверяет: невалидный payload отклоняется контроллером с HTTP 400.
        HttpResponse<String> response = postUpdate("""
                {
                  "id": "wrong-type",
                  "url": 42,
                  "description": {},
                  "tgChatIds": "not-an-array"
                }
                """);

        assertEquals(400, response.statusCode());
    }

    @Test
    void givenInvalidUpdatePayloadWhenPostUpdatesThenTelegramIsNotCalled() throws IOException, InterruptedException {
        // Проверяет: при невалидном payload вызова Telegram API не происходит.
        stubTelegramSendMessage();
        postUpdate("""
                {
                  "id": "wrong-type",
                  "url": 42,
                  "description": {},
                  "tgChatIds": "not-an-array"
                }
                """);

        verifySendMessageCount(0);
    }

    @ParameterizedTest
    @MethodSource("nonBlankDescriptionScenarios")
    void givenNonBlankDescriptionWhenPostUpdatesThenTelegramTextContainsDescriptionAsIs(
            long updateId, long chatId, String description) throws IOException, InterruptedException {
        // Проверяет: непустое описание (включая граничные длины) отправляется в уведомлении без усечения.
        stubTelegramSendMessage();

        postUpdate("""
                {
                  "id": %d,
                  "url": "https://github.com/user/repo",
                  "description": "%s",
                  "tgChatIds": [%d]
                }
                """.formatted(updateId, description, chatId));

        String sentText = extractSingleTelegramMessageText();
        assertEquals(BotMessages.UPDATE_NOTIFICATION + "\n\n" + description, sentText);
    }

    @Test
    void givenBlankDescriptionWhenPostUpdatesThenTelegramTextEqualsBaseNotification()
            throws IOException, InterruptedException {
        // Проверяет: blank description приводит к отправке только базового текста уведомления.
        stubTelegramSendMessage();

        postUpdate("""
                {
                  "id": 5,
                  "url": "https://github.com/user/repo",
                  "description": "   ",
                  "tgChatIds": [606]
                }
                """);

        String sentText = extractSingleTelegramMessageText();
        assertEquals(BotMessages.UPDATE_NOTIFICATION, sentText);
    }

    @Test
    void givenMissingDescriptionWhenPostUpdatesThenTelegramTextEqualsBaseNotification()
            throws IOException, InterruptedException {
        // Проверяет: отсутствие поля description дает только базовый текст уведомления.
        stubTelegramSendMessage();

        postUpdate("""
                {
                  "id": 6,
                  "url": "https://github.com/user/repo",
                  "tgChatIds": [707]
                }
                """);

        String sentText = extractSingleTelegramMessageText();
        assertEquals(BotMessages.UPDATE_NOTIFICATION, sentText);
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

    private HttpResponse<String> postUpdate(String payload) throws IOException, InterruptedException {
        return httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/updates"))
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
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

    private void verifySendMessageCount(int expectedCount) {
        var requestPattern = postRequestedFor(urlPathTemplate("/bot{token}/sendMessage"))
                .withPathParam(
                        "token",
                        com.github.tomakehurst.wiremock.client.WireMock.equalTo(telegramProperties.getToken()));
        assertEquals(expectedCount, findAll(requestPattern).size());
    }

    private static Stream<Arguments> nonBlankDescriptionScenarios() {
        return Stream.of(
                arguments(2L, 303L, "x".repeat(260)),
                arguments(3L, 404L, "y".repeat(200)),
                arguments(4L, 505L, "z".repeat(201)));
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
