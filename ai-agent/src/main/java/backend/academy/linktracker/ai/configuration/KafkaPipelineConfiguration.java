package backend.academy.linktracker.ai.configuration;

import backend.academy.linktracker.ai.dto.ProcessedLinkUpdate;
import backend.academy.linktracker.ai.properties.KafkaProperties;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
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
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

/**
 * Сборка Kafka-клиентов AI Agent: {@link ConsumerFactory}, listener container factory и {@link KafkaTemplate}.
 * Параметры подключения — в {@link KafkaProperties}; приём, обработка и публикация сообщений — в listener-слое.
 */
@Configuration
@EnableKafka
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KafkaPipelineConfiguration {

    /**
     * Создает фабрику консьюмера сырых сообщений.
     */
    @Bean
    ConsumerFactory<String, String> rawLinkUpdateConsumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaProperties.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                kafkaProperties.getGroupId(),
                ConsumerConfig.CLIENT_ID_CONFIG,
                kafkaProperties.getClientId(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                kafkaProperties.getAutoOffsetReset(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    /**
     * Создает container factory для @KafkaListener обработки сырых обновлений.
     */
    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> rawLinkUpdateKafkaListenerContainerFactory(
            ConsumerFactory<String, String> rawLinkUpdateConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(rawLinkUpdateConsumerFactory);
        return factory;
    }

    /**
     * Создает фабрику продьюсера обработанных сообщений.
     */
    @Bean(destroyMethod = "destroy")
    ProducerFactory<String, ProcessedLinkUpdate> processedLinkUpdateProducerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> config = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaProperties.getBootstrapServers(),
                ProducerConfig.CLIENT_ID_CONFIG,
                kafkaProperties.getClientId() + "-processed-producer",
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                JacksonJsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(config);
    }

    /**
     * Создает KafkaTemplate для публикации обработанных обновлений.
     */
    @Bean(destroyMethod = "destroy")
    KafkaTemplate<String, ProcessedLinkUpdate> processedLinkUpdateKafkaTemplate(
            ProducerFactory<String, ProcessedLinkUpdate> processedLinkUpdateProducerFactory) {
        return new KafkaTemplate<>(processedLinkUpdateProducerFactory);
    }
}
