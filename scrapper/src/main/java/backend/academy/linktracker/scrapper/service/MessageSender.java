package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.dto.LinkUpdate;

/**
 * Абстракция отправки уведомлений об обновлениях ссылок из scrapper в bot.
 * Реализации: HTTP (текущая), Kafka (будет добавлена позже).
 */
public interface MessageSender {
    /**
     * Отправляет уведомление об обновлении ссылки.
     *
     * @param update данные обновления ссылки
     */
    void send(LinkUpdate update);
}
