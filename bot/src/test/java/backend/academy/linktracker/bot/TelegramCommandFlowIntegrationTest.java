package backend.academy.linktracker.bot;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathTemplate;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

import backend.academy.linktracker.bot.properties.TelegramProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
    void startCommandFlowSendsWelcomeMessage() {
        long chatId = 1001L;
        stubFlowForSingleUpdate("Start Command Flow", chatId, "/start");

        await().atMost(10, SECONDS).untilAsserted(() -> verifySendMessage(chatId, "Добро пожаловать"));
    }

    @Test
    void helpCommandFlowSendsCommandsDescription() {
        long chatId = 1002L;
        stubFlowForSingleUpdate("Help Command Flow", chatId, "/help");

        await().atMost(10, SECONDS).untilAsserted(() -> verifySendMessage(chatId, "/start"));
    }

    @Test
    void unknownCommandFlowSendsErrorMessage() {
        long chatId = 1003L;
        stubFlowForSingleUpdate("Unknown Command Flow", chatId, "/unsupported");

        await().atMost(10, SECONDS).untilAsserted(() -> verifySendMessage(chatId, "Неизвестная команда"));
    }

    private void stubFlowForSingleUpdate(String scenarioName, long chatId, String command) {
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

    private void verifySendMessage(long chatId, String expectedTextFragment) {
        String encodedText =
                URLEncoder.encode(expectedTextFragment, StandardCharsets.UTF_8).replace("+", "%20");

        verify(postRequestedFor(urlPathTemplate("/bot{token}/sendMessage"))
                .withPathParam("token", equalTo(telegramProperties.getToken()))
                .withRequestBody(containing("chat_id=" + chatId))
                .withRequestBody(containing(encodedText)));
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
