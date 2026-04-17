package backend.academy.linktracker.scrapper.configuration;

import backend.academy.linktracker.scrapper.client.BotNotificationsClient;
import backend.academy.linktracker.scrapper.properties.KafkaProperties;
import backend.academy.linktracker.scrapper.properties.TransportMode;
import backend.academy.linktracker.scrapper.service.ClientMessageSender;
import backend.academy.linktracker.scrapper.service.KafkaMessageSender;
import backend.academy.linktracker.scrapper.service.MessageSender;
import backend.academy.linktracker.scrapper.service.NotificationDispatcher;
import backend.academy.linktracker.scrapper.service.OutboxMessageSender;
import backend.academy.linktracker.scrapper.service.outbox.NotificationOutboxRepository;
import com.example.notification.LinkUpdateEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
    @ConditionalOnProperty(prefix = "app.bot", name = "transport", havingValue = TransportMode.HTTP_VALUE)
    NotificationDispatcher httpNotificationDispatcher(BotNotificationsClient botNotificationsClient) {
        return clientMessageSender(botNotificationsClient);
    }

    /**
     * Транспорт доставки через gRPC-клиент bot-сервиса.
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.bot", name = "transport", havingValue = TransportMode.GRPC_VALUE)
    NotificationDispatcher grpcNotificationDispatcher(BotNotificationsClient botNotificationsClient) {
        return clientMessageSender(botNotificationsClient);
    }

    /**
     * Транспорт доставки через Kafka (транспорт по умолчанию).
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "app.bot",
            name = "transport",
            havingValue = TransportMode.KAFKA_VALUE,
            matchIfMissing = true)
    NotificationDispatcher kafkaNotificationDispatcher(
            KafkaTemplate<String, LinkUpdateEvent> kafkaTemplate, KafkaProperties kafkaProperties) {
        return new KafkaMessageSender(kafkaTemplate, kafkaProperties);
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
}
