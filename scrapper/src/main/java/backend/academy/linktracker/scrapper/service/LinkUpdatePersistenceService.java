package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.dto.LinkUpdate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сохраняет изменения проверяемой ссылки и ставит уведомление в outbox в одной транзакции.
 */
@Service
public class LinkUpdatePersistenceService {

    private final MessageSender messageSender;
    private final TrackedLinkMaintenanceService maintenanceService;

    /**
     * Создает транзакционный сервис сохранения результата проверки ссылки.
     *
     * @param messageSender отправитель уведомления (через outbox)
     * @param maintenanceService сервис обновления метаданных ссылок
     */
    public LinkUpdatePersistenceService(MessageSender messageSender, TrackedLinkMaintenanceService maintenanceService) {
        this.messageSender = messageSender;
        this.maintenanceService = maintenanceService;
    }

    /**
     * Ставит уведомление в outbox и обновляет tracked link в рамках одной транзакции.
     *
     * @param update payload уведомления
     * @param trackedLink состояние ссылки после проверки
     */
    @Transactional
    public void enqueueNotificationAndUpdateTrackedLink(LinkUpdate update, TrackedLink trackedLink) {
        messageSender.send(update);
        maintenanceService.updateTrackedLink(trackedLink);
    }
}
