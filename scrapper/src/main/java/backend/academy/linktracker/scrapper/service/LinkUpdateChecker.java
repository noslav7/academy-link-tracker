package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.dto.LinkUpdate;
import backend.academy.linktracker.scrapper.metrics.ScrapperMetricsRecorder;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Проверяет одну отслеживаемую ссылку на наличие обновлений,
 * при необходимости отправляет уведомление и обновляет метаданные в БД.
 */
@Component
public class LinkUpdateChecker {

    private static final Logger log = LoggerFactory.getLogger(LinkUpdateChecker.class);
    private static final String LLM_AGENT_SCOPE = "llm_agent";
    private static final String LLM_AGENT_SCOPE_TYPE = "update_description";

    private final LinkMetadataResolver linkMetadataResolver;
    private final LinkUpdatePersistenceService persistenceService;
    private final TrackedLinkMaintenanceService maintenanceService;
    private final ScrapperMetricsRecorder metricsRecorder;

    /**
     * Создает компонент проверки обновлений ссылок.
     *
     * @param linkMetadataResolver резолвер метаданных внешних ссылок
     * @param persistenceService   транзакционное сохранение обновлений и outbox-сообщений
     * @param maintenanceService   сервис обновления метаданных ссылок в БД
     */
    public LinkUpdateChecker(
            LinkMetadataResolver linkMetadataResolver,
            LinkUpdatePersistenceService persistenceService,
            TrackedLinkMaintenanceService maintenanceService) {
        this(linkMetadataResolver, persistenceService, maintenanceService, ScrapperMetricsRecorder.noop());
    }

    @Autowired
    public LinkUpdateChecker(
            LinkMetadataResolver linkMetadataResolver,
            LinkUpdatePersistenceService persistenceService,
            TrackedLinkMaintenanceService maintenanceService,
            ScrapperMetricsRecorder metricsRecorder) {
        this.linkMetadataResolver = linkMetadataResolver;
        this.persistenceService = persistenceService;
        this.maintenanceService = maintenanceService;
        this.metricsRecorder = metricsRecorder;
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

        String description = metricsRecorder.recordDuration(
                LLM_AGENT_SCOPE,
                LLM_AGENT_SCOPE_TYPE,
                () -> linkMetadataResolver.resolveUpdateDescription(trackedLink.url()));
        persistenceService.enqueueNotificationAndUpdateTrackedLink(
                new LinkUpdate(trackedLink.id(), trackedLink.url(), description, List.of(trackedLink.chatId())),
                trackedLink.withLastCheck(Instant.now(), currentUpdatedAt));

        log.atInfo()
                .addKeyValue("chatId", trackedLink.chatId())
                .addKeyValue("linkId", trackedLink.id())
                .addKeyValue("url", trackedLink.url())
                .log("Link update notification sent");
    }
}
