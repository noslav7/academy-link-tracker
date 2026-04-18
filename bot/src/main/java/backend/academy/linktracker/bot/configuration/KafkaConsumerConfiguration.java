package backend.academy.linktracker.bot.configuration;

import backend.academy.linktracker.bot.kafka.InvalidLinkUpdateException;
import backend.academy.linktracker.bot.properties.KafkaProperties;
import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import io.apicurio.registry.serde.avro.AvroKafkaSerdeConfig;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.avro.generic.GenericRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Конфигурация Kafka-консьюмера бота для получения уведомлений.
 */
@Configuration
@EnableKafka
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConsumerConfiguration {

    /**
     * Фабрика Kafka-консьюмера LinkUpdate с ErrorHandlingDeserializer.
     */
    @Bean
    ConsumerFactory<String, GenericRecord> linkUpdateConsumerFactory(
            KafkaProperties kafkaProperties, KafkaInfrastructureValidator kafkaInfrastructureValidator) {
        kafkaInfrastructureValidator.validateConnectivity(kafkaProperties, "linkUpdateConsumerFactory");

        Map<String, Object> config = KafkaClientConfigFactory.consumerBaseConfig(kafkaProperties);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        config.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, AvroKafkaDeserializer.class.getName());
        config.put("apicurio.registry.url", kafkaProperties.getSchemaRegistryUrl());
        config.put("apicurio.registry.find-latest", true);
        config.put(AvroKafkaSerdeConfig.USE_SPECIFIC_AVRO_READER, false);
        config.put(AvroKafkaSerdeConfig.AVRO_ENCODING, AvroKafkaSerdeConfig.AVRO_ENCODING_BINARY);

        return new DefaultKafkaConsumerFactory<>(config);
    }

    /**
     * KafkaTemplate для публикации необработанных сообщений в DLQ.
     */
    @Bean
    KafkaTemplate<String, Object> deadLetterKafkaTemplate(ProducerFactory<String, Object> deadLetterProducerFactory) {
        return new KafkaTemplate<>(deadLetterProducerFactory);
    }

    /**
     * Фабрика DLQ-публикатора с явным lifecycle cleanup.
     */
    @Bean(destroyMethod = "destroy")
    ProducerFactory<String, Object> deadLetterProducerFactory(
            KafkaProperties kafkaProperties, KafkaInfrastructureValidator kafkaInfrastructureValidator) {
        kafkaInfrastructureValidator.ensureDeadLetterTopicExists(kafkaProperties);

        Map<String, Object> config = KafkaClientConfigFactory.producerBaseConfig(kafkaProperties, "-dlq-producer");
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);

        return new DefaultKafkaProducerFactory<>(config, new StringSerializer(), new JacksonJsonSerializer<>());
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
                // Не закрепляем номер партиции в DLQ: это исключает сбои,
                // если число партиций исходного топика и DLQ отличается.
                (record, exception) -> new TopicPartition(kafkaProperties.getDlqTopic(), -1));

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
    ConcurrentKafkaListenerContainerFactory<String, GenericRecord> linkUpdateKafkaListenerContainerFactory(
            ConsumerFactory<String, GenericRecord> linkUpdateConsumerFactory,
            CommonErrorHandler kafkaConsumerErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, GenericRecord> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(linkUpdateConsumerFactory);
        factory.setCommonErrorHandler(kafkaConsumerErrorHandler);
        return factory;
    }
}
