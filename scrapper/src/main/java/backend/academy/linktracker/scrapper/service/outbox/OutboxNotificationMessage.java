package backend.academy.linktracker.scrapper.service.outbox;

/**
 * Сообщение уведомления, сохраненное в outbox.
 *
 * @param id идентификатор outbox-записи
 * @param payloadJson сериализованный payload уведомления
 */
public record OutboxNotificationMessage(long id, String payloadJson) {}
