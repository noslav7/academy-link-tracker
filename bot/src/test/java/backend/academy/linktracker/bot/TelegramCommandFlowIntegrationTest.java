package backend.academy.linktracker.bot;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathTemplate;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import backend.academy.linktracker.bot.command.BotMessages;
import backend.academy.linktracker.bot.properties.TelegramProperties;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.wiremock.spring.EnableWireMock;

@SpringBootTest(properties = {"app.telegram.polling-enabled=true", "app.telegram.set-my-commands-on-startup=false"})
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@EnableWireMock
class TelegramCommandFlowIntegrationTest {

    @Autowired
    TelegramProperties telegramProperties;

    @Test
    void givenStartCommandUpdateWhenPollingProcessesItThenSendMessageEqualsWelcomeText() {
        // Проверяет end-to-end обработку /start и отправку приветственного ответа.
        long chatId = 1001L;
        stubFlowForSingleUpdate("Start Command Flow", chatId, "/start");

        await().atMost(10, SECONDS).untilAsserted(() -> verifySendMessage(chatId, BotMessages.START_FIRST_TIME));
    }

    @Test
    void givenHelpCommandUpdateWhenPollingProcessesItThenSendMessageEqualsFullHelpText() {
        // Проверяет end-to-end обработку /help и полный текст справки.
        long chatId = 1002L;
        stubFlowForSingleUpdate("Help Command Flow", chatId, "/help");

        String expectedHelpText = """
                Доступные команды:
                /cancel - прервать текущий процесс
                /help - список доступных команд
                /list - показать отслеживаемые ссылки
                /start - начало работы
                /track - начать отслеживание ссылки
                /untrack - прекратить отслеживание ссылки
                """;
        await().atMost(10, SECONDS).untilAsserted(() -> verifySendMessage(chatId, expectedHelpText));
    }

    @Test
    void givenUnsupportedCommandUpdateWhenPollingProcessesItThenSendMessageEqualsUnknownCommandText() {
        // Проверяет end-to-end обработку неподдерживаемой команды.
        long chatId = 1003L;
        stubFlowForSingleUpdate("Unknown Command Flow", chatId, "/unsupported");

        await().atMost(10, SECONDS).untilAsserted(() -> verifySendMessage(chatId, BotMessages.UNKNOWN_COMMAND));
    }

    private void stubFlowForSingleUpdate(String scenarioName, long chatId, String command) {
        stubFor(post(urlMatching("/tg-chat/\\d+"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)));

        stubFor(post(urlMatching("/bot[^/]+/sendMessage"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"ok\":true,\"result\":{\"message_id\":1}}")));

        stubFor(post(urlMatching("/bot[^/]+/getUpdates"))
                .inScenario(scenarioName)
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(telegramUpdate(chatId, command)))
                .willSetStateTo("Update Delivered"));

        stubFor(post(urlMatching("/bot[^/]+/getUpdates"))
                .inScenario(scenarioName)
                .whenScenarioStateIs("Update Delivered")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "ok": true,
                                  "result": []
                                }
                                """)));
    }

    private void verifySendMessage(long chatId, String expectedText) {
        var requestPattern = postRequestedFor(urlPathTemplate("/bot{token}/sendMessage"))
                .withPathParam("token", equalTo(telegramProperties.getToken()));
        verify(1, requestPattern);

        LoggedRequest request = findAll(requestPattern).getFirst();
        Map<String, String> actualFormData = parseFormData(request.getBodyAsString());
        Map<String, String> expectedFormData = Map.of("chat_id", String.valueOf(chatId), "text", expectedText);
        assertEquals(expectedFormData, actualFormData);
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

    private String telegramUpdate(long chatId, String command) {
        return """
                {
                  "ok": true,
                  "result": [
                    {
                      "update_id": 123456,
                      "message": {
                        "message_id": 1,
                        "from": {
                          "id": 987654321,
                          "is_bot": false,
                          "first_name": "Test",
                          "username": "testuser"
                        },
                        "chat": {
                          "id": %d,
                          "type": "private"
                        },
                        "date": 1234567890,
                        "text": "%s"
                      }
                    }
                  ]
                }
                """.formatted(chatId, command);
    }
}
