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

/**
 * Планировщик периодической проверки обновлений отслеживаемых ссылок.
 */
@Component
public class LinkUpdatesScheduler {

    /**
     * Логгер компонента.
     */
    private static final Logger log = LoggerFactory.getLogger(LinkUpdatesScheduler.class);

    /**
     * Сервис чтения/обновления ссылок.
     */
    private final TrackedLinkMaintenanceService trackedLinkMaintenanceService;
    /**
     * Резолвер времени последнего обновления ссылки.
     */
    private final LinkMetadataResolver linkMetadataResolver;
    /**
     * Клиент отправки уведомлений в bot.
     */
    private final BotNotificationsClient botNotificationsClient;
    /**
     * Настройки интервала планировщика.
     */
    private final SchedulerProperties schedulerProperties;

    /**
     * Создает планировщик проверки ссылок.
     *
     * @param trackedLinkMaintenanceService сервис чтения/обновления отслеживаемых ссылок
     * @param linkMetadataResolver резолвер времени последнего обновления ссылки
     * @param botNotificationsClient клиент отправки уведомлений
     * @param schedulerProperties настройки интервала
     */
    public LinkUpdatesScheduler(
            TrackedLinkMaintenanceService trackedLinkMaintenanceService,
            LinkMetadataResolver linkMetadataResolver,
            BotNotificationsClient botNotificationsClient,
            SchedulerProperties schedulerProperties) {
        this.trackedLinkMaintenanceService = trackedLinkMaintenanceService;
        this.linkMetadataResolver = linkMetadataResolver;
        this.botNotificationsClient = botNotificationsClient;
        this.schedulerProperties = schedulerProperties;
    }

    /**
     * Проверяет все отслеживаемые ссылки и отправляет уведомления о новых обновлениях.
     */
    @Scheduled(fixedDelayString = "#{@schedulerProperties.interval.toMillis()}")
    public void checkUpdates() {
        for (TrackedLink trackedLink : trackedLinkMaintenanceService.allTrackedLinks()) {
            Instant currentUpdatedAt = linkMetadataResolver.resolveLastUpdated(trackedLink.url());
            if (currentUpdatedAt.isAfter(trackedLink.lastUpdatedAt())) {
                botNotificationsClient.sendUpdate(new LinkUpdate(
                        trackedLink.id(),
                        trackedLink.url(),
                        "Обнаружено обновление",
                        java.util.List.of(trackedLink.chatId())));
                trackedLinkMaintenanceService.updateTrackedLink(
                        trackedLink.withLastCheck(Instant.now(), currentUpdatedAt));
                log.atInfo()
                        .addKeyValue("chatId", trackedLink.chatId())
                        .addKeyValue("linkId", trackedLink.id())
                        .addKeyValue("url", trackedLink.url())
                        .log("Link update notification sent");
            } else {
                trackedLinkMaintenanceService.updateTrackedLink(
                        trackedLink.withLastCheck(Instant.now(), trackedLink.lastUpdatedAt()));
            }
        }

        log.atDebug()
                .addKeyValue("intervalMs", schedulerProperties.getInterval().toMillis())
                .log("Scheduled links check completed");
    }
}
