package backend.academy.linktracker.bot;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathTemplate;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import backend.academy.linktracker.bot.command.BotMessages;
import backend.academy.linktracker.bot.properties.TelegramProperties;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.wiremock.spring.EnableWireMock;

@SpringBootTest(properties = {"app.telegram.polling-enabled=true", "app.telegram.set-my-commands-on-startup=false"})
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@EnableWireMock
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class TelegramCommandFlowIntegrationTest {

    private static final String HELP_TEXT = """
            Доступные команды:
            /cancel - прервать текущий процесс
            /help - список доступных команд
            /list - показать отслеживаемые ссылки
            /start - начало работы
            /track - начать отслеживание ссылки
            /untrack - прекратить отслеживание ссылки
            """;

    @Autowired
    TelegramProperties telegramProperties;

    @ParameterizedTest
    @MethodSource("commandScenarios")
    void givenCommandUpdateWhenPollingProcessesItThenSendMessageEqualsExpectedText(
            String scenarioName, long chatId, String command, String expectedText) {
        // Проверяет: для каждой входной команды бот отправляет ожидаемый текст ответа.
        stubFlowForSingleUpdate(scenarioName, chatId, command);
        await().atMost(10, SECONDS).untilAsserted(() -> verifySendMessage(chatId, expectedText));
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
        var requests = findAll(requestPattern);
        assertEquals(1, requests.size());
        LoggedRequest request = requests.getFirst();
        Map<String, String> actualFormData = parseFormData(request.getBodyAsString());
        Map<String, String> expectedFormData = Map.of("chat_id", String.valueOf(chatId), "text", expectedText);
        assertEquals(expectedFormData, actualFormData);
    }

    private static Stream<Arguments> commandScenarios() {
        return Stream.of(
                arguments("Start Command Flow", 1001L, "/start", BotMessages.START_FIRST_TIME),
                arguments("Help Command Flow", 1002L, "/help", HELP_TEXT),
                arguments("Unknown Command Flow", 1003L, "/unsupported", BotMessages.UNKNOWN_COMMAND));
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
