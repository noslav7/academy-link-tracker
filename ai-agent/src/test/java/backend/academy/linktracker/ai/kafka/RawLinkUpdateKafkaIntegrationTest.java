package backend.academy.linktracker.ai.kafka;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import backend.academy.linktracker.ai.dto.ProcessedLinkUpdate;
import backend.academy.linktracker.ai.dto.UpdatePriority;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
        properties = {
            "app.kafka.enabled=true",
            "app.kafka.raw-topic=link.raw-updates",
            "app.kafka.processed-topic=link.processed-updates",
            "app.kafka.group-id=ai-agent-it-group",
            "app.kafka.client-id=ai-agent-it",
            "ai-agent.filtering.stop-words=spam",
            "ai-agent.filtering.excluded-authors=blocked-author",
            "ai-agent.filtering.min-length=5",
            "ai-agent.summarization.threshold=10000",
            "ai-agent.grouping.window-ms=200"
        })
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class RawLinkUpdateKafkaIntegrationTest {

    private static final Logger LISTENER_LOGGER = (Logger) LoggerFactory.getLogger(RawLinkUpdateListener.class);
    private static final String RAW_TOPIC = "link.raw-updates";
    private static final String PROCESSED_TOPIC = "link.processed-updates";
    private static final Duration ASSERT_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration LISTENER_ASSIGNMENT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration CONSUMER_POLL_TIMEOUT = Duration.ofMillis(250);
    private static final int KAFKA_REQUEST_TIMEOUT_MS = 5_000;
    private static final int KAFKA_DELIVERY_TIMEOUT_MS = 6_000;
    private static final int KAFKA_MAX_BLOCK_MS = 6_000;
    private static final int CONSUMER_POLL_ATTEMPTS = 12;
    private static final AtomicInteger CONSUMER_SEQUENCE = new AtomicInteger();
    private static final AtomicBoolean TOPICS_INITIALIZED = new AtomicBoolean();
    private static final AtomicBoolean LISTENER_READY = new AtomicBoolean();
    private static final String MALFORMED_PAYLOAD = "{ this is not a valid json";

    @SuppressWarnings("resource")
    @Container
    private static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.1.1"));

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ListAppender<ILoggingEvent> listenerLogAppender;

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("app.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @BeforeEach
    void setUp() throws ExecutionException, InterruptedException {
        if (TOPICS_INITIALIZED.compareAndSet(false, true)) {
            createTopicIfMissing(RAW_TOPIC);
            createTopicIfMissing(PROCESSED_TOPIC);
        }
        if (LISTENER_READY.compareAndSet(false, true)) {
            waitForKafkaListenerAssignment();
        }
        listenerLogAppender = new ListAppender<>();
        listenerLogAppender.start();
        LISTENER_LOGGER.addAppender(listenerLogAppender);
    }

    @AfterEach
    void tearDown() {
        if (listenerLogAppender != null) {
            LISTENER_LOGGER.detachAppender(listenerLogAppender);
            listenerLogAppender.stop();
        }
    }

    @Test
    void givenValidRawUpdateWhenPublishedThenAiAgentConsumesProcessesAndPublishesToProcessedTopic() throws Exception {
        // TC-1.1: корректное сообщение читается из link.raw-updates, проходит обработку и публикуется без ошибок.
        String key = "tc-1-1-valid";
        String payload = """
                {
                  "id": 101,
                  "description": "Critical release update with enough details",
                  "author": "alice",
                  "tgChatIds": [1001]
                }
                """;

        publishRaw(key, payload);

        await().atMost(ASSERT_TIMEOUT).untilAsserted(() -> {
            String recordPayload = consumeProcessedValueByKey(key);
            assertNotNull(recordPayload);
            ProcessedLinkUpdate processed = objectMapper.readValue(recordPayload, ProcessedLinkUpdate.class);
            assertEquals(
                    new ProcessedLinkUpdate(
                            101L, "Critical release update with enough details", List.of(1001L), UpdatePriority.HIGH),
                    processed);
        });
    }

    @Test
    void givenMalformedRawUpdateWhenPublishedThenListenerSkipsMessage() {
        // Цель: проверить, что невалидный JSON не приводит к публикации в processed topic.
        String malformedKey = "tc-1-2-malformed-skip";
        publishRaw(malformedKey, MALFORMED_PAYLOAD);

        String malformedRecord = consumeProcessedValueByKey(malformedKey);

        assertNull(malformedRecord);
    }

    @Test
    void givenMalformedRawUpdateWhenPublishedThenListenerLogsDeserializationError() {
        // Цель: проверить, что при невалидном JSON listener пишет понятный log об ошибке десериализации.
        String malformedKey = "tc-1-2-malformed-log";
        publishRaw(malformedKey, MALFORMED_PAYLOAD);

        await().atMost(ASSERT_TIMEOUT)
                .untilAsserted(() -> assertEquals(
                        "Failed to deserialize incoming raw update payload: " + MALFORMED_PAYLOAD,
                        firstListenerLogMessage("Failed to deserialize incoming raw update payload: ")));
    }

    @Test
    void givenValidRawUpdateAfterMalformedWhenPublishedThenListenerContinuesProcessing() throws Exception {
        // Цель: проверить, что после ошибочного сообщения listener продолжает обрабатывать валидные.
        publishRaw("tc-1-2-malformed", MALFORMED_PAYLOAD);

        String validKey = "tc-1-2-valid";
        publishRaw(validKey, """
                {
                  "id": 202,
                  "description": "Another useful update after malformed payload",
                  "author": "bob",
                  "tgChatIds": [2001]
                }
                """);

        await().atMost(ASSERT_TIMEOUT).untilAsserted(() -> {
            String recordPayload = consumeProcessedValueByKey(validKey);
            assertNotNull(recordPayload);
            ProcessedLinkUpdate processed = objectMapper.readValue(recordPayload, ProcessedLinkUpdate.class);
            assertEquals(
                    new ProcessedLinkUpdate(
                            202L,
                            "Another useful update after malformed payload",
                            List.of(2001L),
                            UpdatePriority.MEDIUM),
                    processed);
        });
    }

    @Test
    void givenInvalidContractWhenPublishedThenListenerSkipsMessage() {
        // Цель: проверить, что сообщение с нарушением контракта не публикуется в processed topic.
        String key = "tc-1-3-invalid-contract";
        publishRaw(key, """
                {
                  "id": 303,
                  "description": "Valid length but empty recipients list",
                  "author": "charlie",
                  "tgChatIds": []
                }
                """);

        String recordPayload = consumeProcessedValueByKey(key);

        assertNull(recordPayload);
    }

    @Test
    void givenInvalidContractWhenPublishedThenListenerLogsValidationWarning() {
        // Цель: проверить, что при валидационной ошибке listener пишет предупреждение с деталями поля.
        String key = "tc-1-3-invalid-contract-log";
        publishRaw(key, """
                {
                  "id": 304,
                  "description": "Valid length but empty recipients list",
                  "author": "charlie",
                  "tgChatIds": []
                }
                """);

        await().atMost(ASSERT_TIMEOUT).untilAsserted(() -> {
            String validationLog = firstListenerLogMessage("Incoming raw update failed validation: ");
            assertEquals(
                    "Incoming raw update failed validation: [ConstraintViolationImpl{interpolatedMessage='must not be empty', propertyPath=tgChatIds, rootBeanClass=class backend.academy.linktracker.ai.dto.RawLinkUpdate, messageTemplate='{jakarta.validation.constraints.NotEmpty.message}'}]",
                    validationLog);
        });
    }

    @Test
    void givenFilteredByStopWordWhenPublishedThenListenerDoesNotPublishProcessedMessage() {
        // Цель: проверить фильтрацию по стоп-слову до публикации в processed topic.
        String key = "tc-1-4-stopword-filtered";
        publishRaw(key, """
                {
                  "id": 404,
                  "description": "This update contains spam and must be skipped",
                  "author": "dave",
                  "tgChatIds": [4001]
                }
                """);

        String recordPayload = consumeProcessedValueByKey(key);

        assertNull(recordPayload);
    }

    @Test
    void givenNullMessageKeyWhenPublishedThenListenerUsesUpdateIdAsKafkaKey() throws Exception {
        // Цель: проверить fallback ключа Kafka на id обновления при null key.
        String payload = """
                {
                  "id": 505,
                  "description": "Useful update with explicit id fallback key",
                  "author": "erin",
                  "tgChatIds": [5001]
                }
                """;
        publishRaw(null, payload);

        await().atMost(ASSERT_TIMEOUT).untilAsserted(() -> {
            String recordPayload = consumeProcessedValueByKey("505");
            assertNotNull(recordPayload);
            ProcessedLinkUpdate processed = objectMapper.readValue(recordPayload, ProcessedLinkUpdate.class);
            assertEquals(
                    new ProcessedLinkUpdate(
                            505L, "Useful update with explicit id fallback key", List.of(5001L), UpdatePriority.MEDIUM),
                    processed);
        });
    }

    @Test
    void givenEmptyPayloadWhenPublishedThenListenerSkipsMessage() {
        // Цель: проверить, что пустой payload не публикуется в processed topic.
        String key = "tc-sanity-empty-skip";
        publishRaw(key, "   ");

        String recordPayload = consumeProcessedValueByKey(key);

        assertNull(recordPayload);
    }

    @Test
    void givenEmptyPayloadWhenPublishedThenListenerLogsWarning() {
        // Цель: проверить наличие явного warning лога для пустого payload.
        String key = "tc-sanity-empty-log";
        publishRaw(key, "   ");

        await().atMost(ASSERT_TIMEOUT)
                .untilAsserted(() -> assertEquals(
                        "Incoming raw update payload is empty",
                        firstListenerLogMessage("Incoming raw update payload is empty")));
    }

    private void createTopicIfMissing(String topic) throws ExecutionException, InterruptedException {
        Properties config = new Properties();
        config.put("bootstrap.servers", KAFKA.getBootstrapServers());
        try (AdminClient adminClient = AdminClient.create(config)) {
            adminClient
                    .createTopics(List.of(new NewTopic(topic, 1, (short) 1)))
                    .all()
                    .get();
        } catch (ExecutionException exception) {
            if (exception.getCause() == null
                    || !exception.getCause().getClass().getSimpleName().equals("TopicExistsException")) {
                throw exception;
            }
        }
    }

    private void publishRaw(String key, String payload) {
        Properties config = new Properties();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, KAFKA_REQUEST_TIMEOUT_MS);
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, KAFKA_DELIVERY_TIMEOUT_MS);
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, KAFKA_MAX_BLOCK_MS);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(config)) {
            producer.send(new ProducerRecord<>(RAW_TOPIC, key, payload));
            producer.flush();
        }
    }

    private String consumeProcessedValueByKey(String expectedKey) {
        Properties config = new Properties();
        config.putAll(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "ai-agent-it-consumer-" + CONSUMER_SEQUENCE.incrementAndGet(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest",
                ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG,
                KAFKA_REQUEST_TIMEOUT_MS,
                ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG,
                KAFKA_REQUEST_TIMEOUT_MS));

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(PROCESSED_TOPIC));
            for (int attempt = 0; attempt < CONSUMER_POLL_ATTEMPTS; attempt++) {
                for (ConsumerRecord<String, String> record :
                        consumer.poll(CONSUMER_POLL_TIMEOUT).records(PROCESSED_TOPIC)) {
                    if (expectedKey.equals(record.key())) {
                        return record.value();
                    }
                }
            }
        }
        return null;
    }

    private void waitForKafkaListenerAssignment() {
        await().atMost(LISTENER_ASSIGNMENT_TIMEOUT).untilAsserted(() -> {
            List<MessageListenerContainer> containers = kafkaListenerEndpointRegistry.getListenerContainers().stream()
                    .toList();
            int assignedPartitions = containers.stream()
                    .map(MessageListenerContainer::getAssignedPartitions)
                    .mapToInt(partitions -> partitions == null ? 0 : partitions.size())
                    .sum();
            assertAll(() -> assertEquals(1, containers.size()), () -> assertEquals(1, assignedPartitions));
        });
    }

    private String firstListenerLogMessage(String prefix) {
        return listenerLogAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.startsWith(prefix))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No listener log message starts with: " + prefix));
    }
}
