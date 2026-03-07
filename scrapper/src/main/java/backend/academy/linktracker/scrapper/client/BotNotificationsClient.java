package backend.academy.linktracker.scrapper.client;

import backend.academy.linktracker.scrapper.dto.LinkUpdate;

/**
 * Контракт отправки уведомлений из scrapper в bot-сервис.
 */
public interface BotNotificationsClient {
    /**
     * Отправляет уведомление об обновлении ссылки.
     *
     * @param update данные обновления ссылки
     */
    void sendUpdate(LinkUpdate update);
}
