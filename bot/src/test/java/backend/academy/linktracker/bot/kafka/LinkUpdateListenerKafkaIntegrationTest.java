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
import static org.junit.jupiter.api.Assertions.assertEquals;

import backend.academy.linktracker.bot.command.BotMessages;
import backend.academy.linktracker.bot.properties.KafkaProperties;
import backend.academy.linktracker.bot.properties.TelegramProperties;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.wiremock.spring.EnableWireMock;

@SpringBootTest(
        properties = {
            "spring.main.allow-bean-definition-overriding=true",
            "app.kafka.enabled=true",
            "app.kafka.topic=link-updates-bot-it",
            "app.kafka.group-id=bot-link-update-listener-it-group",
            "app.kafka.client-id=bot-it",
            "app.kafka.dlq-topic=link-updates-bot-it-dlq",
            "app.kafka.retry-attempts=2",
            "app.kafka.retry-backoff-ms=50",
            "app.kafka.schema-registry-url=http://localhost:0/apis/registry/v2"
        })
@ActiveProfiles("test")
@EnableWireMock
@EmbeddedKafka(partitions = 1, topics = "link-updates-bot-it", bootstrapServersProperty = "app.kafka.bootstrap-servers")
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LinkUpdateListenerKafkaIntegrationTest {

    private static final String LINK_UPDATE_EVENT_SCHEMA_RESOURCE = "LinkUpdateEvent.avsc";
    private static final Schema LINK_UPDATE_EVENT_SCHEMA = loadSchema();

    private static final String TOPIC = "link-updates-bot-it";
    private static final String DLQ_TOPIC = "link-updates-bot-it-dlq";
    private static final AtomicInteger DLQ_CONSUMER_ID_SEQUENCE = new AtomicInteger();

    @Autowired
    private TelegramProperties telegramProperties;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

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
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, GenericRecordBinarySerializer.class.getName());
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
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        try (KafkaProducer<String, byte[]> producer = new KafkaProducer<>(config)) {
            producer.send(new ProducerRecord<>(TOPIC, key, payload));
            producer.flush();
        }
    }

    private void verifySendMessage(long chatId) {
        verify(
                1,
                postRequestedFor(urlPathTemplate("/bot{token}/sendMessage"))
                        .withPathParam("token", equalTo(telegramProperties.getToken())));
        assertEquals(String.valueOf(chatId), sentFormData().get("chat_id"));
    }

    private ConsumerRecord<String, String> pollSingleRecord(String topic) {
        Properties config = new Properties();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString());
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

    @TestConfiguration(proxyBeanMethods = false)
    static class KafkaTestConfiguration {

        @Bean
        ConsumerFactory<String, GenericRecord> linkUpdateConsumerFactory(KafkaProperties kafkaProperties) {
            Map<String, Object> config = new HashMap<>();
            config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
            config.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaProperties.getGroupId());
            config.put(ConsumerConfig.CLIENT_ID_CONFIG, kafkaProperties.getClientId());
            config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
            config.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, GenericRecordBinaryDeserializer.class);

            return new DefaultKafkaConsumerFactory<>(config);
        }
    }

    public static class GenericRecordBinarySerializer implements Serializer<GenericRecord> {

        @Override
        public byte[] serialize(String topic, GenericRecord data) {
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                var writer = new GenericDatumWriter<GenericRecord>(LINK_UPDATE_EVENT_SCHEMA);
                var encoder = EncoderFactory.get().binaryEncoder(outputStream, null);
                writer.write(data, encoder);
                encoder.flush();
                return outputStream.toByteArray();
            } catch (IOException exception) {
                throw new SerializationException("Failed to serialize test Avro payload", exception);
            }
        }
    }

    public static class GenericRecordBinaryDeserializer implements Deserializer<GenericRecord> {

        @Override
        public GenericRecord deserialize(String topic, byte[] data) {
            try {
                var reader = new GenericDatumReader<GenericRecord>(LINK_UPDATE_EVENT_SCHEMA);
                var decoder = DecoderFactory.get().binaryDecoder(data, null);
                return reader.read(null, decoder);
            } catch (IOException | RuntimeException exception) {
                throw new SerializationException("Failed to deserialize test Avro payload", exception);
            }
        }
    }
}
