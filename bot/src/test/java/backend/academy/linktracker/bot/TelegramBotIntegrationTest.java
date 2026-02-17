package backend.academy.linktracker.bot;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathTemplate;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.academy.linktracker.bot.properties.TelegramProperties;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.GetUpdates;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.wiremock.spring.EnableWireMock;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@EnableWireMock
class TelegramBotIntegrationTest implements WithAssertions {

    @Autowired
    TelegramBot telegramBot;

    @Autowired
    TelegramProperties telegramProperties;

    @AfterEach
    void clearUpdatesListener() {
        telegramBot.removeGetUpdatesListener();
    }

    @Test
    void givenApiReturnsNotFoundWhenExecuteGetUpdatesThenResponseContainsNotFoundError() {
        // Проверяет, что при ответе Telegram API 404 клиент возвращает ошибочный результат.
        stubFor(post(urlMatching("/bot[^/]+/getUpdates"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withBody("{\"ok\":false,\"error_code\":404,\"description\":\"Not Found\"}")));

        var getUpdatesRequest = new GetUpdates();
        var getUpdatesResponse = telegramBot.execute(getUpdatesRequest);

        assertAll(
                () -> assertFalse(getUpdatesResponse.isOk()),
                () -> assertEquals(Integer.valueOf(404), getUpdatesResponse.errorCode()));

        verify(
                1,
                postRequestedFor(urlPathTemplate("/bot{token}/getUpdates"))
                        .withPathParam("token", equalTo(telegramProperties.getToken())));
    }

    @Test
    void givenUpdatesListenerAndSingleUpdateWhenPollingRunsThenListenerReceivesExpectedUpdatePayload()
            throws InterruptedException {
        // Проверяет, что listener получает апдейт с ожидаемыми полями сообщения.
        stubFor(post(urlMatching("/bot[^/]+/getUpdates"))
                .inScenario("Updates Listener")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
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
                                          "id": 987654321,
                                          "type": "private"
                                        },
                                        "date": 1234567890,
                                        "text": "Hello Bot"
                                      }
                                    }
                                  ]
                                }
                                """))
                .willSetStateTo("Updates Received"));

        stubFor(post(urlMatching("/bot[^/]+/getUpdates"))
                .inScenario("Updates Listener")
                .whenScenarioStateIs("Updates Received")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "ok": true,
                                  "result": []
                                }
                                """)));

        List<Update> receivedUpdates = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        telegramBot.setUpdatesListener(updates -> {
            receivedUpdates.addAll(updates);
            latch.countDown();
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });

        boolean received = latch.await(10, TimeUnit.SECONDS);

        assertAll(() -> assertTrue(received), () -> assertThat(receivedUpdates)
                .hasSize(1)
                .first()
                .returns(Integer.valueOf(123456), Update::updateId)
                .extracting(Update::message)
                .returns("Hello Bot", Message::text)
                .extracting(Message::from)
                .returns("testuser", User::username));

        verify(postRequestedFor(urlPathTemplate("/bot{token}/getUpdates"))
                .withPathParam("token", equalTo(telegramProperties.getToken())));
    }
}
