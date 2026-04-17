package backend.academy.linktracker.scrapper.configuration;

import backend.academy.linktracker.scrapper.properties.KafkaProperties;
import backend.academy.linktracker.scrapper.properties.TransportMode;
import com.example.notification.LinkUpdateEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.apicurio.registry.serde.avro.AvroKafkaSerdeConfig;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import io.apicurio.registry.serde.avro.strategy.RecordIdStrategy;
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
     * Фабрика Kafka-публикатора для LinkUpdateEvent с Avro-сериализацией через Schema Registry.
     */
    @Bean(destroyMethod = "destroy")
    @SuppressWarnings({"deprecation", "resource"})
    ProducerFactory<String, LinkUpdateEvent> linkUpdateProducerFactory(
            KafkaProperties kafkaProperties, KafkaInfrastructureValidator kafkaInfrastructureValidator) {
        kafkaInfrastructureValidator.validateConnectivity(kafkaProperties, "linkUpdateProducerFactory");
        kafkaInfrastructureValidator.ensureNotificationTopicExists(kafkaProperties);

        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        config.put(ProducerConfig.CLIENT_ID_CONFIG, kafkaProperties.getClientId());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, AvroKafkaSerializer.class);
        config.put("apicurio.registry.url", kafkaProperties.getSchemaRegistryUrl());
        config.put("apicurio.registry.auto-register", true);
        config.put("apicurio.registry.find-latest", true);
        config.put("apicurio.registry.artifact-id.strategy", RecordIdStrategy.class.getName());
        config.put(AvroKafkaSerdeConfig.AVRO_ENCODING, AvroKafkaSerdeConfig.AVRO_ENCODING_BINARY);

        return new DefaultKafkaProducerFactory<>(config);
    }

    /**
     * KafkaTemplate для публикации LinkUpdateEvent в топик уведомлений.
     */
    @Bean
    KafkaTemplate<String, LinkUpdateEvent> linkUpdateKafkaTemplate(
            ProducerFactory<String, LinkUpdateEvent> linkUpdateProducerFactory) {
        return new KafkaTemplate<>(linkUpdateProducerFactory);
    }
}
