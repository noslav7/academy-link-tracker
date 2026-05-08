package backend.academy.linktracker.scrapper.configuration;

import backend.academy.linktracker.scrapper.properties.KafkaProperties;
import backend.academy.linktracker.scrapper.properties.TransportMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.apicurio.registry.serde.avro.AvroKafkaSerdeConfig;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import io.apicurio.registry.serde.avro.strategy.RecordIdStrategy;
import java.util.HashMap;
import java.util.Map;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
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
@Conditional(KafkaProducerConfiguration.KafkaProducerEnabledCondition.class)
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
    ProducerFactory<String, GenericRecord> linkUpdateProducerFactory(
            KafkaProperties kafkaProperties, KafkaProducerInfrastructureInitializer initializer) {
        initializer.initialize("linkUpdateProducerFactory");

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
     * Инициализатор Kafka-инфраструктуры для producer-бинов.
     */
    @Bean
    @ConditionalOnMissingBean(KafkaProducerInfrastructureInitializer.class)
    KafkaProducerInfrastructureInitializer kafkaProducerInfrastructureInitializer(KafkaProperties kafkaProperties) {
        return new KafkaProducerInfrastructureInitializer(kafkaProperties);
    }

    /**
     * KafkaTemplate для публикации LinkUpdateEvent в топик уведомлений.
     */
    @Bean
    KafkaTemplate<String, GenericRecord> linkUpdateKafkaTemplate(
            ProducerFactory<String, GenericRecord> linkUpdateProducerFactory) {
        return new KafkaTemplate<>(linkUpdateProducerFactory);
    }

    /**
     * Активирует Kafka producer для KAFKA-транспорта и HTTP-транспорта с fallback.
     */
    static final class KafkaProducerEnabledCondition extends AnyNestedCondition {
        KafkaProducerEnabledCondition() {
            super(org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase.PARSE_CONFIGURATION);
        }

        @ConditionalOnProperty(
                prefix = "app.bot",
                name = "transport",
                havingValue = TransportMode.KAFKA_VALUE,
                matchIfMissing = true)
        static class KafkaTransportCondition {}

        @ConditionalOnProperty(prefix = "app.bot", name = "transport", havingValue = TransportMode.HTTP_VALUE)
        @ConditionalOnProperty(
                prefix = "app.bot",
                name = "http-fallback-to-kafka-enabled",
                havingValue = "true",
                matchIfMissing = true)
        static class HttpTransportCondition {}
    }
}
