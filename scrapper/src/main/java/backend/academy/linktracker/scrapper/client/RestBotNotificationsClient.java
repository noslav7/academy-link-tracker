package backend.academy.linktracker.scrapper.client;

import backend.academy.linktracker.scrapper.dto.LinkUpdate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.RestClient;

/**
 * HTTP-клиент отправки уведомлений в bot-сервис.
 */
public class RestBotNotificationsClient implements BotNotificationsClient {

    /**
     * Клиент HTTP-вызовов bot-сервиса.
     */
    private final RestClient restClient;

    /**
     * Создает клиент уведомлений по REST.
     *
     * @param restClient HTTP-клиент для запросов в bot
     */
    public RestBotNotificationsClient(@Qualifier("botRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendUpdate(LinkUpdate update) {
        restClient.post().uri("/updates").body(update).retrieve().toBodilessEntity();
    }
}
