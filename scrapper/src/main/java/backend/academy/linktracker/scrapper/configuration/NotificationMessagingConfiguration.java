package backend.academy.linktracker.scrapper.configuration;

import backend.academy.linktracker.scrapper.client.BotNotificationsClient;
import backend.academy.linktracker.scrapper.metrics.ScrapperMetricsRecorder;
import backend.academy.linktracker.scrapper.properties.BotClientProperties;
import backend.academy.linktracker.scrapper.properties.KafkaProperties;
import backend.academy.linktracker.scrapper.properties.TransportMode;
import backend.academy.linktracker.scrapper.service.CircuitBreakerFallbackNotificationDispatcher;
import backend.academy.linktracker.scrapper.service.ClientMessageSender;
import backend.academy.linktracker.scrapper.service.KafkaMessageSender;
import backend.academy.linktracker.scrapper.service.MessageSender;
import backend.academy.linktracker.scrapper.service.NotificationDispatcher;
import backend.academy.linktracker.scrapper.service.OutboxMessageSender;
import backend.academy.linktracker.scrapper.service.outbox.NotificationOutboxRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.apache.avro.generic.GenericRecord;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Конфигурация выбора транспорта отправки уведомлений в bot.
 */
@Configuration
public class NotificationMessagingConfiguration {

    /**
     * Транспорт доставки через HTTP-клиент bot-сервиса.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "app.bot", name = "transport", havingValue = TransportMode.HTTP_VALUE)
    @ConditionalOnProperty(
            prefix = "app.bot",
            name = "http-fallback-to-kafka-enabled",
            havingValue = "true",
            matchIfMissing = true)
    NotificationDispatcher httpNotificationDispatcherWithFallback(
            BotNotificationsClient botNotificationsClient,
            KafkaMessageSender kafkaMessageSender,
            BotClientProperties botClientProperties) {
        NotificationDispatcher primaryHttpDispatcher = clientMessageSender(botNotificationsClient);
        CircuitBreaker circuitBreaker =
                CircuitBreaker.of("bot-notifications-http", circuitBreakerConfig(botClientProperties));
        return new CircuitBreakerFallbackNotificationDispatcher(
                primaryHttpDispatcher, kafkaMessageSender, circuitBreaker);
    }

    /**
     * Транспорт доставки через HTTP-клиент без Kafka fallback (для тестовых/локальных профилей).
     */
    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "app.bot", name = "transport", havingValue = TransportMode.HTTP_VALUE)
    @ConditionalOnProperty(prefix = "app.bot", name = "http-fallback-to-kafka-enabled", havingValue = "false")
    NotificationDispatcher httpNotificationDispatcherWithoutFallback(BotNotificationsClient botNotificationsClient) {
        return clientMessageSender(botNotificationsClient);
    }

    /**
     * Транспорт доставки через gRPC-клиент bot-сервиса.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "app.bot", name = "transport", havingValue = TransportMode.GRPC_VALUE)
    NotificationDispatcher grpcNotificationDispatcher(BotNotificationsClient botNotificationsClient) {
        return clientMessageSender(botNotificationsClient);
    }

    /**
     * Транспорт доставки через Kafka (транспорт по умолчанию).
     */
    @Bean
    @Primary
    @ConditionalOnProperty(
            prefix = "app.bot",
            name = "transport",
            havingValue = TransportMode.KAFKA_VALUE,
            matchIfMissing = true)
    NotificationDispatcher kafkaNotificationDispatcher(KafkaMessageSender kafkaMessageSender) {
        return kafkaMessageSender;
    }

    /**
     * Kafka dispatcher, используемый как основной транспорт и как fallback для HTTP.
     */
    @Bean
    @ConditionalOnBean(KafkaTemplate.class)
    KafkaMessageSender kafkaMessageSender(
            KafkaTemplate<String, GenericRecord> kafkaTemplate,
            KafkaProperties kafkaProperties,
            ObjectProvider<ScrapperMetricsRecorder> metricsRecorderProvider) {
        ScrapperMetricsRecorder metricsRecorder = metricsRecorderProvider.getIfAvailable(ScrapperMetricsRecorder::noop);
        return new KafkaMessageSender(kafkaTemplate, kafkaProperties, metricsRecorder);
    }

    /**
     * Отправитель бизнес-сообщений через Transactional Outbox.
     */
    @Bean
    MessageSender outboxMessageSender(NotificationOutboxRepository notificationOutboxRepository) {
        return new OutboxMessageSender(notificationOutboxRepository);
    }

    /**
     * Создает отправитель уведомлений через клиент bot-сервиса.
     *
     * @param botNotificationsClient клиент доставки уведомлений
     * @return отправитель уведомлений через клиентский транспорт
     */
    private NotificationDispatcher clientMessageSender(BotNotificationsClient botNotificationsClient) {
        return new ClientMessageSender(botNotificationsClient);
    }

    private CircuitBreakerConfig circuitBreakerConfig(BotClientProperties botClientProperties) {
        BotClientProperties.CircuitBreaker circuitBreaker = botClientProperties.getCircuitBreaker();
        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(circuitBreaker.getSlidingWindowSize())
                .minimumNumberOfCalls(circuitBreaker.getMinimumRequiredCalls())
                .failureRateThreshold(circuitBreaker.getFailureRateThreshold())
                .permittedNumberOfCallsInHalfOpenState(circuitBreaker.getPermittedCallsInHalfOpenState())
                .waitDurationInOpenState(circuitBreaker.getWaitDurationInOpenState())
                .recordException(throwable -> true)
                .build();
    }
}
