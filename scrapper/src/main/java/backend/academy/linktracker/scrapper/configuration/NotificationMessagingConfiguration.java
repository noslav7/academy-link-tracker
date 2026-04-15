package backend.academy.linktracker.scrapper.configuration;

import backend.academy.linktracker.scrapper.client.BotNotificationsClient;
import backend.academy.linktracker.scrapper.dto.LinkUpdate;
import backend.academy.linktracker.scrapper.properties.KafkaProperties;
import backend.academy.linktracker.scrapper.properties.TransportMode;
import backend.academy.linktracker.scrapper.service.ClientMessageSender;
import backend.academy.linktracker.scrapper.service.KafkaMessageSender;
import backend.academy.linktracker.scrapper.service.MessageSender;
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
     * Отправитель через HTTP-клиент bot-сервиса.
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.bot", name = "transport", havingValue = TransportMode.HTTP_VALUE)
    MessageSender httpMessageSender(BotNotificationsClient botNotificationsClient) {
        return clientMessageSender(botNotificationsClient);
    }

    /**
     * Отправитель через gRPC-клиент bot-сервиса.
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.bot", name = "transport", havingValue = TransportMode.GRPC_VALUE)
    MessageSender grpcMessageSender(BotNotificationsClient botNotificationsClient) {
        return clientMessageSender(botNotificationsClient);
    }

    /**
     * Отправитель через Kafka (транспорт по умолчанию).
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "app.bot",
            name = "transport",
            havingValue = TransportMode.KAFKA_VALUE,
            matchIfMissing = true)
    MessageSender kafkaMessageSender(KafkaTemplate<String, LinkUpdate> kafkaTemplate, KafkaProperties kafkaProperties) {
        return new KafkaMessageSender(kafkaTemplate, kafkaProperties);
    }

    /**
     * Создает отправитель уведомлений через клиент bot-сервиса.
     *
     * @param botNotificationsClient клиент доставки уведомлений
     * @return отправитель уведомлений через клиентский транспорт
     */
    private MessageSender clientMessageSender(BotNotificationsClient botNotificationsClient) {
        return new ClientMessageSender(botNotificationsClient);
    }
}
