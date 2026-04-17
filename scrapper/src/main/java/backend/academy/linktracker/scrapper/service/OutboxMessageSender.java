package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.dto.LinkUpdate;
import backend.academy.linktracker.scrapper.service.outbox.NotificationOutboxRepository;

/**
 * Пишет уведомления в outbox-таблицу вместо прямой отправки во внешний транспорт.
 */
public class OutboxMessageSender implements MessageSender {

    private final NotificationOutboxRepository notificationOutboxRepository;

    /**
     * Создает отправитель, использующий Transactional Outbox.
     *
     * @param notificationOutboxRepository репозиторий outbox-сообщений
     */
    public OutboxMessageSender(NotificationOutboxRepository notificationOutboxRepository) {
        this.notificationOutboxRepository = notificationOutboxRepository;
    }

    @Override
    public void send(LinkUpdate update) {
        notificationOutboxRepository.enqueue(update);
    }
}
