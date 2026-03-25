package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.dto.LinkUpdate;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Проверяет одну отслеживаемую ссылку на наличие обновлений,
 * при необходимости отправляет уведомление и обновляет метаданные в БД.
 */
@Component
public class LinkUpdateChecker {

    private static final Logger log = LoggerFactory.getLogger(LinkUpdateChecker.class);

    private final LinkMetadataResolver linkMetadataResolver;
    private final MessageSender messageSender;
    private final TrackedLinkMaintenanceService maintenanceService;

    /**
     * Создает компонент проверки обновлений ссылок.
     *
     * @param linkMetadataResolver резолвер метаданных внешних ссылок
     * @param messageSender        отправитель уведомлений подписчикам
     * @param maintenanceService   сервис обновления метаданных ссылок в БД
     */
    public LinkUpdateChecker(
            LinkMetadataResolver linkMetadataResolver,
            MessageSender messageSender,
            TrackedLinkMaintenanceService maintenanceService) {
        this.linkMetadataResolver = linkMetadataResolver;
        this.messageSender = messageSender;
        this.maintenanceService = maintenanceService;
    }

    /**
     * Проверяет ссылку на обновление: сравнивает текущее время обновления
     * с сохранённым, и при наличии изменения — отправляет уведомление подписчикам.
     *
     * @param trackedLink отслеживаемая ссылка для проверки
     */
    public void check(TrackedLink trackedLink) {
        Instant currentUpdatedAt = linkMetadataResolver.resolveLastUpdated(trackedLink.url());

        if (!currentUpdatedAt.isAfter(trackedLink.lastUpdatedAt())) {
            maintenanceService.updateTrackedLink(trackedLink.withLastCheck(Instant.now(), trackedLink.lastUpdatedAt()));
            return;
        }

        String description = linkMetadataResolver.resolveUpdateDescription(trackedLink.url());
        messageSender.send(
                new LinkUpdate(trackedLink.id(), trackedLink.url(), description, List.of(trackedLink.chatId())));
        maintenanceService.updateTrackedLink(trackedLink.withLastCheck(Instant.now(), currentUpdatedAt));

        log.atInfo()
                .addKeyValue("chatId", trackedLink.chatId())
                .addKeyValue("linkId", trackedLink.id())
                .addKeyValue("url", trackedLink.url())
                .log("Link update notification sent");
    }
}
