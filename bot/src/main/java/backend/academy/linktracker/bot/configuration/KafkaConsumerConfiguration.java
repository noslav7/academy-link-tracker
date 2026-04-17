package backend.academy.linktracker.bot.configuration;

import backend.academy.linktracker.bot.kafka.InvalidLinkUpdateException;
import backend.academy.linktracker.bot.properties.KafkaProperties;
import com.example.notification.LinkUpdateEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import io.apicurio.registry.serde.avro.AvroKafkaSerdeConfig;
import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Конфигурация Kafka-консьюмера бота для получения уведомлений.
 */
@Configuration
@EnableKafka
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConsumerConfiguration {

    /**
     * ObjectMapper с автоподключением модулей для десериализации Kafka-пейлоуда.
     */
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    ObjectMapper objectMapper() {
        return JsonMapper.builder().findAndAddModules().build();
    }

    /**
     * Фабрика Kafka-консьюмера LinkUpdate с ErrorHandlingDeserializer.
     */
    @Bean
    @SuppressWarnings({"deprecation", "resource"})
    ConsumerFactory<String, LinkUpdateEvent> linkUpdateConsumerFactory(
            KafkaProperties kafkaProperties, KafkaInfrastructureValidator kafkaInfrastructureValidator) {
        kafkaInfrastructureValidator.validateConnectivity(kafkaProperties, "linkUpdateConsumerFactory");

        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaProperties.getGroupId());
        config.put(ConsumerConfig.CLIENT_ID_CONFIG, kafkaProperties.getClientId());
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        config.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, AvroKafkaDeserializer.class.getName());
        config.put("apicurio.registry.url", kafkaProperties.getSchemaRegistryUrl());
        config.put("apicurio.registry.find-latest", true);
        config.put(AvroKafkaSerdeConfig.USE_SPECIFIC_AVRO_READER, true);
        config.put(AvroKafkaSerdeConfig.AVRO_ENCODING, AvroKafkaSerdeConfig.AVRO_ENCODING_BINARY);

        return new DefaultKafkaConsumerFactory<>(config);
    }

    /**
     * KafkaTemplate для публикации необработанных сообщений в DLQ.
     */
    @Bean
    KafkaTemplate<String, Object> deadLetterKafkaTemplate(KafkaProperties kafkaProperties, ObjectMapper objectMapper) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        config.put(ProducerConfig.CLIENT_ID_CONFIG, kafkaProperties.getClientId() + "-dlq-producer");
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        return new KafkaTemplate<>(
                new DefaultKafkaProducerFactory<>(config, new StringSerializer(), new JsonSerializer<>(objectMapper)));
    }

    /**
     * Обработчик ошибок консьюмера:
     * - повторяет обработку для временных ошибок;
     * - отправляет сообщение в DLQ после исчерпания попыток;
     * - не повторяет десериализацию и валидацию, сразу направляя их в DLQ.
     */
    @Bean
    CommonErrorHandler kafkaConsumerErrorHandler(
            KafkaProperties kafkaProperties, KafkaTemplate<String, Object> deadLetterKafkaTemplate) {
        DeadLetterPublishingRecoverer deadLetterPublishingRecoverer = new DeadLetterPublishingRecoverer(
                deadLetterKafkaTemplate,
                (record, exception) -> new TopicPartition(kafkaProperties.getDlqTopic(), record.partition()));

        long retriesAfterFirstAttempt = Math.max(0L, kafkaProperties.getRetryAttempts() - 1L);
        FixedBackOff fixedBackOff = new FixedBackOff(kafkaProperties.getRetryBackoffMs(), retriesAfterFirstAttempt);
        DefaultErrorHandler defaultErrorHandler = new DefaultErrorHandler(deadLetterPublishingRecoverer, fixedBackOff);
        defaultErrorHandler.addNotRetryableExceptions(
                DeserializationException.class, InvalidLinkUpdateException.class, ConstraintViolationException.class);

        return defaultErrorHandler;
    }

    /**
     * Listener container factory для @KafkaListener бота.
     */
    @Bean
    ConcurrentKafkaListenerContainerFactory<String, LinkUpdateEvent> linkUpdateKafkaListenerContainerFactory(
            ConsumerFactory<String, LinkUpdateEvent> linkUpdateConsumerFactory,
            CommonErrorHandler kafkaConsumerErrorHandler,
            KafkaProperties kafkaProperties,
            KafkaInfrastructureValidator kafkaInfrastructureValidator) {
        kafkaInfrastructureValidator.validateConnectivity(kafkaProperties, "linkUpdateKafkaListenerContainerFactory");

        ConcurrentKafkaListenerContainerFactory<String, LinkUpdateEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(linkUpdateConsumerFactory);
        factory.setCommonErrorHandler(kafkaConsumerErrorHandler);
        return factory;
    }
}
