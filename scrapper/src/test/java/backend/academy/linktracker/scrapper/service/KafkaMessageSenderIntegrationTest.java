package backend.academy.linktracker.scrapper.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import backend.academy.linktracker.scrapper.dto.LinkUpdate;
import backend.academy.linktracker.scrapper.properties.KafkaProperties;
import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import io.apicurio.registry.serde.avro.AvroKafkaSerdeConfig;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.avro.generic.GenericRecord;
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
import org.testcontainers.containers.GenericContainer;
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
    private static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.1.1"))
            .withStartupTimeout(Duration.ofMinutes(3));

    @SuppressWarnings("resource")
    @Container
    private static final GenericContainer<?> SCHEMA_REGISTRY = new GenericContainer<>(
                    DockerImageName.parse("apicurio/apicurio-registry-mem:2.6.13.Final"))
            .withExposedPorts(8080);

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
    void GivenKafkaTransportWhenMessageSenderSendsThenMessageAppearsInExpectedAvroFormat() throws Exception {
        // Проверяет: KafkaMessageSender публикует Avro-событие с ожидаемыми полями.
        createTopicIfMissing();
        NotificationDispatcher sender = createKafkaMessageSender();
        LinkUpdate update = new LinkUpdate(
                77L, "https://github.com/octocat/hello-world", "Repository updated", List.of(501L, 777L));

        sender.dispatch(update);

        ConsumerRecord<String, GenericRecord> record = pollSingleRecord();
        Map<String, Object> actualMessage = Map.of(
                "key", record.key(),
                "url", String.valueOf(record.value().get("url")),
                "description", String.valueOf(record.value().get("description")),
                "tgChatIds", record.value().get("tgChatIds"));
        Map<String, Object> expectedMessage = Map.of(
                "key", "77",
                "url", "https://github.com/octocat/hello-world",
                "description", "Repository updated",
                "tgChatIds", List.of(501L, 777L));
        assertEquals(expectedMessage, actualMessage);
    }

    private NotificationDispatcher createKafkaMessageSender() throws Exception {
        return (NotificationDispatcher) Class.forName("backend.academy.linktracker.scrapper.service.KafkaMessageSender")
                .getConstructor(KafkaTemplate.class, KafkaProperties.class)
                .newInstance(kafkaTemplate(), kafkaProperties());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private KafkaTemplate kafkaTemplate() {
        ProducerFactory producerFactory = new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                AvroKafkaSerializer.class,
                "apicurio.registry.url",
                schemaRegistryUrl(),
                "apicurio.registry.auto-register",
                true,
                "apicurio.registry.find-latest",
                true,
                AvroKafkaSerdeConfig.AVRO_ENCODING,
                AvroKafkaSerdeConfig.AVRO_ENCODING_BINARY));
        return new KafkaTemplate<>(producerFactory);
    }

    private KafkaProperties kafkaProperties() {
        KafkaProperties properties = new KafkaProperties();
        properties.setBootstrapServers(KAFKA.getBootstrapServers());
        properties.setTopic(TOPIC);
        properties.setClientId("scrapper-it");
        properties.setSchemaRegistryUrl(schemaRegistryUrl());
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

    private ConsumerRecord<String, GenericRecord> pollSingleRecord() {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                GROUP_ID,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest",
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                AvroKafkaDeserializer.class,
                "apicurio.registry.url",
                schemaRegistryUrl(),
                "apicurio.registry.find-latest",
                true);
        try (KafkaConsumer<String, GenericRecord> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(TOPIC));
            int maxPollAttempts = 50;
            for (int pollAttempt = 0; pollAttempt < maxPollAttempts; pollAttempt++) {
                var records = consumer.poll(Duration.ofMillis(300));
                if (!records.isEmpty()) {
                    return records.iterator().next();
                }
            }
        }
        throw new AssertionError("Kafka message was not consumed in time");
    }

    private static String schemaRegistryUrl() {
        return "http://%s:%d/apis/registry/v2"
                .formatted(SCHEMA_REGISTRY.getHost(), SCHEMA_REGISTRY.getMappedPort(8080));
    }
}
