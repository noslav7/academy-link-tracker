package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.client.BotNotificationsClient;
import backend.academy.linktracker.scrapper.dto.LinkUpdate;

/**
 * Реализация отправки уведомлений через клиент bot-сервиса (REST или gRPC).
 */
public class ClientMessageSender implements NotificationDispatcher {

    private final BotNotificationsClient botNotificationsClient;

    /**
     * Создает отправитель, делегирующий в клиент bot-сервиса.
     *
     * @param botNotificationsClient клиент уведомлений bot-сервиса
     */
    public ClientMessageSender(BotNotificationsClient botNotificationsClient) {
        this.botNotificationsClient = botNotificationsClient;
    }

    @Override
    public void dispatch(LinkUpdate update) {
        botNotificationsClient.sendUpdate(update);
    }
}
