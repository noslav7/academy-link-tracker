package backend.academy.linktracker.scrapper.client;

import backend.academy.linktracker.scrapper.dto.LinkUpdate;

public interface BotNotificationsClient {
    void sendUpdate(LinkUpdate update);
}
