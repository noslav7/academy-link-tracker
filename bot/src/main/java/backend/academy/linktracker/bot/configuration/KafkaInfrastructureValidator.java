package backend.academy.linktracker.bot.configuration;

import backend.academy.linktracker.bot.properties.KafkaProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Проверяет доступность Kafka для инициализации инфраструктурных бинов bot-сервиса.
 */
@Component
final class KafkaInfrastructureValidator {

    /**
     * Логгер компонента.
     */
    private static final Logger log = LoggerFactory.getLogger(KafkaInfrastructureValidator.class);
    /**
     * Таймаут проверки доступности кластера Kafka при инициализации бина.
     */
    private static final long STARTUP_CHECK_TIMEOUT_SECONDS = 5L;
    /**
     * Fallback-значение числа партиций для DLQ, если основной топик еще не описан в кластере.
     */
    private static final int DLQ_FALLBACK_PARTITIONS = 1;
    /**
     * Репликация DLQ по умолчанию 1 для совместимости с single-broker окружениями.
     */
    private static final short DLQ_REPLICATION_FACTOR = 1;

    /**
     * Проверяет доступность Kafka до создания инфраструктурного бина.
     *
     * @param kafkaProperties настройки подключения к Kafka
     * @param infrastructureName имя инфраструктурного бина, для которого идет проверка
     */
    void validateConnectivity(KafkaProperties kafkaProperties, String infrastructureName) {
        Map<String, Object> adminConfig = new HashMap<>();
        adminConfig.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        adminConfig.put(AdminClientConfig.CLIENT_ID_CONFIG, kafkaProperties.getClientId() + "-startup-check");

        try (AdminClient adminClient = AdminClient.create(adminConfig)) {
            int nodesCount = adminClient
                    .describeCluster()
                    .nodes()
                    .get(STARTUP_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .size();

            log.atInfo()
                    .addKeyValue("bootstrapServers", kafkaProperties.getBootstrapServers())
                    .addKeyValue("reachableNodes", nodesCount)
                    .addKeyValue("infrastructure", infrastructureName)
                    .log("Kafka connectivity check passed");
        } catch (Exception exception) {
            log.atWarn()
                    .setCause(exception)
                    .addKeyValue("bootstrapServers", kafkaProperties.getBootstrapServers())
                    .addKeyValue("infrastructure", infrastructureName)
                    .log("Kafka connectivity check failed, continuing without fail-fast startup");
        }
    }

    /**
     * Создает DLQ-топик, если он отсутствует.
     *
     * <p>Число партиций берется из основного топика уведомлений, чтобы избежать ошибок публикации DLT
     * в конкретную партицию. Если основной топик недоступен для описания, используется fallback.
     *
     * @param kafkaProperties настройки подключения к Kafka
     */
    void ensureDeadLetterTopicExists(KafkaProperties kafkaProperties) {
        Map<String, Object> adminConfig = new HashMap<>();
        adminConfig.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        adminConfig.put(AdminClientConfig.CLIENT_ID_CONFIG, kafkaProperties.getClientId() + "-dlq-topic-provisioner");

        try (AdminClient adminClient = AdminClient.create(adminConfig)) {
            int partitions = resolveSourceTopicPartitions(adminClient, kafkaProperties);
            NewTopic deadLetterTopic = new NewTopic(kafkaProperties.getDlqTopic(), partitions, DLQ_REPLICATION_FACTOR);
            CreateTopicsResult createTopicsResult = adminClient.createTopics(List.of(deadLetterTopic));
            createTopicsResult.all().get(STARTUP_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log.atInfo()
                    .addKeyValue("topic", kafkaProperties.getDlqTopic())
                    .addKeyValue("partitions", partitions)
                    .addKeyValue("replicationFactor", DLQ_REPLICATION_FACTOR)
                    .log("Kafka DLQ topic has been created");
        } catch (Exception exception) {
            if (topicAlreadyExists(exception)) {
                log.atInfo()
                        .addKeyValue("topic", kafkaProperties.getDlqTopic())
                        .log("Kafka DLQ topic already exists");
                return;
            }
            log.atWarn()
                    .setCause(exception)
                    .addKeyValue("topic", kafkaProperties.getDlqTopic())
                    .log("Failed to create Kafka DLQ topic, continuing without fail-fast startup");
        }
    }

    private int resolveSourceTopicPartitions(AdminClient adminClient, KafkaProperties kafkaProperties) {
        try {
            int partitions = adminClient
                    .describeTopics(List.of(kafkaProperties.getTopic()))
                    .allTopicNames()
                    .get(STARTUP_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .get(kafkaProperties.getTopic())
                    .partitions()
                    .size();
            return Math.max(partitions, DLQ_FALLBACK_PARTITIONS);
        } catch (Exception exception) {
            log.atWarn()
                    .setCause(exception)
                    .addKeyValue("topic", kafkaProperties.getTopic())
                    .addKeyValue("fallbackPartitions", DLQ_FALLBACK_PARTITIONS)
                    .log("Failed to resolve source topic partitions for DLQ creation");
            return DLQ_FALLBACK_PARTITIONS;
        }
    }

    private boolean topicAlreadyExists(Exception exception) {
        if (exception instanceof TopicExistsException) {
            return true;
        }
        if (exception instanceof ExecutionException executionException) {
            return executionException.getCause() instanceof TopicExistsException;
        }
        return exception.getCause() instanceof TopicExistsException;
    }
}
