package backend.academy.linktracker.bot.configuration;

import backend.academy.linktracker.bot.client.dto.LinkUpdate;
import backend.academy.linktracker.bot.properties.KafkaProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonSerde;

/**
 * Конфигурация Kafka-консьюмера бота для получения уведомлений.
 */
@Configuration
@EnableKafka
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
     * Фабрика Kafka-консьюмера LinkUpdate с JsonSerde.
     */
    @Bean
    @SuppressWarnings({"deprecation", "resource"})
    ConsumerFactory<String, LinkUpdate> linkUpdateConsumerFactory(
            KafkaProperties kafkaProperties,
            ObjectMapper objectMapper,
            KafkaInfrastructureValidator kafkaInfrastructureValidator) {
        kafkaInfrastructureValidator.validateConnectivity(kafkaProperties, "linkUpdateConsumerFactory");

        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaProperties.getGroupId());
        config.put(ConsumerConfig.CLIENT_ID_CONFIG, kafkaProperties.getClientId());

        JsonSerde<LinkUpdate> valueSerde = new JsonSerde<>(LinkUpdate.class, objectMapper);
        valueSerde.deserializer().addTrustedPackages("backend.academy.linktracker.*");
        valueSerde.deserializer().setRemoveTypeHeaders(true);
        valueSerde.deserializer().setUseTypeMapperForKey(false);

        return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), valueSerde.deserializer());
    }

    /**
     * Listener container factory для @KafkaListener бота.
     */
    @Bean
    ConcurrentKafkaListenerContainerFactory<String, LinkUpdate> linkUpdateKafkaListenerContainerFactory(
            ConsumerFactory<String, LinkUpdate> linkUpdateConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, LinkUpdate> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(linkUpdateConsumerFactory);
        return factory;
    }
}
