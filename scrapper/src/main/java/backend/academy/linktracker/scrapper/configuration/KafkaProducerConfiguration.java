package backend.academy.linktracker.scrapper.configuration;

import backend.academy.linktracker.scrapper.dto.LinkUpdate;
import backend.academy.linktracker.scrapper.properties.KafkaProperties;
import backend.academy.linktracker.scrapper.properties.TransportMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Конфигурация Kafka-публикатора уведомлений scrapper-сервиса.
 */
@Configuration
@EnableKafka
@ConditionalOnProperty(
        prefix = "app.bot",
        name = "transport",
        havingValue = TransportMode.KAFKA_VALUE,
        matchIfMissing = true)
public class KafkaProducerConfiguration {

    /**
     * ObjectMapper с автоподключением модулей для сериализации Kafka-пейлоуда.
     */
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    ObjectMapper objectMapper() {
        return JsonMapper.builder().findAndAddModules().build();
    }

    /**
     * Фабрика Kafka-публикатора для LinkUpdate с ручным сериализатором.
     */
    @Bean(destroyMethod = "destroy")
    ProducerFactory<String, LinkUpdate> linkUpdateProducerFactory(
            KafkaProperties kafkaProperties,
            ObjectMapper objectMapper,
            KafkaInfrastructureValidator kafkaInfrastructureValidator) {
        kafkaInfrastructureValidator.validateConnectivity(kafkaProperties, "linkUpdateProducerFactory");

        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        config.put(ProducerConfig.CLIENT_ID_CONFIG, kafkaProperties.getClientId());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        return new DefaultKafkaProducerFactory<>(
                config, new StringSerializer(), new LinkUpdateSerializer(objectMapper));
    }

    /**
     * KafkaTemplate для публикации LinkUpdate в топик уведомлений.
     */
    @Bean
    KafkaTemplate<String, LinkUpdate> linkUpdateKafkaTemplate(
            ProducerFactory<String, LinkUpdate> linkUpdateProducerFactory) {
        return new KafkaTemplate<>(linkUpdateProducerFactory);
    }
}
