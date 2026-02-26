package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.client.BotNotificationsClient;
import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.dto.LinkUpdate;
import backend.academy.linktracker.scrapper.properties.SchedulerProperties;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LinkUpdatesScheduler {

    private static final Logger log = LoggerFactory.getLogger(LinkUpdatesScheduler.class);

    private final LinkService linkService;
    private final BotNotificationsClient botNotificationsClient;
    private final SchedulerProperties schedulerProperties;

    public LinkUpdatesScheduler(
            LinkService linkService,
            BotNotificationsClient botNotificationsClient,
            SchedulerProperties schedulerProperties) {
        this.linkService = linkService;
        this.botNotificationsClient = botNotificationsClient;
        this.schedulerProperties = schedulerProperties;
    }

    @Scheduled(fixedDelayString = "#{@schedulerProperties.interval.toMillis()}")
    public void checkUpdates() {
        for (TrackedLink trackedLink : linkService.allTrackedLinks()) {
            Instant currentUpdatedAt = linkService.resolveLastUpdated(trackedLink.url());
            if (currentUpdatedAt.isAfter(trackedLink.lastUpdatedAt())) {
                botNotificationsClient.sendUpdate(new LinkUpdate(
                        trackedLink.id(),
                        trackedLink.url(),
                        "Обнаружено обновление",
                        java.util.List.of(trackedLink.chatId())));
                linkService.updateTrackedLink(trackedLink.withLastCheck(Instant.now(), currentUpdatedAt));
                log.atInfo()
                        .addKeyValue("chatId", trackedLink.chatId())
                        .addKeyValue("linkId", trackedLink.id())
                        .addKeyValue("url", trackedLink.url())
                        .log("Link update notification sent");
            } else {
                linkService.updateTrackedLink(trackedLink.withLastCheck(Instant.now(), trackedLink.lastUpdatedAt()));
            }
        }

        log.atDebug()
                .addKeyValue("intervalMs", schedulerProperties.getInterval().toMillis())
                .log("Scheduled links check completed");
    }
}
