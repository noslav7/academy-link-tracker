package backend.academy.linktracker.scrapper.configuration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import backend.academy.linktracker.scrapper.properties.KafkaProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;

@Tag("unit")
class KafkaProducerConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(KafkaProducerConfiguration.class);

    @Test
    void givenHttpTransportWhenContextStartsThenProducerFactoryBeanIsCreated() {
        // Проверяет создание producer factory при HTTP-транспорте.
        KafkaProducerInfrastructureInitializer initializer = mock(KafkaProducerInfrastructureInitializer.class);
        doNothing().when(initializer).initialize(anyString());

        contextRunner
                .withPropertyValues("app.bot.transport=HTTP")
                .withBean(KafkaProperties.class, () -> kafkaProperties("localhost:9092", "http-fallback-test"))
                .withBean(KafkaProducerInfrastructureInitializer.class, () -> initializer)
                .run(context -> assertTrue(context.containsBean("linkUpdateProducerFactory")));
    }

    @Test
    void givenHttpTransportWhenContextStartsThenKafkaTemplateBeanIsCreated() {
        // Проверяет создание KafkaTemplate при HTTP-транспорте.
        KafkaProducerInfrastructureInitializer initializer = mock(KafkaProducerInfrastructureInitializer.class);
        doNothing().when(initializer).initialize(anyString());

        contextRunner
                .withPropertyValues("app.bot.transport=HTTP")
                .withBean(KafkaProperties.class, () -> kafkaProperties("localhost:9092", "http-fallback-test"))
                .withBean(KafkaProducerInfrastructureInitializer.class, () -> initializer)
                .run(context -> assertNotNull(context.getBean(KafkaTemplate.class)));
    }

    @Test
    void givenHttpTransportWhenContextStartsThenInitializerIsInvokedForProducerFactory() {
        // Проверяет вызов инициализатора Kafka-инфраструктуры для producer factory.
        KafkaProducerInfrastructureInitializer initializer = mock(KafkaProducerInfrastructureInitializer.class);
        doNothing().when(initializer).initialize(anyString());

        contextRunner
                .withPropertyValues("app.bot.transport=HTTP")
                .withBean(KafkaProperties.class, () -> kafkaProperties("localhost:9092", "http-fallback-test"))
                .withBean(KafkaProducerInfrastructureInitializer.class, () -> initializer)
                .run(context -> verify(initializer, times(1)).initialize("linkUpdateProducerFactory"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"linkUpdateKafkaTemplate", "linkUpdateProducerFactory"})
    void givenGrpcTransportWhenContextStartsThenKafkaProducerBeanIsNotCreated(String beanName) {
        // Проверяет отсутствие Kafka producer-бинов при GRPC-транспорте.
        contextRunner
                .withPropertyValues("app.bot.transport=GRPC")
                .run(context -> assertFalse(context.containsBean(beanName)));
    }

    private static KafkaProperties kafkaProperties(String bootstrapServers, String topic) {
        KafkaProperties properties = new KafkaProperties();
        properties.setBootstrapServers(bootstrapServers);
        properties.setSchemaRegistryUrl("http://localhost:8085/apis/registry/v2");
        properties.setTopic(topic);
        properties.setClientId("scrapper-test");
        return properties;
    }
}
