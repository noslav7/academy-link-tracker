package backend.academy.linktracker.scrapper.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import backend.academy.linktracker.scrapper.dto.LinkUpdate;
import backend.academy.linktracker.scrapper.properties.KafkaProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
class KafkaMessageSenderIntegrationTest {

    private static final String TOPIC = "link-updates-scrapper-it";
    private static final String GROUP_ID = "scrapper-kafka-it-group";

    @SuppressWarnings("resource")
    @Container
    private static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.1.1"));

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterAll
    static void tearDownTopic() {
        try (AdminClient adminClient =
                AdminClient.create(Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            adminClient.deleteTopics(List.of(TOPIC)).all().get();
        } catch (Exception ignored) {
            // topic can be already removed alongside container shutdown
        }
    }

    @Test
    void givenKafkaTransportWhenMessageSenderSendsThenMessageAppearsInExpectedJsonFormat() throws Exception {
        // Проверяет: KafkaMessageSender публикует JSON с ожидаемыми полями и значениями.
        createTopicIfMissing();
        KafkaMessageSender sender = new KafkaMessageSender(kafkaTemplate(), kafkaProperties());
        LinkUpdate update = new LinkUpdate(
                77L, "https://github.com/octocat/hello-world", "Repository updated", List.of(501L, 777L));

        sender.send(update);

        ConsumerRecord<String, String> record = pollSingleRecord();
        JsonNode payload = objectMapper.readTree(record.value());
        Map<String, Object> actualMessage = Map.of(
                "key", record.key(),
                "url", payload.path("url").asText(),
                "description", payload.path("description").asText(),
                "tgChatIds",
                        List.of(
                                payload.path("tgChatIds").get(0).asLong(),
                                payload.path("tgChatIds").get(1).asLong()));
        Map<String, Object> expectedMessage = Map.of(
                "key", "77",
                "url", "https://github.com/octocat/hello-world",
                "description", "Repository updated",
                "tgChatIds", List.of(501L, 777L));
        assertEquals(expectedMessage, actualMessage);
    }

    private KafkaTemplate<String, LinkUpdate> kafkaTemplate() {
        ProducerFactory<String, LinkUpdate> producerFactory = new DefaultKafkaProducerFactory<>(
                Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()),
                new StringSerializer(),
                (topic, data) -> {
                    try {
                        return objectMapper.writeValueAsString(data).getBytes(StandardCharsets.UTF_8);
                    } catch (Exception exception) {
                        throw new IllegalStateException("Unable to serialize test LinkUpdate payload", exception);
                    }
                });
        return new KafkaTemplate<>(producerFactory);
    }

    private KafkaProperties kafkaProperties() {
        KafkaProperties properties = new KafkaProperties();
        properties.setBootstrapServers(KAFKA.getBootstrapServers());
        properties.setTopic(TOPIC);
        properties.setClientId("scrapper-it");
        return properties;
    }

    private void createTopicIfMissing() throws Exception {
        try (AdminClient adminClient =
                AdminClient.create(Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            adminClient
                    .createTopics(List.of(new NewTopic(TOPIC, 1, (short) 1)))
                    .all()
                    .get();
        }
    }

    private ConsumerRecord<String, String> pollSingleRecord() {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                GROUP_ID,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest");
        try (KafkaConsumer<String, String> consumer =
                new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(TOPIC));

            long deadline = System.currentTimeMillis() + Duration.ofSeconds(15).toMillis();
            while (System.currentTimeMillis() < deadline) {
                var records = consumer.poll(Duration.ofMillis(300));
                if (!records.isEmpty()) {
                    return records.iterator().next();
                }
            }
        }
        throw new AssertionError("Kafka message was not consumed in time");
    }
}
