package backend.academy.linktracker.scrapper.configuration;

import backend.academy.linktracker.scrapper.properties.KafkaProperties;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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

/**
 * Инициализирует Kafka-инфраструктуру для producer-бинов scrapper-сервиса.
 */
class KafkaProducerInfrastructureInitializer {
    /**
     * Логгер инициализатора инфраструктуры.
     */
    private static final Logger log = LoggerFactory.getLogger(KafkaProducerInfrastructureInitializer.class);
    /**
     * Таймаут инфраструктурных вызовов к Kafka при старте.
     * Ограничивает время fail-fast и не дает запуску зависнуть бесконечно.
     */
    private static final long STARTUP_CHECK_TIMEOUT_SECONDS = 5L;
    /**
     * Фактор репликации топика уведомлений.
     * Нужен для устойчивости сообщений к сбою отдельного брокера.
     */
    private static final short NOTIFICATIONS_TOPIC_REPLICATION_FACTOR = 3;
    /**
     * Количество партиций топика уведомлений.
     * Задает базовый параллелизм обработки без избыточного дробления.
     */
    private static final int NOTIFICATIONS_TOPIC_PARTITIONS = 3;
    /**
     * Минимальное количество ISR для подтверждения записи.
     * Снижает риск потери уже подтвержденных уведомлений.
     */
    private static final String NOTIFICATIONS_TOPIC_MIN_INSYNC_REPLICAS = "2";
    /**
     * Время хранения уведомлений в Kafka (24 часа).
     * Ограничивает рост хранилища при сохранении окна для догоняющих consumer-ов.
     */
    private static final String NOTIFICATIONS_TOPIC_RETENTION_MS = "86400000";
    /**
     * Допустимые типы исключений, означающие, что топик уже существует.
     * Используется для идемпотентного provisioning без ложного падения старта.
     */
    private static final Set<Class<?>> TOPIC_ALREADY_EXISTS_EXCEPTIONS = Set.of(TopicExistsException.class);

    /**
     * Параметры Kafka из конфигурации приложения.
     */
    private final KafkaProperties kafkaProperties;

    /**
     * Создает инициализатор инфраструктуры producer-а.
     *
     * @param kafkaProperties параметры подключения и топика
     */
    KafkaProducerInfrastructureInitializer(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    /**
     * Выполняет инфраструктурную подготовку producer-а.
     * Нужен для fail-fast старта: недоступная Kafka или ошибка provisioning выявляются до обработки трафика.
     *
     * @param infrastructureName имя инициализируемого инфраструктурного элемента
     */
    void initialize(String infrastructureName) {
        validateConnectivity(infrastructureName);
        ensureNotificationTopicExists();
    }

    /**
     * Проверяет доступность Kafka-кластера.
     * Нужен, чтобы предотвратить запуск producer-конфигурации в заведомо неработоспособном окружении.
     *
     * @param infrastructureName имя инициализируемого инфраструктурного элемента
     */
    private void validateConnectivity(String infrastructureName) {
        Map<String, Object> adminConfig = adminConfig("-startup-check");

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
     * Идемпотентно создает топик уведомлений при отсутствии.
     * Нужен, чтобы producer мог публиковать события сразу после старта без ручной подготовки топиков.
     */
    private void ensureNotificationTopicExists() {
        Map<String, Object> adminConfig = adminConfig("-topic-provisioner");

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
     * Определяет, что ошибка создания топика означает "топик уже существует".
     * Нужен, чтобы не считать повторный provisioning фатальной ошибкой.
     *
     * @param exception исходная ошибка Kafka Admin API
     * @return {@code true}, если топик уже создан ранее
     */
    private boolean topicAlreadyExists(Exception exception) {
        if (isTopicAlreadyExistsError(exception)) {
            return true;
        }
        if (exception instanceof ExecutionException executionException) {
            return isTopicAlreadyExistsError(executionException.getCause());
        }
        return isTopicAlreadyExistsError(exception.getCause());
    }

    /**
     * Проверяет принадлежность ошибки к набору "уже существует".
     * Нужен для единообразной обработки как прямых, так и вложенных исключений.
     *
     * @param throwable проверяемая ошибка
     * @return {@code true}, если ошибка сообщает о существующем топике
     */
    private boolean isTopicAlreadyExistsError(Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        return TOPIC_ALREADY_EXISTS_EXCEPTIONS.contains(throwable.getClass());
    }

    /**
     * Формирует конфигурацию AdminClient с нужным client-id суффиксом.
     * Нужен для переиспользования одной схемы настройки в разных инфраструктурных шагах.
     *
     * @param clientIdSuffix суффикс client-id для конкретной операции
     * @return конфигурация для Kafka AdminClient
     */
    private Map<String, Object> adminConfig(String clientIdSuffix) {
        Map<String, Object> adminConfig = new HashMap<>();
        adminConfig.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        adminConfig.put(AdminClientConfig.CLIENT_ID_CONFIG, kafkaProperties.getClientId() + clientIdSuffix);
        return adminConfig;
    }
}
