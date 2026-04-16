package backend.academy.linktracker.bot.kafka;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathTemplate;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import backend.academy.linktracker.bot.command.BotMessages;
import backend.academy.linktracker.bot.properties.TelegramProperties;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import org.wiremock.spring.EnableWireMock;

@SpringBootTest(properties = {"app.kafka.topic=link-updates-bot-it"})
@ActiveProfiles("test")
@EnableWireMock
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LinkUpdateListenerKafkaIntegrationTest {

    private static final String TOPIC = "link-updates-bot-it";
    private static final String GROUP_ID = "bot-link-update-listener-it-group";

    @SuppressWarnings("resource")
    @Container
    private static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.1.1"));

    @Autowired
    private TelegramProperties telegramProperties;

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("app.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("app.kafka.group-id", () -> GROUP_ID);
        registry.add("app.kafka.client-id", () -> "bot-it");
    }

    @BeforeEach
    void setUp() {
        WireMock.reset();
        stubFor(post(urlMatching("/bot[^/]+/sendMessage"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"ok\":true,\"result\":{\"message_id\":1}}")));
    }

    @Test
    @Order(1)
    void givenValidKafkaPayloadWhenConsumedThenBotSendsNotificationToTelegram() {
        // Проверяет: валидное Kafka-сообщение приводит к отправке корректного уведомления в Telegram.
        String payload = """
                {
                  "id": 15,
                  "url": "https://example.com/news",
                  "description": "New post detected",
                  "tgChatIds": [1001]
                }
                """;

        publishRawJson("15", payload);

        await().atMost(20, SECONDS).untilAsserted(() -> {
            verifySendMessage(1001L);
            assertEquals(
                    BotMessages.UPDATE_NOTIFICATION + "\n\nNew post detected",
                    sentFormData().get("text"));
        });
    }

    @Test
    @Order(2)
    void givenScrapperSendsKafkaMessageWhenBotConsumesThenEndToEndPathWorks() {
        // Проверяет: сообщение scrapper-формата проходит полный путь Kafka -> bot -> Telegram.
        publishRawJson("18", """
                {
                  "id": 18,
                  "url": "https://github.com/octocat/hello-world",
                  "description": "Issue created",
                  "tgChatIds": [2002]
                }
                """);

        await().atMost(20, SECONDS).untilAsserted(() -> {
            verifySendMessage(2002L);
            assertEquals(
                    BotMessages.UPDATE_NOTIFICATION + "\n\nIssue created",
                    sentFormData().get("text"));
        });
    }

    @Test
    @Order(3)
    void givenMalformedKafkaPayloadWhenConsumedThenBotSkipsTelegramNotification() {
        // Проверяет: невалидный JSON из Kafka не должен приводить к вызову Telegram API.
        publishRawJson("malformed", "{\"id\": \"wrong\", \"url\": ");

        await().during(1, SECONDS)
                .atMost(2, SECONDS)
                .untilAsserted(() -> verify(
                        0,
                        postRequestedFor(urlPathTemplate("/bot{token}/sendMessage"))
                                .withPathParam("token", equalTo(telegramProperties.getToken()))));
    }

    private void publishRawJson(String key, String json) {
        Properties config = new Properties();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(config)) {
            producer.send(new ProducerRecord<>(TOPIC, key, json));
            producer.flush();
        }
    }

    private void verifySendMessage(long chatId) {
        assertAll(
                () -> verify(
                        1,
                        postRequestedFor(urlPathTemplate("/bot{token}/sendMessage"))
                                .withPathParam("token", equalTo(telegramProperties.getToken()))),
                () -> assertEquals(String.valueOf(chatId), sentFormData().get("chat_id")));
    }

    private Map<String, String> sentFormData() {
        var requestPattern = postRequestedFor(urlPathTemplate("/bot{token}/sendMessage"))
                .withPathParam("token", equalTo(telegramProperties.getToken()));
        LoggedRequest request = findAll(requestPattern).getFirst();
        return parseFormData(request.getBodyAsString());
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
