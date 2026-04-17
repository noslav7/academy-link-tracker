package backend.academy.linktracker.scrapper.configuration;

import backend.academy.linktracker.scrapper.properties.KafkaProperties;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.errors.TopicExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Проверяет доступность Kafka для инициализации инфраструктурных бинов scrapper-сервиса.
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
     * В кластере 3 брокера, поэтому replication factor = 3:
     * уведомления переживают потерю одного брокера без потери отказоустойчивости.
     */
    private static final short NOTIFICATIONS_TOPIC_REPLICATION_FACTOR = 3;
    /**
     * 3 партиции дают баланс между параллелизмом чтения и операционной простотой;
     * этого достаточно для нескольких экземпляров bot без избыточного дробления потока событий.
     */
    private static final int NOTIFICATIONS_TOPIC_PARTITIONS = 3;
    /**
     * Требуем минимум 2 ISR для подтверждения записи:
     * это согласуется с конфигурацией кластера и снижает риск потери подтвержденных сообщений.
     */
    private static final String NOTIFICATIONS_TOPIC_MIN_INSYNC_REPLICAS = "2";
    /**
     * Нотификации имеют короткий срок полезности, поэтому retention = 24 часа:
     * позволяет догнать отставший consumer и не раздувает storage кластера.
     */
    private static final String NOTIFICATIONS_TOPIC_RETENTION_MS = "86400000";

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
            log.atError()
                    .setCause(exception)
                    .addKeyValue("bootstrapServers", kafkaProperties.getBootstrapServers())
                    .addKeyValue("infrastructure", infrastructureName)
                    .log("Kafka connectivity check failed");
            throw new IllegalStateException(
                    "Failed to initialize Kafka infrastructure: " + infrastructureName, exception);
        }
    }

    /**
     * Создает топик уведомлений, если он отсутствует.
     *
     * @param kafkaProperties настройки подключения к Kafka
     */
    void ensureNotificationTopicExists(KafkaProperties kafkaProperties) {
        Map<String, Object> adminConfig = new HashMap<>();
        adminConfig.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        adminConfig.put(AdminClientConfig.CLIENT_ID_CONFIG, kafkaProperties.getClientId() + "-topic-provisioner");

        NewTopic notificationTopic = new NewTopic(
                        kafkaProperties.getTopic(),
                        NOTIFICATIONS_TOPIC_PARTITIONS,
                        NOTIFICATIONS_TOPIC_REPLICATION_FACTOR)
                .configs(Map.of(
                        TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, NOTIFICATIONS_TOPIC_MIN_INSYNC_REPLICAS,
                        TopicConfig.RETENTION_MS_CONFIG, NOTIFICATIONS_TOPIC_RETENTION_MS));

        try (AdminClient adminClient = AdminClient.create(adminConfig)) {
            CreateTopicsResult createTopicsResult = adminClient.createTopics(java.util.List.of(notificationTopic));
            createTopicsResult.all().get(STARTUP_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log.atInfo()
                    .addKeyValue("bootstrapServers", kafkaProperties.getBootstrapServers())
                    .addKeyValue("topic", kafkaProperties.getTopic())
                    .addKeyValue("partitions", NOTIFICATIONS_TOPIC_PARTITIONS)
                    .addKeyValue("replicationFactor", NOTIFICATIONS_TOPIC_REPLICATION_FACTOR)
                    .log("Kafka notification topic has been created");
        } catch (Exception exception) {
            if (topicAlreadyExists(exception)) {
                log.atInfo()
                        .addKeyValue("topic", kafkaProperties.getTopic())
                        .log("Kafka notification topic already exists");
                return;
            }

            log.atError()
                    .setCause(exception)
                    .addKeyValue("bootstrapServers", kafkaProperties.getBootstrapServers())
                    .addKeyValue("topic", kafkaProperties.getTopic())
                    .log("Failed to create Kafka notification topic");

            throw new IllegalStateException(
                    "Failed to provision Kafka notification topic: " + kafkaProperties.getTopic(), exception);
        }
    }

    /**
     * Проверяет, что ошибка создания топика связана с уже существующим топиком.
     *
     * @param exception исходное исключение
     * @return {@code true}, если топик уже существует
     */
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
