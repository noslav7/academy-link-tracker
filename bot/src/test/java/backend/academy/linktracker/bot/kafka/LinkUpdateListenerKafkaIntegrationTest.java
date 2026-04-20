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
import io.apicurio.registry.serde.avro.AvroKafkaSerdeConfig;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
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
import org.testcontainers.containers.GenericContainer;
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

    private static final String LINK_UPDATE_EVENT_SCHEMA_RESOURCE = "LinkUpdateEvent.avsc";
    private static final Schema LINK_UPDATE_EVENT_SCHEMA = loadSchema();

    private static final String TOPIC = "link-updates-bot-it";
    private static final String DLQ_TOPIC = "link-updates-bot-it-dlq";
    private static final String GROUP_ID = "bot-link-update-listener-it-group";
    private static final AtomicInteger DLQ_CONSUMER_ID_SEQUENCE = new AtomicInteger();

    @SuppressWarnings("resource")
    @Container
    private static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:4.1.1"));

    @SuppressWarnings("resource")
    @Container
    private static final GenericContainer<?> SCHEMA_REGISTRY = new GenericContainer<>(
                    DockerImageName.parse("apicurio/apicurio-registry-mem:2.6.13.Final"))
            .withExposedPorts(8080);

    @Autowired
    private TelegramProperties telegramProperties;

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("app.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("app.kafka.group-id", () -> GROUP_ID);
        registry.add("app.kafka.client-id", () -> "bot-it");
        registry.add("app.kafka.dlq-topic", () -> DLQ_TOPIC);
        registry.add("app.kafka.retry-attempts", () -> 2);
        registry.add("app.kafka.retry-backoff-ms", () -> 50L);
        registry.add("app.kafka.schema-registry-url", LinkUpdateListenerKafkaIntegrationTest::schemaRegistryUrl);
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
        // Проверяет: валидное Avro-сообщение приводит к отправке корректного уведомления в Telegram.
        publishAvro("15", linkUpdateEvent(15L, "https://example.com/news", "New post detected", List.of(1001L)));

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
        // Проверяет: Avro-сообщение scrapper-формата проходит путь Kafka -> bot -> Telegram.
        publishAvro(
                "18", linkUpdateEvent(18L, "https://github.com/octocat/hello-world", "Issue created", List.of(2002L)));

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
        // Проверяет: невалидный бинарный payload не вызывает Telegram API и публикуется в DLQ.
        publishMalformedBytes("malformed", new byte[] {1, 2, 3, 4, 5});

        await().during(1, SECONDS)
                .atMost(2, SECONDS)
                .untilAsserted(() -> verify(
                        0,
                        postRequestedFor(urlPathTemplate("/bot{token}/sendMessage"))
                                .withPathParam("token", equalTo(telegramProperties.getToken()))));

        await().atMost(10, SECONDS).untilAsserted(() -> {
            ConsumerRecord<String, String> record = pollSingleRecord(DLQ_TOPIC);
            assertEquals("malformed", record.key());
        });
    }

    private void publishAvro(String key, GenericRecord event) {
        Properties config = new Properties();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, AvroKafkaSerializer.class.getName());
        config.put("apicurio.registry.url", schemaRegistryUrl());
        config.put("apicurio.registry.auto-register", true);
        config.put("apicurio.registry.find-latest", true);
        config.put(AvroKafkaSerdeConfig.AVRO_ENCODING, AvroKafkaSerdeConfig.AVRO_ENCODING_BINARY);
        try (KafkaProducer<String, GenericRecord> producer = new KafkaProducer<>(config)) {
            producer.send(new ProducerRecord<>(TOPIC, key, event));
            producer.flush();
        }
    }

    private static GenericRecord linkUpdateEvent(long id, String url, String description, List<Long> tgChatIds) {
        GenericRecord event = new GenericData.Record(LINK_UPDATE_EVENT_SCHEMA);
        event.put("id", id);
        event.put("url", url);
        event.put("description", description);
        event.put("tgChatIds", tgChatIds);
        return event;
    }

    private static Schema loadSchema() {
        ClassLoader classLoader = LinkUpdateListenerKafkaIntegrationTest.class.getClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(LINK_UPDATE_EVENT_SCHEMA_RESOURCE)) {
            if (inputStream == null) {
                throw new IllegalStateException("Avro schema resource not found: " + LINK_UPDATE_EVENT_SCHEMA_RESOURCE);
            }
            return new Schema.Parser().parse(inputStream);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to load Avro schema resource: " + LINK_UPDATE_EVENT_SCHEMA_RESOURCE, exception);
        }
    }

    private void publishMalformedBytes(String key, byte[] payload) {
        Properties config = new Properties();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        try (KafkaProducer<String, byte[]> producer = new KafkaProducer<>(config)) {
            producer.send(new ProducerRecord<>(TOPIC, key, payload));
            producer.flush();
        }
    }

    private static String schemaRegistryUrl() {
        return "http://%s:%d/apis/registry/v2"
                .formatted(SCHEMA_REGISTRY.getHost(), SCHEMA_REGISTRY.getMappedPort(8080));
    }

    private void verifySendMessage(long chatId) {
        assertAll(
                () -> verify(
                        1,
                        postRequestedFor(urlPathTemplate("/bot{token}/sendMessage"))
                                .withPathParam("token", equalTo(telegramProperties.getToken()))),
                () -> assertEquals(String.valueOf(chatId), sentFormData().get("chat_id")));
    }

    private ConsumerRecord<String, String> pollSingleRecord(String topic) {
        Properties config = new Properties();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "bot-it-dlq-check-" + DLQ_CONSUMER_ID_SEQUENCE.incrementAndGet());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(topic));
            for (ConsumerRecord<String, String> record :
                    consumer.poll(Duration.ofSeconds(2)).records(topic)) {
                return record;
            }
            throw new AssertionError("Expected record in topic " + topic);
        }
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
