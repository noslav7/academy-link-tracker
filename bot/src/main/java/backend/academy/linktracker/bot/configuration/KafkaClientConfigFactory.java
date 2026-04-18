package backend.academy.linktracker.bot.configuration;

import backend.academy.linktracker.bot.properties.KafkaProperties;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;

/**
 * Переиспользуемая фабрика базовых Kafka-конфигов для bot-сервиса.
 */
final class KafkaClientConfigFactory {

    private KafkaClientConfigFactory() {}

    /**
     * Формирует базовый набор настроек Kafka-консьюмера.
     *
     * @param kafkaProperties свойства Kafka
     * @return базовый конфиг консьюмера
     */
    static Map<String, Object> consumerBaseConfig(KafkaProperties kafkaProperties) {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaProperties.getGroupId());
        config.put(ConsumerConfig.CLIENT_ID_CONFIG, kafkaProperties.getClientId());
        return config;
    }

    /**
     * Формирует базовый набор настроек Kafka-продьюсера.
     *
     * @param kafkaProperties свойства Kafka
     * @param clientIdSuffix суффикс к clientId
     * @return базовый конфиг продьюсера
     */
    static Map<String, Object> producerBaseConfig(KafkaProperties kafkaProperties, String clientIdSuffix) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        config.put(ProducerConfig.CLIENT_ID_CONFIG, kafkaProperties.getClientId() + clientIdSuffix);
        return config;
    }

    /**
     * Формирует базовый набор настроек Kafka AdminClient.
     *
     * @param kafkaProperties свойства Kafka
     * @param clientIdSuffix суффикс к clientId
     * @return базовый конфиг admin-клиента
     */
    static Map<String, Object> adminConfig(KafkaProperties kafkaProperties, String clientIdSuffix) {
        Map<String, Object> config = new HashMap<>();
        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        config.put(AdminClientConfig.CLIENT_ID_CONFIG, kafkaProperties.getClientId() + clientIdSuffix);
        return config;
    }
}
