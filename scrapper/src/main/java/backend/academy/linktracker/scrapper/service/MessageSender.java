package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.dto.LinkUpdate;

/**
 * Абстракция отправки уведомлений об обновлениях ссылок из scrapper в bot.
 * Основная реализация пишет сообщения в Transactional Outbox.
 */
public interface MessageSender {
    /**
     * Отправляет уведомление об обновлении ссылки.
     *
     * @param update данные обновления ссылки
     */
    void send(LinkUpdate update);
}
