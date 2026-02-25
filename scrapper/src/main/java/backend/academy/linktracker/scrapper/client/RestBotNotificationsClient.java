package backend.academy.linktracker.scrapper.client;

import backend.academy.linktracker.scrapper.dto.LinkUpdate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.RestClient;

public class RestBotNotificationsClient implements BotNotificationsClient {

    private final RestClient restClient;

    public RestBotNotificationsClient(@Qualifier("botRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public void sendUpdate(LinkUpdate update) {
        restClient.post().uri("/updates").body(update).retrieve().toBodilessEntity();
    }
}
