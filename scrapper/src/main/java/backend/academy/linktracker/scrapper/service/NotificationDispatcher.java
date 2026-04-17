package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.dto.LinkUpdate;

/**
 * Низкоуровневый транспорт доставки уведомлений во внешний bot-сервис.
 */
public interface NotificationDispatcher {
    /**
     * Немедленно отправляет уведомление во внешний транспорт.
     *
     * @param update данные обновления ссылки
     */
    void dispatch(LinkUpdate update);
}
