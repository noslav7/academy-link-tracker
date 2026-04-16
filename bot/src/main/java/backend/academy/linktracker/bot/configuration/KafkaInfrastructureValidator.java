package backend.academy.linktracker.bot.configuration;

import backend.academy.linktracker.bot.properties.KafkaProperties;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
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
}
