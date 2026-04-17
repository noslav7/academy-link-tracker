package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.dto.LinkUpdate;
import backend.academy.linktracker.scrapper.properties.OutboxProperties;
import backend.academy.linktracker.scrapper.service.outbox.NotificationOutboxRepository;
import backend.academy.linktracker.scrapper.service.outbox.OutboxNotificationMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Фоновый publisher сообщений из Transactional Outbox.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final NotificationOutboxRepository notificationOutboxRepository;
    private final NotificationDispatcher notificationDispatcher;
    private final OutboxProperties outboxProperties;
    private final ObjectMapper objectMapper;

    /**
     * Создает publisher outbox-сообщений.
     *
     * @param notificationOutboxRepository репозиторий outbox
     * @param notificationDispatcher транспорт внешней доставки
     * @param outboxProperties настройки publisher-а
     * @param objectMapper JSON-десериализатор
     */
    public OutboxPublisher(
            NotificationOutboxRepository notificationOutboxRepository,
            NotificationDispatcher notificationDispatcher,
            OutboxProperties outboxProperties,
            ObjectProvider<ObjectMapper> objectMapperProvider) {
        this.notificationOutboxRepository = notificationOutboxRepository;
        this.notificationDispatcher = notificationDispatcher;
        this.outboxProperties = outboxProperties;
        this.objectMapper = objectMapperProvider.getIfAvailable(
                () -> JsonMapper.builder().findAndAddModules().build());
    }

    /**
     * Публикует накопленные outbox-сообщения.
     */
    @Scheduled(fixedDelayString = "#{@outboxProperties.publishInterval.toMillis()}")
    public void publishPendingNotifications() {
        List<OutboxNotificationMessage> messages =
                notificationOutboxRepository.fetchUnprocessedBatch(outboxProperties.getBatchSize());
        for (OutboxNotificationMessage message : messages) {
            try {
                LinkUpdate update = objectMapper.readValue(message.payloadJson(), LinkUpdate.class);
                notificationDispatcher.dispatch(update);
                notificationOutboxRepository.markProcessed(message.id());
            } catch (Exception exception) {
                log.atWarn()
                        .setCause(exception)
                        .addKeyValue("outboxMessageId", message.id())
                        .log("Failed to publish outbox notification, will retry on next run");
            }
        }
    }
}
